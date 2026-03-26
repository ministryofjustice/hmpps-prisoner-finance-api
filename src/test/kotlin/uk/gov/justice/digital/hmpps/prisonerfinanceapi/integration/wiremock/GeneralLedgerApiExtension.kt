package uk.gov.justice.digital.hmpps.prisonerfinanceapi.integration.wiremock

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.any
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.common.ConsoleNotifier
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.integration.wiremock.GeneralLedgerApiExtension.Companion.generalLedgerApi
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.AccountBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.AccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.PrisonerTransactionListResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.StatementEntryResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.SubAccountBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.SubAccountResponse
import java.time.Instant
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

  fun stubGetStatementEntriesList(
    accountId: UUID,
    response: List<StatementEntryResponse>,
    startDate: String = "",
    endDate: String = "",
  ) {
    stubFor(
      get(
        urlPathEqualTo("/accounts/$accountId/statement"),
      )
        .withQueryParam("startDate", equalTo(startDate))
        .withQueryParam("endDate", equalTo(endDate))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(mapper.writeValueAsString(response))
            .withStatus(200),
        ),
    )
  }

  fun stubGetStatementEntriesListNotFound(accountId: UUID) {
    stubFor(
      get("/accounts/$accountId/statement")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody("GL Account not found")
            .withStatus(404),
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
  fun stubAnyRequestThrows500() {
    generalLedgerApi.stubFor(
      any(urlMatching(".*"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody("GL Internal Server Error")
            .withStatus(500),
        ),
    )
  }

  fun stubGetTransactionThrows404(accountId: UUID) {
    generalLedgerApi.stubFor(
      get("/accounts/$accountId/transactions")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody("GL Account not found")
            .withStatus(404),
        ),
    )
  }
  fun stubGetAccountListWithAccount(accountRef: String, returnAccountId: UUID, subAccounts: List<SubAccountResponse> = emptyList()) {
    generalLedgerApi.stubFor(
      get("/accounts?reference=$accountRef")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              mapper.writeValueAsString(
                listOf<AccountResponse>(
                  AccountResponse(
                    id = returnAccountId,
                    reference = accountRef,
                    createdAt = Instant.now(),
                    createdBy = "",
                    type = AccountResponse.Type.PRISONER,
                    subAccounts = subAccounts,
                  ),
                ),
              ),
            )
            .withStatus(200),
        ),
    )
  }

  fun stubGetAccountListWithNoAccount(accountRef: String) {
    generalLedgerApi.stubFor(
      get("/accounts?reference=$accountRef")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(mapper.writeValueAsString(emptyList<AccountResponse>()))
            .withStatus(200),
        ),
    )
  }

  fun stubGetAccountBalance(accountId: UUID, balanceAmount: Long) {
    generalLedgerApi.stubFor(
      get("/accounts/$accountId/balance")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              mapper.writeValueAsString(
                AccountBalanceResponse(
                  accountId = accountId,
                  balanceDateTime = Instant.now(),
                  amount = balanceAmount,
                ),
              ),
            )
            .withStatus(200),
        ),
    )
  }

  fun stubGetSubAccountBalance(subAccountId: UUID, balanceAmount: Long) {
    generalLedgerApi.stubFor(
      get("/sub-accounts/$subAccountId/balance")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              mapper.writeValueAsString(
                SubAccountBalanceResponse(
                  subAccountId = subAccountId,
                  balanceDateTime = Instant.now(),
                  amount = balanceAmount,
                ),
              ),
            )
            .withStatus(200),
        ),
    )
  }
}
