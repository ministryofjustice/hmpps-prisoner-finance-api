package uk.gov.justice.digital.hmpps.prisonerfinanceapi.integration.wiremock

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.common.ConsoleNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.PrisonerTransactionListResponse
import java.util.UUID

class GeneralLedgerApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {

  companion object {
    val generalLedgerApi = GeneralLedgerApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    generalLedgerApi.start()
  }

  override fun afterAll(context: ExtensionContext) {
    generalLedgerApi.stop()
  }

  override fun beforeEach(context: ExtensionContext) {
    generalLedgerApi.resetAll()
  }
}

class GeneralLedgerApiMockServer :
  WireMockServer(
    WireMockConfiguration.wireMockConfig()
      .port(8091)
      .notifier(ConsoleNotifier(true)),
  ) {

  private val mapper = ObjectMapper().registerModule(JavaTimeModule())

  fun stubHealthPing(status: Int) {
    stubFor(
      get("/health/ping").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) """{"status":"UP"}""" else """{"status":"DOWN"}""")
          .withStatus(status),
      ),
    )
  }
  fun stubGetTransactionList(accountId: UUID, response: List<PrisonerTransactionListResponse>) {
    stubFor(
      get("/accounts/$accountId/transactions")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(mapper.writeValueAsString(response))
            .withStatus(200),
        ),
    )
  }
}
