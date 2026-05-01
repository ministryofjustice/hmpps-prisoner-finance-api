package uk.gov.justice.digital.hmpps.prisonerfinanceapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpStatus
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.config.ROLE_PRISONER_FINANCE__PROFILE__RO
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.config.ROLE_PRISONER_FINANCE__PROFILE__RW
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.integration.wiremock.GeneralLedgerApiExtension
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.integration.wiremock.GeneralLedgerApiExtension.Companion.generalLedgerApi
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.integration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.PostingResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.TransactionResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.request.CreateTransactionFormRequest
import java.time.Instant
import java.util.UUID

@ExtendWith(HmppsAuthApiExtension::class, GeneralLedgerApiExtension::class)
class TransactionIntegrationTest : IntegrationTestBase() {

  @Nested
  inner class CreateTransaction {

    fun stubPostTransaction(creditSubAccountId: UUID, debitSubAccountId: UUID, amount: Long, description: String): TransactionResponse {
      val payload = TransactionResponse(
        id = UUID.randomUUID(),
        createdBy = "",
        createdAt = Instant.now(),
        reference = UUID.randomUUID().toString(),
        description = description,
        timestamp = Instant.now(),
        amount = amount,
        postings = listOf(
          PostingResponse(
            id = UUID.randomUUID(),
            createdBy = "",
            createdAt = Instant.now(),
            type = PostingResponse.Type.DR,
            amount = amount,
            subAccountID = debitSubAccountId,
          ),
          PostingResponse(
            id = UUID.randomUUID(),
            createdBy = "",
            createdAt = Instant.now(),
            type = PostingResponse.Type.CR,
            amount = amount,
            subAccountID = creditSubAccountId,
          ),
        ),
      )

      generalLedgerApi.stubPostTransaction(payload)

      return payload
    }

    @Test
    fun `Should return 200 and the transaction id`() {
      val creditSubAccountId = UUID.randomUUID()
      val debitSubAccountId = UUID.randomUUID()
      val amount = 5L
      val description = "TEST"

      val stubbedTransactionResponse = stubPostTransaction(creditSubAccountId, debitSubAccountId, amount, description)
      val uiFormRequest = CreateTransactionFormRequest(
        creditSubAccountId = creditSubAccountId,
        debitSubAccountId = debitSubAccountId,
        amount = amount,
        description = description,
      )

      val responseBody = webTestClient.post()
        .uri("/transaction")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__PROFILE__RW)))
        .bodyValue(uiFormRequest)
        .exchange()
        .expectStatus().isOk
        .expectBody<TransactionResponse>()
        .returnResult().responseBody!!

      assertThat(responseBody).isEqualTo(
        stubbedTransactionResponse,
      )
    }

    @Test
    fun `Should return 400 if the transaction form data is invalid`() {
      val uiFormRequest = mapOf(
        "creditSubAccountId" to "test",
        "debitSubAccountId" to "test",
        "amount" to "fiver",
        "description" to "TEST",
      )

      webTestClient.post()
        .uri("/transaction")
        .bodyValue(uiFormRequest)
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__PROFILE__RW)))
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `Should return 400 Bad Request if General Ledger responds with 400`() {
      val creditSubAccountId = UUID.randomUUID()
      val debitSubAccountId = UUID.randomUUID()
      val amount = 5L
      val description = "TEST"

      val uiFormRequest = mapOf(
        "creditSubAccountId" to creditSubAccountId,
        "debitSubAccountId" to debitSubAccountId,
        "amount" to amount,
        "description" to description,
      )

      generalLedgerApi.stubPostTransactionBadRequest()

      webTestClient.post()
        .uri("/transaction")
        .bodyValue(uiFormRequest)
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__PROFILE__RW)))
        .exchange()
        .expectStatus().isBadRequest
    }

    @Test
    fun `Should return 403 Forbidden if the user does not have the correct role`() {
      val creditSubAccountId = UUID.randomUUID()
      val debitSubAccountId = UUID.randomUUID()
      val amount = 5L
      val description = "TEST"

      val uiFormRequest = CreateTransactionFormRequest(
        creditSubAccountId = creditSubAccountId,
        debitSubAccountId = debitSubAccountId,
        amount = amount,
        description = description,
      )

      webTestClient.post()
        .uri("/transaction")
        .bodyValue(uiFormRequest)
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__PROFILE__RO)))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `Should return 404 if the account does not exist`() {
      val creditSubAccountId = UUID.randomUUID()
      val debitSubAccountId = UUID.randomUUID()
      val amount = 5L
      val description = "TEST"

      val uiFormRequest = CreateTransactionFormRequest(
        creditSubAccountId = creditSubAccountId,
        debitSubAccountId = debitSubAccountId,
        amount = amount,
        description = description,
      )

      generalLedgerApi.stubPostTransactionSubAccountIDNotFound()

      webTestClient.post()
        .uri("/transaction")
        .bodyValue(uiFormRequest)
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__PROFILE__RW)))
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    fun `Should return 502 if General Ledger is down`() {
      val creditSubAccountId = UUID.randomUUID()
      val debitSubAccountId = UUID.randomUUID()
      val amount = 5L
      val description = "TEST"

      val uiFormRequest = CreateTransactionFormRequest(
        creditSubAccountId = creditSubAccountId,
        debitSubAccountId = debitSubAccountId,
        amount = amount,
        description = description,
      )

      generalLedgerApi.stubAnyRequestThrows500()

      webTestClient.post()
        .uri("/transaction")
        .bodyValue(uiFormRequest)
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__PROFILE__RW)))
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.BAD_GATEWAY)
    }
  }
}
