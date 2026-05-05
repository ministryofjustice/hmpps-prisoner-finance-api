package uk.gov.justice.digital.hmpps.prisonerfinanceapi.services

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.CustomException
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.client.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.CreatePostingRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.CreateTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.PagedResponseStatementEntryResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.PostingResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.StatementEntryAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.StatementEntryOppositePostingsResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.StatementEntryResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.TransactionResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.request.CreateTransactionFormRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.services.helpers.ServiceTestHelpers
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class TransactionServiceTest {

  val serviceTestHelpers = ServiceTestHelpers()

  @Mock private lateinit var generalLedgerApiClient: GeneralLedgerApiClient

  @InjectMocks private lateinit var transactionService: TransactionService

  @Nested
  inner class GetPrisonerTransactionsByAccountId {

    @Test
    fun `Should return empty list if given an empty list`() {
      val prisonerId = UUID.randomUUID()

      whenever(
        generalLedgerApiClient.getStatementForAccountId(
          accountId = prisonerId,
          startDate = null,
          endDate = null,
          credit = true,
          debit = true,
          pageNumber = 1,
          pageSize = 25,
          subAccountId = null,
        ),
      ).thenReturn(
        PagedResponseStatementEntryResponse(content = emptyList(), pageNumber = 1, pageSize = 25, totalElements = 0, totalPages = 1, isLastPage = true),
      )

      val response = transactionService.getPrisonerTransactionsByAccountId(
        accountId = prisonerId,
        startDate = null,
        endDate = null,
        credit = true,
        debit = true,
        pageNumber = 1,
        pageSize = 25,
        subAccountId = null,
      )
      assertThat(response.content).isEmpty()
    }

    @Test
    fun `Should map prisoner to prisoner postings to UI transactions`() {
      val prisonerId = UUID.randomUUID()

      val parentAccount = serviceTestHelpers.createParentAccountResponse(
        reference = "A1234BC",
        StatementEntryAccountResponse.Type.PRISONER,
      )

      val subAccountCash = serviceTestHelpers.createSubAccountWithParentResponse(parentAccount, "CASH")

      val subAccountSavings = serviceTestHelpers.createSubAccountWithParentResponse(parentAccount, "SAVINGS")

      val statementPageContents = listOf(
        serviceTestHelpers.createStatementEntryResponse(
          subAccount = subAccountCash,
          postingType = StatementEntryResponse.PostingType.CR,
          amount = 2L,
          statementOppositePosting = listOf(
            serviceTestHelpers.createStatementEntryOppositePostingResponse(
              subAccountSavings,
              2L,
              StatementEntryOppositePostingsResponse.Type.DR,
            ),
          ),
        ),
        serviceTestHelpers.createStatementEntryResponse(
          subAccount = subAccountSavings,
          postingType = StatementEntryResponse.PostingType.DR,
          amount = 2L,
          statementOppositePosting = listOf(
            serviceTestHelpers.createStatementEntryOppositePostingResponse(
              subAccountCash,
              2L,
              StatementEntryOppositePostingsResponse.Type.CR,
            ),
          ),
        ),
      )
      val statementPage = PagedResponseStatementEntryResponse(content = statementPageContents, pageNumber = 1, pageSize = 25, totalElements = 2, totalPages = 1, isLastPage = true)

      whenever(
        generalLedgerApiClient.getStatementForAccountId(
          accountId = prisonerId,
          startDate = null,
          endDate = null,
          credit = true,
          debit = true,
          pageNumber = 1,
          pageSize = 25,
          subAccountId = null,
        ),
      ).thenReturn(statementPage)

      val responseContent = transactionService.getPrisonerTransactionsByAccountId(prisonerId, null, null, credit = true, debit = true, pageNumber = 1, pageSize = 25, subAccountId = null).content

      assertThat(responseContent).hasSize(2)
      assertThat(responseContent[0].description).isEqualTo(statementPageContents[0].description)
      assertThat(responseContent[0].accountType).isEqualTo(statementPageContents[0].subAccount.reference)
      assertThat(responseContent[0].location).isEqualTo("")
      assertThat(responseContent[0].credit).isEqualTo(statementPageContents[0].amount)
      assertThat(responseContent[0].debit).isEqualTo(0)
      assertThat(responseContent[0].date).isEqualTo(statementPageContents[0].transactionTimestamp)

      assertThat(responseContent[1].description).isEqualTo(statementPageContents[1].description)
      assertThat(responseContent[1].accountType).isEqualTo(statementPageContents[1].subAccount.reference)
      assertThat(responseContent[1].location).isEqualTo("")
      assertThat(responseContent[1].credit).isEqualTo(0)
      assertThat(responseContent[1].debit).isEqualTo(statementPageContents[1].amount)
      assertThat(responseContent[1].date).isEqualTo(statementPageContents[1].transactionTimestamp)
    }

    @Test
    fun `Should map prison to prisoner postings to UI transactions`() {
      val prisonerId = UUID.randomUUID()

      val parentAccountPrisoner = serviceTestHelpers.createParentAccountResponse(
        reference = "A1234BC",
        StatementEntryAccountResponse.Type.PRISONER,
      )

      val parentAccountPrison = serviceTestHelpers.createParentAccountResponse(
        reference = "LEI",
        StatementEntryAccountResponse.Type.PRISON,
      )

      val subAccountCashPrisoner = serviceTestHelpers.createSubAccountWithParentResponse(parentAccountPrisoner, "CASH")

      val subAccountPrison = serviceTestHelpers.createSubAccountWithParentResponse(parentAccountPrison, "CANT")

      val statementPageContents = listOf(
        serviceTestHelpers.createStatementEntryResponse(
          subAccount = subAccountCashPrisoner,
          postingType = StatementEntryResponse.PostingType.CR,
          amount = 2L,
          statementOppositePosting = listOf(
            serviceTestHelpers.createStatementEntryOppositePostingResponse(
              subAccountPrison,
              2L,
              StatementEntryOppositePostingsResponse.Type.DR,
            ),
          ),
        ),
      )
      val statementPage = PagedResponseStatementEntryResponse(content = statementPageContents, pageNumber = 1, pageSize = 25, totalElements = 1, totalPages = 1, isLastPage = true)

      whenever(generalLedgerApiClient.getStatementForAccountId(prisonerId, null, null, credit = true, debit = true, pageNumber = 1, pageSize = 25, subAccountId = null)).thenReturn(statementPage)

      val responseContent = transactionService.getPrisonerTransactionsByAccountId(prisonerId, null, null, credit = true, debit = true, pageNumber = 1, pageSize = 25, subAccountId = null).content

      assertThat(responseContent).hasSize(1)
      assertThat(responseContent[0].description).isEqualTo(statementPageContents[0].description)
      assertThat(responseContent[0].accountType).isEqualTo(statementPageContents[0].subAccount.reference)
      assertThat(responseContent[0].location).isEqualTo("LEI")
      assertThat(responseContent[0].credit).isEqualTo(statementPageContents[0].amount)
      assertThat(responseContent[0].debit).isEqualTo(0)
      assertThat(responseContent[0].date).isEqualTo(statementPageContents[0].transactionTimestamp)
    }

    @Test
    fun `Should throw custom exception internal server error when no opposite postings are provided`() {
      val prisonerId = UUID.randomUUID()

      val parentAccountPrisoner = serviceTestHelpers.createParentAccountResponse(
        reference = "A1234BC",
        StatementEntryAccountResponse.Type.PRISONER,
      )

      val subAccountCashPrisoner = serviceTestHelpers.createSubAccountWithParentResponse(parentAccountPrisoner, "CASH")

      val statementPageContents = listOf(
        serviceTestHelpers.createStatementEntryResponse(
          subAccount = subAccountCashPrisoner,
          postingType = StatementEntryResponse.PostingType.CR,
          amount = 2L,
          statementOppositePosting = listOf(),
        ),
      )

      val statementPage = PagedResponseStatementEntryResponse(content = statementPageContents, pageNumber = 1, pageSize = 25, totalElements = 1, totalPages = 1, isLastPage = true)

      whenever(generalLedgerApiClient.getStatementForAccountId(prisonerId, null, null, credit = true, debit = true, subAccountId = null)).thenReturn(statementPage)

      assertThatThrownBy {
        transactionService.getPrisonerTransactionsByAccountId(prisonerId, null, null, credit = true, debit = true, pageNumber = 1, pageSize = 25, subAccountId = null)
      }.isInstanceOf(CustomException::class.java)
        .extracting("status")
        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
    }
  }

  @Nested
  inner class CreateTransaction {
    @Test
    fun `Should call the general ledger and return the transaction id`() {
      val creditSubAccountId = UUID.randomUUID()
      val debitSubAccountId = UUID.randomUUID()

      val createTransactionFormRequest = CreateTransactionFormRequest(
        creditSubAccountId = creditSubAccountId,
        debitSubAccountId = debitSubAccountId,
        amount = 2L,
        description = "description",
      )

      val postings = listOf(
        CreatePostingRequest(
          subAccountId = debitSubAccountId,
          type = CreatePostingRequest.Type.DR,
          amount = 2L,
          entrySequence = 1,
        ),
        CreatePostingRequest(
          subAccountId = creditSubAccountId,
          type = CreatePostingRequest.Type.CR,
          amount = 2L,
          entrySequence = 2,
        ),
      )

      val postingsResponse = listOf(
        PostingResponse(
          id = UUID.randomUUID(),
          subAccountID = debitSubAccountId,
          type = PostingResponse.Type.DR,
          amount = 2L,
          createdBy = "",
          createdAt = Instant.now(),
        ),
        PostingResponse(
          id = UUID.randomUUID(),
          subAccountID = creditSubAccountId,
          type = PostingResponse.Type.CR,
          amount = 2L,
          createdBy = "",
          createdAt = Instant.now(),
        ),
      )

      val transactionReference = UUID.randomUUID().toString()

      val transactionResponse = TransactionResponse(
        id = UUID.randomUUID(),
        createdBy = "",
        createdAt = Instant.now(),
        reference = transactionReference,
        description = createTransactionFormRequest.description,
        timestamp = Instant.now(),
        amount = 2L,
        postings = postingsResponse,
      )

      whenever(
        generalLedgerApiClient.postTransaction(
          idempotencyKey = any(),
          createTransactionRequest = argThat<CreateTransactionRequest> { request ->

            val referenceIsValidUUID = try {
              UUID.fromString(request.reference)
              true
            } catch (_: IllegalArgumentException) {
              false
            }

            request.description == createTransactionFormRequest.description &&
              request.amount == createTransactionFormRequest.amount &&
              request.postings == postings &&
              referenceIsValidUUID
          },
        ),
      ).thenReturn(transactionResponse)

      val createdTransaction = transactionService.createTransaction(createTransactionFormRequest)

      assertThat(createdTransaction?.amount).isEqualTo(transactionResponse.amount)
      assertThat(createdTransaction?.description).isEqualTo(transactionResponse.description)
      assertThat(createdTransaction?.postings).isEqualTo(transactionResponse.postings)
      assertThat(createdTransaction?.reference).isEqualTo(transactionResponse.reference)
    }
  }
}
