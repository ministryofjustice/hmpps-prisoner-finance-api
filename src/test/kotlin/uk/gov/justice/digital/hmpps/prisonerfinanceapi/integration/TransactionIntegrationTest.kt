package uk.gov.justice.digital.hmpps.prisonerfinanceapi.integration

import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.config.ROLE_PRISONER_FINANCE__PROFILE__RO
import java.util.UUID

class TransactionIntegrationTest : IntegrationTestBase() {

  @Test
  fun `return a list of transactions when sent a valid account ID`() {
    val response = webTestClient.get()
      .uri("/accounts/${UUID.randomUUID()}/transactions")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__PROFILE__RO)))
      .exchange()
      .expectStatus().isOk
  }
}
