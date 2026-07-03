package com.financeapp.ui.subscriptions

import com.financeapp.domain.model.DetectedSubscription
import com.financeapp.domain.model.Payee
import com.financeapp.domain.model.PayeeWithStats
import com.financeapp.domain.model.SubscriptionStatus
import com.financeapp.domain.model.TransactionFrequency
import com.financeapp.domain.repository.PayeeRepository
import com.financeapp.domain.repository.SubscriptionRepository
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setup() { kotlinx.coroutines.Dispatchers.setMain(testDispatcher) }
    @AfterTest fun teardown() { kotlinx.coroutines.Dispatchers.resetMain() }

    private fun sub(id: Long, cadence: TransactionFrequency, median: Long, status: SubscriptionStatus) =
        DetectedSubscription(
            id = id, payeeId = null, displayName = "Sub$id", matchKey = "name:sub$id",
            cadence = cadence, status = status, medianAmountCents = median,
            minAmountCents = median, maxAmountCents = median, isVariable = false,
            occurrenceCount = 4, firstSeen = LocalDate(2026, 1, 1), lastSeen = LocalDate(2026, 4, 1),
            nextExpectedDate = LocalDate(2026, 5, 1), confidence = 90, isActive = true
        )

    private class FakeRepo(initial: List<DetectedSubscription>) : SubscriptionRepository {
        val flow = MutableStateFlow(initial)
        var confirmed: Long? = null
        var bridged: Long? = null
        var markedPayee: Long? = null
        override fun getSubscriptions(): Flow<List<DetectedSubscription>> = flow
        override suspend fun rescan() {}
        override suspend fun confirm(id: Long) { confirmed = id }
        override suspend fun dismiss(id: Long) {}
        override suspend fun markPayeeAsSubscription(payeeId: Long) { markedPayee = payeeId }
        override suspend fun createScheduledFromSubscription(id: Long) { bridged = id }
        override fun notifySubscriptionsChanged() {}
    }

    // Minimal PayeeRepository fake — SubscriptionViewModel only reads the payee list for the picker.
    // Every other member is stubbed to satisfy the interface.
    private class FakePayees : PayeeRepository {
        override fun getAllPayees(): Flow<List<Payee>> = flowOf(emptyList())
        override fun getPayeesWithStats(): Flow<List<PayeeWithStats>> = flowOf(emptyList())
        override suspend fun getPayeeById(id: Long): Payee? = null
        override suspend fun getPayeeByName(name: String): Payee? = null
        override suspend fun getPayeesByNames(names: List<String>): Map<String, Payee> = emptyMap()
        override suspend fun insertPayee(payee: Payee): Long = 0
        override suspend fun batchInsertPayees(payees: List<Payee>): Map<String, Long> = emptyMap()
        override suspend fun updatePayee(payee: Payee) {}
        override suspend fun deletePayee(id: Long) {}
        override suspend fun mergePayees(sourceId: Long, targetId: Long) {}
        override fun notifyPayeesChanged() {}
    }

    @Test
    fun `estimated monthly total normalizes yearly to monthly and hides dismissed`() = runTest(testDispatcher, timeout = 5.seconds) {
        val repo = FakeRepo(listOf(
            sub(1, TransactionFrequency.MONTHLY, 1000, SubscriptionStatus.CONFIRMED),   // $10/mo
            sub(2, TransactionFrequency.YEARLY, 12000, SubscriptionStatus.CANDIDATE),   // $120/yr -> $10/mo
            sub(3, TransactionFrequency.MONTHLY, 5000, SubscriptionStatus.DISMISSED)    // hidden
        ))
        val vm = SubscriptionViewModel(repo, FakePayees())
        vm.uiState.test(timeout = 5.seconds) {
            // Skip initial empty emission, take the loaded one.
            val state = awaitItem().let { if (it.subscriptions.isEmpty()) awaitItem() else it }
            assertEquals(2, state.subscriptions.size, "dismissed hidden by default")
            assertEquals(2000, state.estimatedMonthlyCents, "10 + 10 dollars per month")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `confirm delegates to repository`() = runTest(testDispatcher) {
        val repo = FakeRepo(emptyList())
        SubscriptionViewModel(repo, FakePayees()).confirm(7)
        assertEquals(7, repo.confirmed)
    }

    @Test
    fun `confirming a payee-mapped candidate offers the schedule bridge`() = runTest(testDispatcher) {
        val payeeMapped = sub(5, TransactionFrequency.MONTHLY, 1599, SubscriptionStatus.CANDIDATE)
            .copy(payeeId = 42, scheduledTransactionId = null)
        val repo = FakeRepo(listOf(payeeMapped))
        val vm = SubscriptionViewModel(repo, FakePayees())
        vm.uiState.test(timeout = 5.seconds) {
            awaitItem().let { if (it.subscriptions.isEmpty()) awaitItem() else it }  // wait for load
            vm.confirm(5)
            assertEquals(5L, awaitItem().pendingBridge?.id, "confirm should park a bridge offer")
            vm.addScheduledForPending()
            assertNull(awaitItem().pendingBridge, "accepting clears the prompt")
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(5L, repo.bridged)
    }
}
