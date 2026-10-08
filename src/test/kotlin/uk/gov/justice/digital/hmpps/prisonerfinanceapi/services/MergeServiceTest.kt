package uk.gov.justice.digital.hmpps.prisonerfinanceapi.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Spy
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.domainevents.AdditionalInformation
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.domainevents.HmppsMergeEvent
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.AccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.CreatePostingRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.CreateTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.SubAccountBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.SubAccountResponse
import java.time.Instant
import java.util.UUID
import kotlin.math.abs

@ExtendWith(MockitoExtension::class)
class MergeServiceTest {

  @Mock
  lateinit var accountService: AccountService

  @Mock
  lateinit var transactionService: TransactionService

  @Spy
  lateinit var idempotencyKeyService: GeneralLedgerIdempotencyService

  @InjectMocks
  lateinit var mergeService: MergeService

  @Nested
  inner class MergeAPrisonerAccount {

    val timestamp = Instant.now()
    val messageId = UUID.randomUUID()
    private val keepNomsNumber = "A1234AA"
    private val removeNomsNumber = "A1234BB"
    val mergeEvent = HmppsMergeEvent(
      eventType = "prison-offender-events.prisoner.merged",
      additionalInformation = AdditionalInformation(
        nomsNumber = keepNomsNumber,
        removedNomsNumber = removeNomsNumber,
        reason = "merged",
      ),
    )

    val accountToKeepParentAccountId = UUID.randomUUID()
    val subAccountCashToKeepAccountId = UUID.randomUUID()
    val subAccountSpendsToKeepAccountId = UUID.randomUUID()
    val subAccountSavingsToKeepAccountId = UUID.randomUUID()

    val accountToRemoveParentAccountId = UUID.randomUUID()
    val subAccountCashToRemoveAccountId = UUID.randomUUID()
    val subAccountSpendsToRemoveAccountId = UUID.randomUUID()
    val subAccountSavingsToRemoveAccountId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
      whenever(accountService.getOrCreatePrisonerAccountStructure(keepNomsNumber)).thenReturn(
        AccountResponse(
          id = accountToKeepParentAccountId,
          reference = keepNomsNumber,
          type = AccountResponse.Type.PRISONER,
          createdAt = Instant.now(),
          createdBy = "TEST",
          subAccounts = listOf(
            SubAccountResponse(
              id = subAccountCashToKeepAccountId,
              reference = "CASH",
              parentAccountId = accountToKeepParentAccountId,
              createdAt = Instant.now(),
              createdBy = "TEST",
            ),
            SubAccountResponse(
              id = subAccountSpendsToKeepAccountId,
              reference = "SPENDS",
              parentAccountId = accountToKeepParentAccountId,
              createdAt = Instant.now(),
              createdBy = "TEST",
            ),
            SubAccountResponse(
              id = subAccountSavingsToKeepAccountId,
              reference = "SAVINGS",
              parentAccountId = accountToKeepParentAccountId,
              createdAt = Instant.now(),
              createdBy = "TEST",
            ),
          ),
        ),
      )

      whenever(accountService.getOrCreatePrisonerAccountStructure(removeNomsNumber)).thenReturn(
        AccountResponse(
          id = accountToKeepParentAccountId,
          reference = removeNomsNumber,
          type = AccountResponse.Type.PRISONER,
          createdAt = Instant.now(),
          createdBy = "TEST",
          subAccounts = listOf(
            SubAccountResponse(
              id = subAccountCashToRemoveAccountId,
              reference = "CASH",
              parentAccountId = accountToRemoveParentAccountId,
              createdAt = Instant.now(),
              createdBy = "TEST",
            ),
            SubAccountResponse(
              id = subAccountSpendsToRemoveAccountId,
              reference = "SPENDS",
              parentAccountId = accountToRemoveParentAccountId,
              createdAt = Instant.now(),
              createdBy = "TEST",
            ),
            SubAccountResponse(
              id = subAccountSavingsToRemoveAccountId,
              reference = "SAVINGS",
              parentAccountId = accountToRemoveParentAccountId,
              createdAt = Instant.now(),
              createdBy = "TEST",
            ),
          ),
        ),
      )
    }

    private fun mockBalance(subAccountId: UUID, amount: Long) {
      whenever(accountService.getSubAccountBalance(subAccountId)).thenReturn(
        SubAccountBalanceResponse(
          subAccountId = subAccountId,
          balanceDateTime = Instant.now(),
          amount = amount,
        ),
      )
    }

    fun verifyTransaction(
      transactionRequest: CreateTransactionRequest,
      accountBalance: Long,
      debitSubAccountId: UUID,
      creditSubAccountId: UUID,
    ) {
      assertThat(transactionRequest.reference).isEqualTo(messageId.toString())
      assertThat(transactionRequest.timestamp).isEqualTo(timestamp)
      assertThat(transactionRequest.amount).isEqualTo(abs(accountBalance))
      assertThat(transactionRequest.postings).hasSize(2)
      assertThat(transactionRequest.description).isEqualTo("ADJ - MERGED FROM A1234BB TO A1234AA")

      val debitPostingOne = transactionRequest.postings.first { it.type == CreatePostingRequest.Type.DR }
      assertThat(debitPostingOne.subAccountId).isEqualTo(debitSubAccountId)
      assertThat(debitPostingOne.entrySequence).isEqualTo(1)

      val creditPostingOne = transactionRequest.postings.first { it.type == CreatePostingRequest.Type.CR }
      assertThat(creditPostingOne.subAccountId).isEqualTo(creditSubAccountId)
      assertThat(creditPostingOne.entrySequence).isEqualTo(2)
    }

    @Test
    fun `Should not make any transactions if the account to remove has zero balances`() {
      mockBalance(subAccountCashToRemoveAccountId, 0)
      mockBalance(subAccountSpendsToRemoveAccountId, 0)
      mockBalance(subAccountSavingsToRemoveAccountId, 0)

      mergeService.mergeAPrisonerAccount(
        prisonerMergeEvent = mergeEvent,
        messageId = messageId,
        timestamp = timestamp,
      )

      verify(transactionService, never()).postTransaction(any(), any())
    }

    @Test
    fun `Should make a transactions for each subAccount, pass the reference as messageId and the timestamp as the message timestamp`() {
      val cashBalance = 10L
      val savingsBalance = 11L
      val spendsBalance = -3L

      mockBalance(subAccountCashToRemoveAccountId, cashBalance)
      mockBalance(subAccountSavingsToRemoveAccountId, savingsBalance)
      mockBalance(subAccountSpendsToRemoveAccountId, spendsBalance)

      val transactionCaptor = argumentCaptor<CreateTransactionRequest>()
      whenever(
        transactionService.postTransaction(
          idempotencyKey = any(),
          createTransactionRequest = transactionCaptor.capture(),
        ),
      ).thenReturn(mock())

      mergeService.mergeAPrisonerAccount(
        prisonerMergeEvent = mergeEvent,
        messageId = messageId,
        timestamp = timestamp,
      )

      verify(transactionService, times(3)).postTransaction(any(), any())

      val requests = transactionCaptor.allValues
      assertThat(requests).hasSize(3)

      val cashRequest = requests.first { it.amount == abs(cashBalance) }
      val spendsRequest = requests.first { it.amount == abs(spendsBalance) }
      val savingsRequest = requests.first { it.amount == abs(savingsBalance) }

      verifyTransaction(
        transactionRequest = cashRequest,
        accountBalance = cashBalance,
        debitSubAccountId = subAccountCashToRemoveAccountId,
        creditSubAccountId = subAccountCashToKeepAccountId,
      )
      verifyTransaction(
        transactionRequest = savingsRequest,
        accountBalance = savingsBalance,
        debitSubAccountId = subAccountSavingsToRemoveAccountId,
        creditSubAccountId = subAccountSavingsToKeepAccountId,
      )
      verifyTransaction(
        transactionRequest = spendsRequest,
        accountBalance = spendsBalance,
        debitSubAccountId = subAccountSpendsToKeepAccountId,
        creditSubAccountId = subAccountSpendsToRemoveAccountId,
      )
    }

    @Test
    fun `Should make a debit transaction when the subAccount to remove has a negative balance`() {
      mockBalance(subAccountCashToRemoveAccountId, -10)
      mockBalance(subAccountSpendsToRemoveAccountId, 0)
      mockBalance(subAccountSavingsToRemoveAccountId, 0)

      val transactionCaptor = argumentCaptor<CreateTransactionRequest>()
      whenever(
        transactionService.postTransaction(
          idempotencyKey = any(),
          createTransactionRequest = transactionCaptor.capture(),
        ),
      ).thenReturn(mock())

      mergeService.mergeAPrisonerAccount(
        prisonerMergeEvent = mergeEvent,
        messageId = messageId,
        timestamp = timestamp,
      )

      verify(transactionService, times(1)).postTransaction(any(), any())

      val transactionRequest = transactionCaptor.firstValue

      verifyTransaction(
        transactionRequest = transactionRequest,
        accountBalance = 10,
        debitSubAccountId = subAccountCashToKeepAccountId,
        creditSubAccountId = subAccountCashToRemoveAccountId,
      )
    }

    @Test
    fun `Should make a credit transaction when the subAccount to remove has a positive balance`() {
      mockBalance(subAccountCashToRemoveAccountId, 15)
      mockBalance(subAccountSpendsToRemoveAccountId, 0)
      mockBalance(subAccountSavingsToRemoveAccountId, 0)

      val transactionCaptor = argumentCaptor<CreateTransactionRequest>()
      whenever(
        transactionService.postTransaction(
          idempotencyKey = any(),
          createTransactionRequest = transactionCaptor.capture(),
        ),
      ).thenReturn(mock())

      mergeService.mergeAPrisonerAccount(
        prisonerMergeEvent = mergeEvent,
        messageId = messageId,
        timestamp = timestamp,
      )

      verify(transactionService, times(1)).postTransaction(any(), any())

      val transactionRequest = transactionCaptor.firstValue

      verifyTransaction(
        transactionRequest = transactionRequest,
        accountBalance = 15,
        debitSubAccountId = subAccountCashToRemoveAccountId,
        creditSubAccountId = subAccountCashToKeepAccountId,
      )
    }
  }
}
