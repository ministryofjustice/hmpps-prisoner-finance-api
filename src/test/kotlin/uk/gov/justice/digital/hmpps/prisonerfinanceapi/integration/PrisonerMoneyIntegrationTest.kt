package uk.gov.justice.digital.hmpps.prisonerfinanceapi.integration

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matching
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
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
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.AccountBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.StatementEntryAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.StatementEntryOppositePostingsResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.StatementEntryResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.SubAccountBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.SubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.response.PrisonerTransactionResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.services.helpers.ServiceTestHelpers
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.time.Instant
import java.util.UUID

@ExtendWith(HmppsAuthApiExtension::class, GeneralLedgerApiExtension::class)
class PrisonerMoneyIntegrationTest : IntegrationTestBase() {

  val serviceTestHelpers = ServiceTestHelpers()

  @Nested
  inner class PrisonerSubAccountBalance {
    @Test
    fun `should return 200 and sub account balance when sent a valid account reference`() {
      val accountId = UUID.randomUUID()
      val subAccountId = UUID.randomUUID()
      val accountRef = "AE123456"
      val subAccountRef = "CASH"
      val amount = 1000L

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
      generalLedgerApi.stubGetSubAccountBalance(subAccountId = subAccountId, balanceAmount = amount)

      val responseBody = webTestClient.get()
        .uri("/prisoners/$accountRef/money/balance/$subAccountRef")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__PROFILE__RO)))
        .exchange()
        .expectStatus().isOk
        .expectBody<SubAccountBalanceResponse>()
        .returnResult().responseBody!!

      assertThat(responseBody.subAccountId).isEqualTo(subAccountId)
      assertThat(responseBody.balanceDateTime).isInThePast()
      assertThat(responseBody.amount).isEqualTo(amount)
    }

    @Test
    fun `should return 401 when not authorized`() {
      val accountRef = "AF123F33"
      val subAccountRef = "CASH"

      webTestClient.get()
        .uri("/prisoners/$accountRef/money/balance/$subAccountRef")
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `should return 403 Forbidden when role is incorrect`() {
      val accountRef = "AF123F33"
      val subAccountRef = "CASH"

      webTestClient.get()
        .uri("/prisoners/$accountRef/money/balance/$subAccountRef")
        .headers(setAuthorisation(roles = listOf("WRONG_ROLE")))
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.FORBIDDEN)
    }

    @Test
    fun `should return 404 when sub account reference does not exist`() {
      val accountRef = "AS12345"
      val subAccountRef = "CASH"
      val accountId = UUID.randomUUID()

      generalLedgerApi.stubGetAccountListWithAccount(
        accountRef = accountRef,
        returnAccountId = accountId,
      )
      webTestClient.get()
        .uri("/prisoners/$accountRef/money/balance/$subAccountRef")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__PROFILE__RO)))
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.NOT_FOUND)
        .expectBody().jsonPath("$.userMessage").isEqualTo("Sub account not found")
    }

    @Test
    fun `should return 404 when parent account reference does not exist`() {
      val accountRef = "AS12345"
      val subAccountRef = "CASH"

      generalLedgerApi.stubGetAccountListWithNoAccount(accountRef)

      webTestClient.get()
        .uri("/prisoners/$accountRef/money/balance/$subAccountRef")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__PROFILE__RO)))
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.NOT_FOUND)
        .expectBody().jsonPath("$.userMessage").isEqualTo("Account not found")
    }

    @Test
    fun `should return 502 when general ledger is down`() {
      val accountRef = "AS12345"
      val subAccountRef = "CASH"
      generalLedgerApi.stubAnyRequestThrows500()

      webTestClient.get()
        .uri("/prisoners/$accountRef/money/balance/$subAccountRef")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__PROFILE__RO)))
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.BAD_GATEWAY)
    }
  }

  @Nested
  inner class PrisonerAccountBalance {
    @Test
    fun `should return 200 and account balance when sent a valid account reference`() {
      val accountId = UUID.randomUUID()
      val accountRef = "AE123456"
      val amount = 1000L

      generalLedgerApi.stubGetAccountListWithAccount(accountRef = accountRef, returnAccountId = accountId)
      generalLedgerApi.stubGetAccountBalance(accountId = accountId, balanceAmount = amount)

      val responseBody = webTestClient.get()
        .uri("/prisoners/$accountRef/money/balance")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__PROFILE__RO)))
        .exchange()
        .expectStatus().isOk
        .expectBody<AccountBalanceResponse>()
        .returnResult().responseBody!!

      assertThat(responseBody.accountId).isEqualTo(accountId)
      assertThat(responseBody.balanceDateTime).isInThePast()
      assertThat(responseBody.amount).isEqualTo(amount)
    }

    @Test
    fun `should return 401 when not authorized`() {
      val accountRef = "AF123F33"
      webTestClient.get()
        .uri("/prisoners/$accountRef/money/balance")
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `should return 403 Forbidden when role is incorrect`() {
      val accountRef = "AF123F33"

      webTestClient.get()
        .uri("/prisoners/$accountRef/money/balance")
        .headers(setAuthorisation(roles = listOf("WRONG_ROLE")))
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.FORBIDDEN)
    }

    @Test
    fun `should return 404 when account reference does not exist`() {
      val accountRef = "AS12345"
      generalLedgerApi.stubGetAccountListWithNoAccount(accountRef)

      webTestClient.get()
        .uri("/prisoners/$accountRef/money/balance")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__PROFILE__RO)))
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.NOT_FOUND)
        .expectBody().jsonPath("$.userMessage").isEqualTo("Account not found")
    }

    @Test
    fun `should return 502 when general ledger is down`() {
      val accountRef = "AS12345"
      generalLedgerApi.stubAnyRequestThrows500()

      webTestClient.get()
        .uri("/prisoners/$accountRef/money/balance")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__PROFILE__RO)))
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.BAD_GATEWAY)
    }
  }

  @Nested
  inner class PrisonerTransactions {
    @Test
    fun `return a empty list of transactions when sent a valid account reference with no transactions`() {
      val accountRef = "A12345"
      val accountId = UUID.randomUUID()

      generalLedgerApi.stubGetAccountListWithAccount(accountRef, accountId)

      val startDate = "2010-10-10"
      val endDate = "2020-10-10"
      generalLedgerApi.stubGetStatementEntriesList(accountId, emptyList(), startDate, endDate)

      val responseBody = webTestClient.get()
        .uri("/prisoners/$accountRef/money/transactions?startDate=$startDate&endDate=$endDate")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__PROFILE__RO)))
        .exchange()
        .expectStatus().isOk()
        .expectBody<List<StatementEntryResponse>>().returnResult().responseBody!!

      assertThat(responseBody.size).isEqualTo(0)

      generalLedgerApi.verify(
        1,
        getRequestedFor(urlPathEqualTo("/accounts/$accountId/statement"))
          .withQueryParam("startDate", equalTo(startDate))
          .withQueryParam("endDate", equalTo(endDate)),
      )
      generalLedgerApi.verify(
        1,
        getRequestedFor(urlPathEqualTo("/accounts"))
          .withQueryParam("reference", matching(accountRef)),
      )
    }

    @Test
    fun `return a list of prison to prisoner transactions when sent a valid account reference`() {
      val accountId = UUID.randomUUID()
      val accountRef = "AE123456"

      generalLedgerApi.stubGetAccountListWithAccount(accountRef = accountRef, returnAccountId = accountId)

      val parentAccountPrisoner = serviceTestHelpers.createParentAccountResponse(
        reference = "A1234BC",
        StatementEntryAccountResponse.Type.PRISONER,
      )

      val parentAccountPrison = serviceTestHelpers.createParentAccountResponse(
        reference = "LEI",
        StatementEntryAccountResponse.Type.PRISON,
      )

      val subAccountCashPrisoner = serviceTestHelpers.createSubAccountWithParentResponse(parentAccountPrisoner, "CASH")

      val subAccountPrison = serviceTestHelpers.createSubAccountWithParentResponse(parentAccountPrison, "CANT")

      val glResponses = listOf(
        serviceTestHelpers.createStatementEntryResponse(
          subAccount = subAccountCashPrisoner,
          postingType = StatementEntryResponse.PostingType.CR,
          amount = 2L,
          statementOppositePosting = listOf(
            serviceTestHelpers.createStatementEntryOppositePostingResponse(
              subAccountPrison,
              2L,
              StatementEntryOppositePostingsResponse.Type.DR,
            ),
          ),
        ),
      )

      generalLedgerApi.stubGetStatementEntriesList(accountId, glResponses)

      val responseBody = webTestClient.get()
        .uri("/prisoners/$accountRef/money/transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__PROFILE__RO)))
        .exchange()
        .expectStatus().isOk
        .expectBody<List<PrisonerTransactionResponse>>().returnResult().responseBody!!

      val tx1 = responseBody[0]
      assertThat(tx1.date).isEqualTo(glResponses[0].transactionTimestamp)
      assertThat(tx1.description).isEqualTo(glResponses[0].description)
      assertThat(tx1.credit).isEqualTo(2)
      assertThat(tx1.debit).isEqualTo(0)
      assertThat(tx1.location).isEqualTo(parentAccountPrison.reference)
      assertThat(tx1.accountType).isEqualTo(subAccountCashPrisoner.reference)

      generalLedgerApi.verify(
        1,
        getRequestedFor(urlPathMatching("/accounts/$accountId/statement")),
      )
      generalLedgerApi.verify(
        1,
        getRequestedFor(urlPathEqualTo("/accounts"))
          .withQueryParam("reference", matching(accountRef)),
      )
    }

    @Test
    fun `return a list of prisoner to prisoner transactions when sent a valid account reference`() {
      val accountId = UUID.randomUUID()

      val parentAccount = serviceTestHelpers.createParentAccountResponse(
        reference = "A1234BC",
        StatementEntryAccountResponse.Type.PRISONER,
      )

      val subAccountCash = serviceTestHelpers.createSubAccountWithParentResponse(parentAccount, "CASH")

      val subAccountSavings = serviceTestHelpers.createSubAccountWithParentResponse(parentAccount, "SAVINGS")

      val glResponses = listOf(
        serviceTestHelpers.createStatementEntryResponse(
          subAccount = subAccountCash,
          postingType = StatementEntryResponse.PostingType.CR,
          amount = 2L,
          statementOppositePosting = listOf(
            serviceTestHelpers.createStatementEntryOppositePostingResponse(
              subAccountSavings,
              2L,
              StatementEntryOppositePostingsResponse.Type.DR,
            ),
          ),
        ),
        serviceTestHelpers.createStatementEntryResponse(
          subAccount = subAccountSavings,
          postingType = StatementEntryResponse.PostingType.DR,
          amount = 2L,
          statementOppositePosting = listOf(
            serviceTestHelpers.createStatementEntryOppositePostingResponse(
              subAccountCash,
              2L,
              StatementEntryOppositePostingsResponse.Type.CR,
            ),
          ),
        ),
      )

      generalLedgerApi.stubGetAccountListWithAccount(accountRef = parentAccount.reference, returnAccountId = accountId)
      generalLedgerApi.stubGetStatementEntriesList(accountId, glResponses)

      val responseBody = webTestClient.get()
        .uri("/prisoners/${parentAccount.reference}/money/transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__PROFILE__RO)))
        .exchange()
        .expectStatus().isOk
        .expectBody<List<PrisonerTransactionResponse>>()
        .returnResult().responseBody!!

      assertThat(responseBody.size).isEqualTo(2)

      val tx1 = responseBody[0]
      assertThat(tx1.date).isEqualTo(glResponses[0].transactionTimestamp)
      assertThat(tx1.description).isEqualTo(glResponses[0].description)
      assertThat(tx1.credit).isEqualTo(2)
      assertThat(tx1.debit).isEqualTo(0)
      assertThat(tx1.location).isEqualTo("")
      assertThat(tx1.accountType).isEqualTo("CASH")

      val tx2 = responseBody[1]
      assertThat(tx2.date).isEqualTo(glResponses[1].transactionTimestamp)
      assertThat(tx2.description).isEqualTo(glResponses[1].description)
      assertThat(tx2.credit).isEqualTo(0)
      assertThat(tx2.debit).isEqualTo(2)
      assertThat(tx2.location).isEqualTo("")
      assertThat(tx2.accountType).isEqualTo("SAVINGS")

      generalLedgerApi.verify(1, getRequestedFor(urlPathMatching("/accounts/$accountId/statement")))

      generalLedgerApi.verify(
        1,
        getRequestedFor(urlPathEqualTo("/accounts"))
          .withQueryParam("reference", matching(parentAccount.reference)),
      )
    }

    @Test
    fun `Should throw custom exception internal server error when no opposite postings are provided`() {
      val accountId = UUID.randomUUID()

      val parentAccountPrisoner = serviceTestHelpers.createParentAccountResponse(
        reference = "A1234BC",
        StatementEntryAccountResponse.Type.PRISONER,
      )

      val subAccountCashPrisoner = serviceTestHelpers.createSubAccountWithParentResponse(parentAccountPrisoner, "CASH")

      val glResponses = listOf(
        serviceTestHelpers.createStatementEntryResponse(
          subAccount = subAccountCashPrisoner,
          postingType = StatementEntryResponse.PostingType.CR,
          amount = 2L,
          statementOppositePosting = listOf(),
        ),
      )

      generalLedgerApi.stubGetAccountListWithAccount(accountRef = parentAccountPrisoner.reference, returnAccountId = accountId)
      generalLedgerApi.stubGetStatementEntriesList(accountId, glResponses)

      val exception =
        webTestClient.get()
          .uri("/prisoners/${parentAccountPrisoner.reference}/money/transactions")
          .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__PROFILE__RO)))
          .exchange()
          .expectStatus().is5xxServerError
          .expectBody<ErrorResponse>()
          .returnResult().responseBody!!

      assertThat(exception.status).isEqualTo(500)
      assertThat(exception.userMessage).isEqualTo("Unexpected posting without an opposite posting")
    }

    @Test
    fun `should return 401 when not authorized`() {
      val accountRef = "AF123F33"
      webTestClient.get()
        .uri("/prisoners/$accountRef/money/transactions")
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `should return 403 Forbidden when role is incorrect`() {
      val accountRef = "AF123F33"

      webTestClient.get()
        .uri("/prisoners/$accountRef/money/transactions")
        .headers(setAuthorisation(roles = listOf("WRONG_ROLE")))
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.FORBIDDEN)
    }

    @Test
    fun `should return 404 when account reference does not exist`() {
      val accountRef = "AS12345"

      generalLedgerApi.stubGetAccountListWithNoAccount(accountRef)

      webTestClient.get()
        .uri("/prisoners/$accountRef/money/transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__PROFILE__RO)))
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.NOT_FOUND)
    }

    @Test
    fun `should return 502 when general ledger is down`() {
      val accountRef = "AS12345"
      generalLedgerApi.stubAnyRequestThrows500()

      webTestClient.get()
        .uri("/prisoners/$accountRef/money/transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__PROFILE__RO)))
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.BAD_GATEWAY)
    }
  }
}
