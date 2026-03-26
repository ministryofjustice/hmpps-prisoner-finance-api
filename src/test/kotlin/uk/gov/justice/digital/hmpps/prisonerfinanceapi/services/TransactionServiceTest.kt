package uk.gov.justice.digital.hmpps.prisonerfinanceapi.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.client.GeneralLedgerApiClient
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

      whenever(generalLedgerApiClient.getStatementForAccountId(prisonerId, null, null)).thenReturn(emptyList())

      val response = transactionService.getPrisonerTransactionsByAccountId(prisonerId, null, null)
      assertThat(response).isEmpty()
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

      val glResponses = listOf(
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

      whenever(generalLedgerApiClient.getStatementForAccountId(prisonerId, null, null)).thenReturn(glResponses)

      val response = transactionService.getPrisonerTransactionsByAccountId(prisonerId, null, null)

      assertThat(response).hasSize(2)
      assertThat(response[0].description).isEqualTo(glResponses[0].description)
      assertThat(response[0].accountType).isEqualTo(glResponses[0].subAccount.reference)
      assertThat(response[0].location).isEqualTo("")
      assertThat(response[0].credit).isEqualTo(glResponses[0].amount)
      assertThat(response[0].debit).isEqualTo(0)
      assertThat(response[0].date).isEqualTo(glResponses[0].transactionTimestamp)

      assertThat(response[1].description).isEqualTo(glResponses[1].description)
      assertThat(response[1].accountType).isEqualTo(glResponses[1].subAccount.reference)
      assertThat(response[1].location).isEqualTo("")
      assertThat(response[1].credit).isEqualTo(0)
      assertThat(response[1].debit).isEqualTo(glResponses[1].amount)
      assertThat(response[1].date).isEqualTo(glResponses[1].transactionTimestamp)
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

      val glResponses = listOf(
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

      whenever(generalLedgerApiClient.getStatementForAccountId(prisonerId, null, null)).thenReturn(glResponses)

      val response = transactionService.getPrisonerTransactionsByAccountId(prisonerId, null, null)

      assertThat(response).hasSize(1)
      assertThat(response[0].description).isEqualTo(glResponses[0].description)
      assertThat(response[0].accountType).isEqualTo(glResponses[0].subAccount.reference)
      assertThat(response[0].location).isEqualTo("LEI")
      assertThat(response[0].credit).isEqualTo(glResponses[0].amount)
      assertThat(response[0].debit).isEqualTo(0)
      assertThat(response[0].date).isEqualTo(glResponses[0].transactionTimestamp)
    }
  }
}
