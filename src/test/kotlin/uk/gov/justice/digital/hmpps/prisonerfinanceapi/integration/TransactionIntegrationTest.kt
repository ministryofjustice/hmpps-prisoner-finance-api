package uk.gov.justice.digital.hmpps.prisonerfinanceapi.integration

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpStatus
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.config.ROLE_PRISONER_FINANCE__PROFILE__RO
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.integration.wiremock.GeneralLedgerApiExtension
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.integration.wiremock.GeneralLedgerApiExtension.Companion.generalLedgerApi
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.integration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.ParentAccountListResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.PrisonerPostingListResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.response.PrisonerFinanceTransactionResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.services.helpers.ServiceTestHelpers
import java.time.Instant
import java.util.UUID

@ExtendWith(HmppsAuthApiExtension::class, GeneralLedgerApiExtension::class)
class TransactionIntegrationTest : IntegrationTestBase() {

  val serviceTestHelpers = ServiceTestHelpers()

  @Test
  fun `return a empty list of transactions when sent a valid account ID with no transactions`() {
    val accountId = UUID.randomUUID()
    generalLedgerApi.stubGetTransactionList(accountId, listOf())

    val responseBody = webTestClient.get()
      .uri("/accounts/$accountId/transactions")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__PROFILE__RO)))
      .exchange()
      .expectStatus().isOk()
      .expectBody<List<PrisonerFinanceTransactionResponse>>().returnResult().responseBody!!

    assertThat(responseBody.size).isEqualTo(0)

    generalLedgerApi.verify(1, getRequestedFor(urlPathMatching("/accounts/$accountId/transactions")))
  }

  @Test
  fun `return a list of prison to prisoner transactions when sent a valid account ID`() {
    val accountId = UUID.randomUUID()

    val request = serviceTestHelpers.createTransactionListResponse(
      timestamp = Instant.now(),
      description = "CASH_TO_CANTEEN",
      postings = listOf(
        serviceTestHelpers.createPosting(
          10L,
          PrisonerPostingListResponse.Type.DR,
          "CASH",
          "AB123F33",
          ParentAccountListResponse.Type.PRISONER,
        ),
        serviceTestHelpers.createPosting(
          10L,
          PrisonerPostingListResponse.Type.CR,
          "1001:CANT",
          "LEI",
          ParentAccountListResponse.Type.PRISON,
        ),
      ),
    )

    generalLedgerApi.stubGetTransactionList(accountId, listOf(request))

    val responseBody = webTestClient.get()
      .uri("/accounts/$accountId/transactions")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__PROFILE__RO)))
      .exchange()
      .expectStatus().isOk
      .expectBody<List<PrisonerFinanceTransactionResponse>>().returnResult().responseBody!!

    val tx1 = responseBody[0]
    assertThat(tx1.date).isEqualTo(request.timestamp)
    assertThat(tx1.description).isEqualTo("CASH_TO_CANTEEN")
    assertThat(tx1.credit).isEqualTo(0)
    assertThat(tx1.debit).isEqualTo(10)
    assertThat(tx1.location).isEqualTo("LEI")
    assertThat(tx1.accountType).isEqualTo("CASH")

    generalLedgerApi.verify(1, getRequestedFor(urlPathMatching("/accounts/$accountId/transactions")))
  }

  @Test
  fun `return a list of prisoner to prisoner transactions when sent a valid account ID`() {
    val accountId = UUID.randomUUID()

    val request = serviceTestHelpers.createTransactionListResponse(
      timestamp = Instant.now(),
      description = "CASH_TO_SAVINGS",
      postings = listOf(
        serviceTestHelpers.createPosting(
          10L,
          PrisonerPostingListResponse.Type.DR,
          "CASH",
          "AB123F33",
          ParentAccountListResponse.Type.PRISONER,
        ),
        serviceTestHelpers.createPosting(
          10L,
          PrisonerPostingListResponse.Type.CR,
          "SAVINGS",
          "AB123F33",
          ParentAccountListResponse.Type.PRISONER,
        ),
      ),
    )

    generalLedgerApi.stubGetTransactionList(accountId, listOf(request))

    val responseBody = webTestClient.get()
      .uri("/accounts/$accountId/transactions")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__PROFILE__RO)))
      .exchange()
      .expectStatus().isOk
      .expectBody<List<PrisonerFinanceTransactionResponse>>()
      .returnResult().responseBody!!

    assertThat(responseBody.size).isEqualTo(2)

    val tx1 = responseBody[0]
    assertThat(tx1.date).isEqualTo(request.timestamp)
    assertThat(tx1.description).isEqualTo(request.description)
    assertThat(tx1.debit).isEqualTo(10)
    assertThat(tx1.credit).isEqualTo(0)
    assertThat(tx1.location).isEqualTo("")
    assertThat(tx1.accountType).isEqualTo("CASH")

    val tx2 = responseBody[1]
    assertThat(tx2.date).isEqualTo(request.timestamp)
    assertThat(tx2.description).isEqualTo(request.description)
    assertThat(tx2.debit).isEqualTo(0)
    assertThat(tx2.credit).isEqualTo(10)
    assertThat(tx2.location).isEqualTo("")
    assertThat(tx2.accountType).isEqualTo("SAVINGS")

    generalLedgerApi.verify(1, getRequestedFor(urlPathMatching("/accounts/$accountId/transactions")))
  }

  @Test
  fun `return a list of multiple transactions when sent a valid account ID`() {
    val accountId = UUID.randomUUID()

    val requestOne = serviceTestHelpers.createTransactionListResponse(
      timestamp = Instant.now(),
      description = "CASH_TO_CANTEEN",
      postings = listOf(
        serviceTestHelpers.createPosting(
          10L,
          PrisonerPostingListResponse.Type.DR,
          "CASH",
          "AB123F33",
          ParentAccountListResponse.Type.PRISONER,
        ),
        serviceTestHelpers.createPosting(
          10L,
          PrisonerPostingListResponse.Type.CR,
          "1001:CANT",
          "LEI",
          ParentAccountListResponse.Type.PRISON,
        ),
      ),
    )

    val requestTwo = serviceTestHelpers.createTransactionListResponse(
      timestamp = Instant.now(),
      description = "CASH_TO_SAVINGS",
      postings = listOf(
        serviceTestHelpers.createPosting(
          10L,
          PrisonerPostingListResponse.Type.DR,
          "CASH",
          "AB123F33",
          ParentAccountListResponse.Type.PRISONER,
        ),
        serviceTestHelpers.createPosting(
          10L,
          PrisonerPostingListResponse.Type.CR,
          "SAVINGS",
          "AB123F33",
          ParentAccountListResponse.Type.PRISONER,
        ),
      ),
    )

    // Transactions ordered by timestamp descending
    generalLedgerApi.stubGetTransactionList(accountId, listOf(requestTwo, requestOne))

    val responseBody = webTestClient.get()
      .uri("/accounts/$accountId/transactions")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__PROFILE__RO)))
      .exchange()
      .expectStatus().isOk
      .expectBody<List<PrisonerFinanceTransactionResponse>>().returnResult().responseBody!!

    assertThat(responseBody.size).isEqualTo(3)

    val tx1 = responseBody[0]
    assertThat(tx1.date).isEqualTo(requestTwo.timestamp)
    assertThat(tx1.description).isEqualTo("CASH_TO_SAVINGS")
    assertThat(tx1.credit).isEqualTo(0)
    assertThat(tx1.debit).isEqualTo(10)
    assertThat(tx1.location).isEqualTo("")
    assertThat(tx1.accountType).isEqualTo("CASH")

    val tx2 = responseBody[1]
    assertThat(tx2.date).isEqualTo(requestTwo.timestamp)
    assertThat(tx2.description).isEqualTo("CASH_TO_SAVINGS")
    assertThat(tx2.credit).isEqualTo(10)
    assertThat(tx2.debit).isEqualTo(0)
    assertThat(tx2.location).isEqualTo("")
    assertThat(tx2.accountType).isEqualTo("SAVINGS")

    val tx3 = responseBody[2]
    assertThat(tx3.date).isEqualTo(requestOne.timestamp)
    assertThat(tx3.description).isEqualTo("CASH_TO_CANTEEN")
    assertThat(tx3.credit).isEqualTo(0)
    assertThat(tx3.debit).isEqualTo(10)
    assertThat(tx3.location).isEqualTo("LEI")
    assertThat(tx3.accountType).isEqualTo("CASH")

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
  fun `should return 403 Forbidden when role is incorrect`() {
    val accountId = UUID.randomUUID()

    webTestClient.get()
      .uri("/accounts/$accountId/transactions")
      .headers(setAuthorisation(roles = listOf("WRONG_ROLE")))
      .exchange()
      .expectStatus().isEqualTo(HttpStatus.FORBIDDEN)
  }

  @Test
  fun `should return 404 when account ID does not exist`() {
    val accountId = UUID.randomUUID()
    generalLedgerApi.stubGetTransactionThrows404(accountId)
    webTestClient.get()
      .uri("/accounts/$accountId/transactions")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__PROFILE__RO)))
      .exchange()
      .expectStatus().isEqualTo(HttpStatus.NOT_FOUND)
  }

  @Test
  fun `should return 503 when general ledger is down`() {
    val accountId = UUID.randomUUID()

    generalLedgerApi.stubGetTransactionThrows500(accountId)

    webTestClient.get()
      .uri("/accounts/$accountId/transactions")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__PROFILE__RO)))
      .exchange()
      .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
  }
}
