package uk.gov.justice.digital.hmpps.prisonerfinanceapi.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.client.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.ParentAccountListResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.PrisonerPostingListResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.response.PrisonerFinanceTransactionResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.services.helpers.ServiceTestHelpers
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class TransactionServiceTest {

  val serviceTestHelpers = ServiceTestHelpers()

  @Mock private lateinit var generalLedgerApiClient: GeneralLedgerApiClient

  @InjectMocks private lateinit var transactionService: TransactionService

  @Nested
  inner class TransformForUI {

    @Test
    fun `Should return empty list if given an empty list`() {
      val prisonerId = UUID.randomUUID()

      whenever(generalLedgerApiClient.getListOfTransactionsByAccountId(prisonerId)).thenReturn(emptyList())

      val response = transactionService.getPrisonerTransactionsByAccountId(prisonerId)
      assertThat(response).isEmpty()
    }

    @Test
    fun `should return a list of transactions when given a valid prisoner ID`() {
      val prisonerId = UUID.randomUUID()

      val request = serviceTestHelpers.createTransactionListResponse(
        listOf(
          serviceTestHelpers.createPrisonerPosting(
            10L,
            PrisonerPostingListResponse.Type.DR,
            "CASH",
            "AB123F33",
            ParentAccountListResponse.Type.PRISONER,
          ),
          serviceTestHelpers.createPrisonerPosting(
            10L,
            PrisonerPostingListResponse.Type.CR,
            "1001:CANT",
            "LEI",
            ParentAccountListResponse.Type.PRISON,
          ),
        ),
      )

      val expectedResponse = listOf(PrisonerFinanceTransactionResponse(request.timestamp, request.description, 10L, 10L, "LEI", "CASH"))

      whenever(generalLedgerApiClient.getListOfTransactionsByAccountId(prisonerId)).thenReturn(listOf(request))

      val response = transactionService.getPrisonerTransactionsByAccountId(prisonerId)

      assertEquals(expectedResponse, response)
    }

    @Test
    fun `Should return a list of transactions when transactions occur between prisoner accounts`() {
      val prisonerId = UUID.randomUUID()

      val request = serviceTestHelpers.createTransactionListResponse(
        listOf(
          serviceTestHelpers.createPrisonerPosting(
            10L,
            PrisonerPostingListResponse.Type.DR,
            "CASH",
            "AB123F33",
            ParentAccountListResponse.Type.PRISONER,
          ),
          serviceTestHelpers.createPrisonerPosting(
            10L,
            PrisonerPostingListResponse.Type.CR,
            "SAVINGS",
            "AB123F33",
            ParentAccountListResponse.Type.PRISONER,
          ),
        ),
      )

      val expectedResponse = listOf(
        PrisonerFinanceTransactionResponse(request.timestamp, request.description, 10L, 10L, "", "CASH"),
        PrisonerFinanceTransactionResponse(request.timestamp, request.description, 10L, 10L, "", "SAVINGS"),
      )

      whenever(generalLedgerApiClient.getListOfTransactionsByAccountId(prisonerId)).thenReturn(listOf(request))

      val response = transactionService.getPrisonerTransactionsByAccountId(prisonerId)

      assertEquals(expectedResponse, response)
    }
  }
}
