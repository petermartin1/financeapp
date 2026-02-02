package com.financeapp.data.backup

import com.financeapp.db.schema.Accounts
import com.financeapp.db.schema.Budgets
import com.financeapp.db.schema.Categories
import com.financeapp.db.schema.Payees
import com.financeapp.db.schema.Transactions
import com.financeapp.domain.model.ExportFormat
import com.financeapp.domain.model.ExportOptions
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class ExportRepository(
    private val database: Database
) {

    fun exportTransactions(options: ExportOptions): Pair<String, Int> {
        return when (options.format) {
            ExportFormat.CSV -> exportToCsv(options)
            ExportFormat.OFX -> exportToOfx(options)
        }
    }

    private fun exportToCsv(options: ExportOptions): Pair<String, Int> = transaction(database) {
        val transactions = if (options.accountId != null) {
            Transactions.selectAll().where { Transactions.accountId eq options.accountId.toInt() }.toList()
        } else {
            Transactions.selectAll().toList()
        }

        val accounts = Accounts.selectAll().associate { it[Accounts.id].value.toLong() to it[Accounts.name] }
        val categories = Categories.selectAll().associate { it[Categories.id].value.toLong() to it[Categories.name] }
        val payees = Payees.selectAll().associate { it[Payees.id].value.toLong() to it[Payees.name] }

        val sb = StringBuilder()
        sb.appendLine("Date,Account,Payee,Category,Amount,Memo,Cleared,Reconciled")

        var count = 0
        transactions.forEach { tx ->
            if (!options.includeCleared && tx[Transactions.isCleared]) return@forEach
            if (!options.includeReconciled && tx[Transactions.isReconciled]) return@forEach

            val date = formatDate(tx[Transactions.date])
            val account = escapeCsv(accounts[tx[Transactions.accountId].value.toLong()] ?: "")
            val payee = escapeCsv(tx[Transactions.payeeId]?.value?.toLong()?.let { payees[it] } ?: "")
            val category = escapeCsv(tx[Transactions.categoryId]?.value?.toLong()?.let { categories[it] } ?: "")
            val amount = formatAmount(tx[Transactions.amount])
            val memo = escapeCsv(tx[Transactions.memo] ?: "")
            val cleared = if (tx[Transactions.isCleared]) "Y" else "N"
            val reconciled = if (tx[Transactions.isReconciled]) "Y" else "N"

            sb.appendLine("$date,$account,$payee,$category,$amount,$memo,$cleared,$reconciled")
            count++
        }

        sb.toString() to count
    }

    private fun exportToOfx(options: ExportOptions): Pair<String, Int> = transaction(database) {
        val transactions = if (options.accountId != null) {
            Transactions.selectAll().where { Transactions.accountId eq options.accountId.toInt() }.toList()
        } else {
            Transactions.selectAll().toList()
        }

        val accounts = Accounts.selectAll().toList()
        val payees = Payees.selectAll().associate { it[Payees.id].value.toLong() to it[Payees.name] }

        val sb = StringBuilder()
        sb.appendLine("OFXHEADER:100")
        sb.appendLine("DATA:OFXSGML")
        sb.appendLine("VERSION:102")
        sb.appendLine("SECURITY:NONE")
        sb.appendLine("ENCODING:USASCII")
        sb.appendLine("CHARSET:1252")
        sb.appendLine("COMPRESSION:NONE")
        sb.appendLine("OLDFILEUID:NONE")
        sb.appendLine("NEWFILEUID:NONE")
        sb.appendLine()
        sb.appendLine("<OFX>")
        sb.appendLine("<BANKMSGSRSV1>")

        var totalCount = 0

        // Group transactions by account
        val txByAccount = transactions.groupBy { it[Transactions.accountId].value.toLong() }

        accounts.forEach { account ->
            val accountId = account[Accounts.id].value.toLong()
            val accountTxs = txByAccount[accountId] ?: return@forEach

            sb.appendLine("<STMTTRNRS>")
            sb.appendLine("<STMTRS>")
            sb.appendLine("<CURDEF>${account[Accounts.currency]}</CURDEF>")
            sb.appendLine("<BANKACCTFROM>")
            sb.appendLine("<BANKID>000000000</BANKID>")
            sb.appendLine("<ACCTID>$accountId</ACCTID>")
            sb.appendLine("<ACCTTYPE>${account[Accounts.type]}</ACCTTYPE>")
            sb.appendLine("</BANKACCTFROM>")
            sb.appendLine("<BANKTRANLIST>")

            accountTxs.forEach { tx ->
                if (!options.includeCleared && tx[Transactions.isCleared]) return@forEach
                if (!options.includeReconciled && tx[Transactions.isReconciled]) return@forEach

                val dateStr = formatOfxDate(tx[Transactions.date])
                val amount = tx[Transactions.amount]
                val trnType = if (amount >= 0) "CREDIT" else "DEBIT"
                val payeeName = escapeXml(tx[Transactions.payeeId]?.value?.toLong()?.let { payees[it] } ?: "Unknown")

                sb.appendLine("<STMTTRN>")
                sb.appendLine("<TRNTYPE>$trnType</TRNTYPE>")
                sb.appendLine("<DTPOSTED>$dateStr</DTPOSTED>")
                sb.appendLine("<TRNAMT>${amount / 100.0}</TRNAMT>")
                sb.appendLine("<FITID>${tx[Transactions.id].value}</FITID>")
                sb.appendLine("<NAME>$payeeName</NAME>")
                tx[Transactions.memo]?.let {
                    sb.appendLine("<MEMO>${escapeXml(it)}</MEMO>")
                }
                sb.appendLine("</STMTTRN>")
                totalCount++
            }

            sb.appendLine("</BANKTRANLIST>")
            sb.appendLine("</STMTRS>")
            sb.appendLine("</STMTTRNRS>")
        }

        sb.appendLine("</BANKMSGSRSV1>")
        sb.appendLine("</OFX>")

        sb.toString() to totalCount
    }

    fun exportAccounts(): Pair<String, Int> = transaction(database) {
        val accounts = Accounts.selectAll().toList()

        val sb = StringBuilder()
        sb.appendLine("Name,Type,Institution,Currency,Active")

        accounts.forEach { account ->
            val name = escapeCsv(account[Accounts.name])
            val institution = escapeCsv(account[Accounts.institution] ?: "")
            val currency = escapeCsv(account[Accounts.currency])
            val active = if (account[Accounts.isActive]) "Y" else "N"
            sb.appendLine("$name,${account[Accounts.type]},$institution,$currency,$active")
        }

        sb.toString() to accounts.size
    }

    fun exportCategories(): Pair<String, Int> = transaction(database) {
        val categories = Categories.selectAll().toList()

        val sb = StringBuilder()
        sb.appendLine("Name,Type,Icon,Color")

        categories.forEach { category ->
            val name = escapeCsv(category[Categories.name])
            val icon = escapeCsv(category[Categories.icon] ?: "")
            val color = escapeCsv(category[Categories.color] ?: "")
            sb.appendLine("$name,${category[Categories.type]},$icon,$color")
        }

        sb.toString() to categories.size
    }

    fun exportBudgets(): Pair<String, Int> = transaction(database) {
        val categories = Categories.selectAll().associate { it[Categories.id].value.toLong() to it[Categories.name] }

        // Query all budgets at once and sort in code
        val allBudgets = Budgets
            .selectAll()
            .orderBy(Budgets.year to SortOrder.ASC, Budgets.month to SortOrder.ASC, Budgets.categoryId to SortOrder.ASC)
            .toList()

        val sb = StringBuilder()
        sb.appendLine("Category,Amount,Year,Month")

        allBudgets.forEach { budget ->
            val categoryId = budget[Budgets.categoryId].value.toLong()
            val categoryName = escapeCsv(categories[categoryId] ?: "Unknown")
            val amount = budget[Budgets.amount] / 100.0
            val year = budget[Budgets.year]
            val month = budget[Budgets.month]
            sb.appendLine("$categoryName,$amount,$year,$month")
        }

        sb.toString() to allBudgets.size
    }

    private fun formatDate(millis: Long): String {
        val instant = Instant.fromEpochMilliseconds(millis)
        val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        return "${dt.year}-${dt.monthNumber.toString().padStart(2, '0')}-${dt.dayOfMonth.toString().padStart(2, '0')}"
    }

    private fun formatOfxDate(millis: Long): String {
        val instant = Instant.fromEpochMilliseconds(millis)
        val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        return "${dt.year}${dt.monthNumber.toString().padStart(2, '0')}${dt.dayOfMonth.toString().padStart(2, '0')}"
    }

    private fun formatAmount(cents: Long): String {
        val dollars = cents / 100.0
        return "%.2f".format(dollars)
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    private fun escapeXml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun String.format(vararg args: Any?): String {
        // Simple format for %.2f
        if (this == "%.2f" && args.isNotEmpty()) {
            val num = args[0] as? Double ?: return this
            val intPart = num.toLong()
            val decPart = ((kotlin.math.abs(num) - kotlin.math.abs(intPart)) * 100).toLong()
            val sign = if (num < 0) "-" else ""
            return "$sign${kotlin.math.abs(intPart)}.${decPart.toString().padStart(2, '0')}"
        }
        return this
    }
}
