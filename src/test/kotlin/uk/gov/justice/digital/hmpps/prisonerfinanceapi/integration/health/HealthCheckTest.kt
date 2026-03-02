package uk.gov.justice.digital.hmpps.prisonerfinanceapi.integration.health

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.integration.IntegrationTestBase

class HealthCheckTest : IntegrationTestBase() {

  @Test
  fun `Health page reports ok`() {
    stubPingWithResponse(200, 200)

    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("status").isEqualTo("UP")
      .jsonPath("components.hmppsAuth.status").isEqualTo("UP")
      .jsonPath("components.generalLedgerApi.status").isEqualTo("UP")
  }

  @ParameterizedTest
  @CsvSource(
    "200, 503, UP, DOWN",
    "503, 200, DOWN, UP",
    "503, 503, DOWN, DOWN",
  )
  fun `Health page reports down`(statusAuth: Int, statusGeneralLedger: Int, expectedAuthStatus: String, expectedGeneralLedgerStatus: String) {
    stubPingWithResponse(statusAuth, statusGeneralLedger)
    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .is5xxServerError
      .expectBody()
      .jsonPath("status").isEqualTo("DOWN")
      .jsonPath("components.hmppsAuth.status").isEqualTo(expectedAuthStatus)
      .jsonPath("components.generalLedgerApi.status").isEqualTo(expectedGeneralLedgerStatus)
  }

  @Test
  fun `Health ping page is accessible`() {
    webTestClient.get()
      .uri("/health/ping")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("status").isEqualTo("UP")
  }

  @Test
  fun `readiness reports ok`() {
    webTestClient.get()
      .uri("/health/readiness")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("status").isEqualTo("UP")
  }

  @Test
  fun `liveness reports ok`() {
    webTestClient.get()
      .uri("/health/liveness")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("status").isEqualTo("UP")
  }
}
