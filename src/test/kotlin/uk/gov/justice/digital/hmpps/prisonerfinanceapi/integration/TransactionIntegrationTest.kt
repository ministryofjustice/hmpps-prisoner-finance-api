package uk.gov.justice.digital.hmpps.prisonerfinanceapi.integration

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.client.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.config.ROLE_PRISONER_FINANCE__PROFILE__RO
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.integration.wiremock.GeneralLedgerApiExtension
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.integration.wiremock.GeneralLedgerApiExtension.Companion.generalLedgerApi
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.integration.wiremock.HmppsAuthApiExtension
import java.util.UUID

@ExtendWith(HmppsAuthApiExtension::class, GeneralLedgerApiExtension::class)
class TransactionIntegrationTest(@Autowired private val generalLedgerApiClient: GeneralLedgerApiClient) : IntegrationTestBase() {

  @Test
  fun `return a list of transactions when sent a valid account ID`() {

    generalLedgerApi.stubGetTransactionList(UUID.randomUUID(), listOf())

    val response = webTestClient.get()
      .uri("/accounts/${UUID.randomUUID()}/transactions")
      .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__PROFILE__RO)))
      .exchange()
      .expectStatus().isOk
  }
}
