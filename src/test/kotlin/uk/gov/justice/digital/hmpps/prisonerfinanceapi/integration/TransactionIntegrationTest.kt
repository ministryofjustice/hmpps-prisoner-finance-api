package uk.gov.justice.digital.hmpps.prisonerfinanceapi.integration

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.config.ROLE_PRISONER_FINANCE__PROFILE__RO
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.integration.wiremock.GeneralLedgerApiExtension
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.integration.wiremock.GeneralLedgerApiExtension.Companion.generalLedgerApi
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.integration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.ParentAccountListResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.PrisonerPostingListResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.services.helpers.ServiceTestHelpers
import java.util.UUID
import kotlin.text.get

@ExtendWith(HmppsAuthApiExtension::class, GeneralLedgerApiExtension::class)
class TransactionIntegrationTest : IntegrationTestBase() {

  val serviceTestHelpers = ServiceTestHelpers()

  @Test
  fun `return a list of prisoner to prisoner transactions when sent a valid account ID`() {
    val accountId = UUID.randomUUID()
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

    generalLedgerApi.stubGetTransactionList(accountId, listOf(request))

    webTestClient.get()
      .uri("/accounts/$accountId/transactions")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__PROFILE__RO)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.size()").isEqualTo(2)
      .jsonPath("$[0].date").isEqualTo(request.timestamp)
      .jsonPath("$[0].description").isEqualTo(request.description)
      .jsonPath("$[0].credit").isEqualTo(10)
      .jsonPath("$[0].debit").isEqualTo(10)
      .jsonPath("$[0].location").isEqualTo("")
      .jsonPath("$[0].accountType").isEqualTo("CASH")
      .jsonPath("$[1].date").isEqualTo(request.timestamp)
      .jsonPath("$[1].description").isEqualTo(request.description)
      .jsonPath("$[1].credit").isEqualTo(10)
      .jsonPath("$[1].debit").isEqualTo(10)
      .jsonPath("$[1].location").isEqualTo("")
      .jsonPath("$[1].accountType").isEqualTo("SAVINGS")

    generalLedgerApi.verify(1, getRequestedFor(urlPathMatching("/accounts/$accountId/transactions")))
  }

  @Test
  fun `return a list of multiple transactions when sent a valid account ID`() {
    val accountId = UUID.randomUUID()

    val requestOne = serviceTestHelpers.createTransactionListResponse(
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

    val requestTwo = serviceTestHelpers.createTransactionListResponse(
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

    generalLedgerApi.stubGetTransactionList(accountId, listOf(requestOne, requestTwo))

    webTestClient.get()
      .uri("/accounts/$accountId/transactions")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__PROFILE__RO)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.size()").isEqualTo(3)
      .jsonPath("$[0].date").isEqualTo(requestOne.timestamp)
      .jsonPath("$[0].description").isEqualTo(requestOne.description)
      .jsonPath("$[0].credit").isEqualTo(10)
      .jsonPath("$[0].debit").isEqualTo(10)
      .jsonPath("$[0].location").isEqualTo("LEI")
      .jsonPath("$[0].accountType").isEqualTo("CASH")
      .jsonPath("$[1].date").isEqualTo(requestTwo.timestamp)
      .jsonPath("$[1].description").isEqualTo(requestTwo.description)
      .jsonPath("$[1].credit").isEqualTo(10)
      .jsonPath("$[1].debit").isEqualTo(10)
      .jsonPath("$[1].location").isEqualTo("")
      .jsonPath("$[1].accountType").isEqualTo("CASH")
      .jsonPath("$[2].date").isEqualTo(requestTwo.timestamp)
      .jsonPath("$[2].description").isEqualTo(requestTwo.description)
      .jsonPath("$[2].credit").isEqualTo(10)
      .jsonPath("$[2].debit").isEqualTo(10)
      .jsonPath("$[2].location").isEqualTo("")
      .jsonPath("$[2].accountType").isEqualTo("SAVINGS")

    generalLedgerApi.verify(1, getRequestedFor(urlPathMatching("/accounts/$accountId/transactions")))
  }

  @Test
  fun `return a list of prison to prisoner transactions when sent a valid account ID`() {
    val accountId = UUID.randomUUID()
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

    generalLedgerApi.stubGetTransactionList(accountId, listOf(request))

    webTestClient.get()
      .uri("/accounts/$accountId/transactions")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__PROFILE__RO)))
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.size()").isEqualTo(1)
      .jsonPath("$[0].date").isEqualTo(request.timestamp)
      .jsonPath("$[0].description").isEqualTo(request.description)
      .jsonPath("$[0].credit").isEqualTo(10)
      .jsonPath("$[0].debit").isEqualTo(10)
      .jsonPath("$[0].location").isEqualTo("LEI")
      .jsonPath("$[0].accountType").isEqualTo("CASH")

    generalLedgerApi.verify(1, getRequestedFor(urlPathMatching("/accounts/$accountId/transactions")))
  }

  @Test
  fun `return a empty list of transactions when sent a valid account ID with no transactions`() {
    val accountId = UUID.randomUUID()
    generalLedgerApi.stubGetTransactionList(accountId, listOf())

    webTestClient.get()
      .uri("/accounts/$accountId/transactions")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__PROFILE__RO)))
      .exchange()
      .expectStatus().isOk()
      .expectBody()
      .jsonPath("$.size()").isEqualTo(0)

    generalLedgerApi.verify(1, getRequestedFor(urlPathMatching("/accounts/$accountId/transactions")))
  }

  @Test
  fun `Get list of transactions should return 400 Bad Request when account ID is invalid`() {
    val accountId = "SAMPLE"
    webTestClient
      .get()
      .uri("/accounts/$accountId/transactions")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__PROFILE__RO)))
      .exchange()
      .expectStatus().isBadRequest
  }

  @Test
  fun `should return 503 when general ledger is down`() {
    val accountId = UUID.randomUUID()

    generalLedgerApi.stubGetTransactionThrows500(accountId)

    webTestClient.get()
      .uri("/accounts/$accountId/transactions")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__PROFILE__RO)))
      .exchange()
      .expectStatus().isOk
  }
}
