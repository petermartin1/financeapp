package com.financeapp.data.ofx

import com.financeapp.data.fileimport.ImportRepository
import com.financeapp.data.fileimport.OfxParser
import com.financeapp.db.schema.BankConnections
import com.financeapp.db.schema.ConnectedAccounts
import com.financeapp.security.SecureCredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlin.time.Instant
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class OfxRepository(
    private val database: Database,
    private val ofxClient: OfxClient,
    private val importRepository: ImportRepository,
    private val secureCredentialStore: SecureCredentialStore,
    private val ofxParser: OfxParser = OfxParser()
) {

    fun getAllConnections(): Flow<List<BankConnectionInfo>> = flow {
        val connections = withContext(Dispatchers.IO) {
            transaction(database) {
                BankConnections.selectAll().map { conn ->
                    BankConnectionInfo(
                        id = conn[BankConnections.id].value.toLong(),
                        bankName = conn[BankConnections.bankName],
                        userId = conn[BankConnections.userId],
                        lastSynced = conn[BankConnections.lastSynced]?.let { Instant.fromEpochMilliseconds(it) },
                        createdAt = Instant.fromEpochMilliseconds(conn[BankConnections.createdAt])
                    )
                }
            }
        }
        emit(connections)
    }

    suspend fun addConnection(
        bankName: String,
        userId: String,
        password: String
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            // Validate inputs
            validateBankName(bankName).getOrElse {
                return@withContext Result.failure(Exception("Invalid bank name"))
            }
            validateUserId(userId).getOrElse {
                return@withContext Result.failure(Exception("Invalid user ID format"))
            }
            validatePassword(password).getOrElse {
                return@withContext Result.failure(it)
            }

            val config = BankConfigs.getByName(bankName)
                ?: return@withContext Result.failure(Exception("Bank not supported"))

            // Test connection first
            val testResult = ofxClient.fetchAccounts(
                config,
                BankCredentials(0, userId, password)
            )

            if (testResult.isFailure) {
                // Return generic error - don't expose internal details
                return@withContext Result.failure(Exception("Unable to connect to bank"))
            }

            // Check for OFX error in response
            val response = testResult.getOrThrow()
            if (response.contains("<CODE>") && !response.contains("<CODE>0</CODE>")) {
                // Don't expose raw bank error messages - they might contain sensitive info
                val errorMatch = Regex("<CODE>(\\d+)</CODE>").find(response)
                val errorCode = errorMatch?.groupValues?.get(1) ?: "unknown"
                return@withContext Result.failure(Exception(
                    when (errorCode) {
                        "2000", "15500", "15000" -> "Invalid credentials"
                        "2002" -> "Account locked - contact your bank"
                        else -> "Authentication failed (code: $errorCode)"
                    }
                ))
            }

            // Save connection (password stored separately in platform-specific secure storage)
            val now = Clock.System.now().toEpochMilliseconds()
            val connectionId = transaction(database) {
                BankConnections.insert {
                    it[BankConnections.bankName] = bankName
                    it[BankConnections.userId] = userId
                    it[BankConnections.lastSynced] = null
                    it[BankConnections.createdAt] = now
                }[BankConnections.id].value.toLong()
            }

            // Store password securely in platform keychain
            val credentialKey = "bank_connection_$connectionId"
            if (!secureCredentialStore.store(credentialKey, password)) {
                // Failed to store securely - delete the connection
                transaction(database) {
                    BankConnections.deleteWhere { BankConnections.id eq connectionId.toInt() }
                }
                return@withContext Result.failure(Exception("Failed to store credentials securely"))
            }

            Result.success(connectionId)
        } catch (e: SecurityException) {
            Result.failure(e)
        } catch (e: Exception) {
            // Log the actual error internally but return generic message
            // In production, log to secure logging system
            Result.failure(Exception("Connection failed - please try again"))
        }
    }

    suspend fun syncConnection(
        connectionId: Long,
        startDate: LocalDate,
        endDate: LocalDate
    ): Result<SyncSummary> = withContext(Dispatchers.IO) {
        try {
            val connection = transaction(database) {
                BankConnections.selectAll().where { BankConnections.id eq connectionId.toInt() }
                    .singleOrNull()
            } ?: return@withContext Result.failure(Exception("Connection not found"))

            val bankName = connection[BankConnections.bankName]
            val userId = connection[BankConnections.userId]

            val config = BankConfigs.getByName(bankName)
                ?: return@withContext Result.failure(Exception("Bank configuration not found"))

            // Retrieve password from secure keychain
            val credentialKey = "bank_connection_$connectionId"
            val password = secureCredentialStore.retrieve(credentialKey)
                ?: return@withContext Result.failure(Exception("Credentials expired - please re-add the connection"))

            val credentials = BankCredentials(
                odataConnectionId = connectionId,
                userId = userId,
                password = password
            )

            // Get connected accounts
            val connectedAccounts = transaction(database) {
                ConnectedAccounts.selectAll().where { ConnectedAccounts.connectionId eq connectionId.toInt() }
                    .toList()
            }

            if (connectedAccounts.isEmpty()) {
                return@withContext Result.failure(Exception("No accounts linked to this connection"))
            }

            var totalImported = 0
            var totalDuplicates = 0
            var syncErrors = 0

            for (account in connectedAccounts) {
                try {
                    val accountType = OfxAccountType.valueOf(account[ConnectedAccounts.accountType])
                    val remoteAccountId = account[ConnectedAccounts.remoteAccountId]
                    val localAccountId = account[ConnectedAccounts.localAccountId].value.toLong()

                    val result = ofxClient.fetchTransactions(
                        config = config,
                        credentials = credentials,
                        accountId = remoteAccountId,
                        accountType = accountType,
                        startDate = startDate,
                        endDate = endDate
                    )

                    if (result.isSuccess) {
                        val ofxContent = result.getOrThrow()
                        val importResult = importRepository.importOfxFile(
                            ofxContent,
                            localAccountId
                        )

                        if (importResult.isSuccess) {
                            val summary = importResult.getOrThrow()
                            totalImported += summary.imported
                            totalDuplicates += summary.duplicates
                        }
                    } else {
                        syncErrors++
                    }
                } catch (e: Exception) {
                    // Log error internally but continue with other accounts
                    syncErrors++
                }
            }

            if (syncErrors == connectedAccounts.size) {
                return@withContext Result.failure(Exception("Failed to sync - check your connection"))
            }

            // Only update last synced if at least one account succeeded
            transaction(database) {
                BankConnections.update({ BankConnections.id eq connectionId.toInt() }) {
                    it[lastSynced] = Clock.System.now().toEpochMilliseconds()
                }
            }

            Result.success(SyncSummary(
                imported = totalImported,
                duplicates = totalDuplicates
            ))
        } catch (e: Exception) {
            // Return generic error - don't expose internal details
            Result.failure(Exception("Sync failed - please try again"))
        }
    }

    suspend fun linkAccount(
        connectionId: Long,
        localAccountId: Long,
        remoteAccountId: String,
        accountType: OfxAccountType
    ) = withContext(Dispatchers.IO) {
        transaction(database) {
            ConnectedAccounts.insert {
                it[ConnectedAccounts.connectionId] = connectionId.toInt()
                it[ConnectedAccounts.localAccountId] = localAccountId.toInt()
                it[ConnectedAccounts.remoteAccountId] = remoteAccountId
                it[ConnectedAccounts.accountType] = accountType.name
            }
        }
    }

    suspend fun deleteConnection(connectionId: Long) = withContext(Dispatchers.IO) {
        // Delete password from secure keychain
        val credentialKey = "bank_connection_$connectionId"
        secureCredentialStore.delete(credentialKey)

        transaction(database) {
            ConnectedAccounts.deleteWhere { ConnectedAccounts.connectionId eq connectionId.toInt() }
            BankConnections.deleteWhere { BankConnections.id eq connectionId.toInt() }
        }
    }

    suspend fun getConnectionById(connectionId: Long): BankConnectionInfo? = withContext(Dispatchers.IO) {
        transaction(database) {
            BankConnections.selectAll().where { BankConnections.id eq connectionId.toInt() }
                .singleOrNull()
                ?.let { conn ->
                    BankConnectionInfo(
                        id = conn[BankConnections.id].value.toLong(),
                        bankName = conn[BankConnections.bankName],
                        userId = conn[BankConnections.userId],
                        lastSynced = conn[BankConnections.lastSynced]?.let { Instant.fromEpochMilliseconds(it) },
                        createdAt = Instant.fromEpochMilliseconds(conn[BankConnections.createdAt])
                    )
                }
        }
    }
}

data class BankConnectionInfo(
    val id: Long,
    val bankName: String,
    val userId: String,
    val lastSynced: Instant?,
    val createdAt: Instant
)

data class SyncSummary(
    val imported: Int,
    val duplicates: Int
)
