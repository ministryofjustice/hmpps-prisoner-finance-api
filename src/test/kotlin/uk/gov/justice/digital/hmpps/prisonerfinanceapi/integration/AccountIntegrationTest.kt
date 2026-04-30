package uk.gov.justice.digital.hmpps.prisonerfinanceapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.http.HttpStatus
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.config.ROLE_PRISONER_FINANCE__PROFILE__RO
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.integration.wiremock.GeneralLedgerApiExtension
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.integration.wiremock.GeneralLedgerApiExtension.Companion.generalLedgerApi
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.integration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.AccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.SubAccountResponse
import java.time.Instant
import java.util.UUID

@ExtendWith(HmppsAuthApiExtension::class, GeneralLedgerApiExtension::class)
class AccountIntegrationTest : IntegrationTestBase() {

  @Nested
  inner class GetAccountByRef {
    @Test
    fun `Should return 200 and an account with its associated subaccounts`() {
      val accountId = UUID.randomUUID()
      val subAccountId = UUID.randomUUID()
      val accountRef = "AE123456"
      val subAccountRef = "CASH"

      generalLedgerApi.stubGetAccountListWithAccount(
        accountRef = accountRef,
        returnAccountId = accountId,
        subAccounts = listOf(
          SubAccountResponse(
            id = subAccountId,
            reference = subAccountRef,
            parentAccountId = accountId,
            createdBy = "TEST_USER",
            createdAt = Instant.now(),
          ),
        ),
      )

      val responseBody = webTestClient.get()
        .uri("/accounts/$accountRef")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__PROFILE__RO)))
        .exchange()
        .expectStatus().isOk
        .expectBody<AccountResponse>()
        .returnResult().responseBody!!

      assertThat(responseBody.id).isEqualTo(accountId)
      assertThat(responseBody.subAccounts.size).isEqualTo(1)
      assertThat(responseBody.subAccounts[0].id).isEqualTo(subAccountId)
      assertThat(responseBody.subAccounts[0].reference).isEqualTo(subAccountRef)
    }

    @Test
    fun `should return 403 Forbidden when role is incorrect`() {
      val accountRef = "AF123F33"

      webTestClient.get()
        .uri("/accounts/$accountRef")
        .headers(setAuthorisation(roles = listOf("WRONG_ROLE")))
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.FORBIDDEN)
    }

    @Test
    fun `Should return 404 when account not found`() {
      val accountRef = "AE123456"

      generalLedgerApi.stubGetAccountListWithNoAccount(accountRef)

      webTestClient.get()
        .uri("/accounts/$accountRef")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__PROFILE__RO)))
        .exchange()
        .expectStatus().isNotFound
    }
  }
}
