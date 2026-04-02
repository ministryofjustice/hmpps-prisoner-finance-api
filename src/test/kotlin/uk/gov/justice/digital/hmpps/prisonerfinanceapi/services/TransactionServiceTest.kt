package uk.gov.justice.digital.hmpps.prisonerfinanceapi.services

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.CustomException
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.client.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.PagedResponseStatementEntryResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.StatementEntryAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.StatementEntryOppositePostingsResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.StatementEntryResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.services.helpers.ServiceTestHelpers
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

      whenever(generalLedgerApiClient.getStatementForAccountId(prisonerId, null, null, pageNumber = 1, pageSize = 25)).thenReturn(
        PagedResponseStatementEntryResponse(content = emptyList(), pageNumber = 1, pageSize = 25, totalElements = 0, totalPages = 1, isLastPage = true),
      )

      val response = transactionService.getPrisonerTransactionsByAccountId(prisonerId, null, null, pageNumber = 1, pageSize = 25)
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

      whenever(generalLedgerApiClient.getStatementForAccountId(prisonerId, null, null, pageNumber = 1, pageSize = 25)).thenReturn(statementPage)

      val responseContent = transactionService.getPrisonerTransactionsByAccountId(prisonerId, null, null, pageNumber = 1, pageSize = 25).content

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

      whenever(generalLedgerApiClient.getStatementForAccountId(prisonerId, null, null, pageNumber = 1, pageSize = 25)).thenReturn(statementPage)

      val responseContent = transactionService.getPrisonerTransactionsByAccountId(prisonerId, null, null, pageNumber = 1, pageSize = 25).content

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

      whenever(generalLedgerApiClient.getStatementForAccountId(prisonerId, null, null)).thenReturn(statementPage)

      assertThatThrownBy {
        transactionService.getPrisonerTransactionsByAccountId(prisonerId, null, null, pageNumber = 1, pageSize = 25)
      }.isInstanceOf(CustomException::class.java)
        .extracting("status")
        .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
    }
  }
}
