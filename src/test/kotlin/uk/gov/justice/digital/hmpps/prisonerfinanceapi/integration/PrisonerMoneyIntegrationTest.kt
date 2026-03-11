package uk.gov.justice.digital.hmpps.prisonerfinanceapi.integration

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
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.ParentAccountListResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.PrisonerPostingListResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.response.PrisonerTransactionResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.services.helpers.ServiceTestHelpers
import java.time.Instant
import java.util.UUID

@ExtendWith(HmppsAuthApiExtension::class, GeneralLedgerApiExtension::class)
class PrisonerMoneyIntegrationTest : IntegrationTestBase() {

  val serviceTestHelpers = ServiceTestHelpers()

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

      generalLedgerApi.stubGetTransactionList(accountId, emptyList())

      val responseBody = webTestClient.get()
        .uri("/prisoners/$accountRef/money/transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__PROFILE__RO)))
        .exchange()
        .expectStatus().isOk()
        .expectBody<List<PrisonerTransactionResponse>>().returnResult().responseBody!!

      assertThat(responseBody.size).isEqualTo(0)

      generalLedgerApi.verify(
        1,
        getRequestedFor(urlPathMatching("/accounts/$accountId/transactions")),
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

      val request = serviceTestHelpers.createTransactionListResponse(
        timestamp = Instant.now(),
        description = "CASH_TO_CANTEEN",
        postings = listOf(
          serviceTestHelpers.createPosting(
            amount = 10L,
            postingType = PrisonerPostingListResponse.Type.DR,
            subAccountRef = "CASH",
            reference = "AB123F33",
            accountType = ParentAccountListResponse.Type.PRISONER,
          ),
          serviceTestHelpers.createPosting(
            amount = 10L,
            postingType = PrisonerPostingListResponse.Type.CR,
            subAccountRef = "1001:CANT",
            reference = "LEI",
            accountType = ParentAccountListResponse.Type.PRISON,
          ),
        ),
      )

      generalLedgerApi.stubGetTransactionList(accountId, listOf(request))

      val responseBody = webTestClient.get()
        .uri("/prisoners/$accountRef/money/transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__PROFILE__RO)))
        .exchange()
        .expectStatus().isOk
        .expectBody<List<PrisonerTransactionResponse>>().returnResult().responseBody!!

      val tx1 = responseBody[0]
      assertThat(tx1.date).isEqualTo(request.timestamp)
      assertThat(tx1.description).isEqualTo("CASH_TO_CANTEEN")
      assertThat(tx1.credit).isEqualTo(0)
      assertThat(tx1.debit).isEqualTo(10)
      assertThat(tx1.location).isEqualTo("LEI")
      assertThat(tx1.accountType).isEqualTo("CASH")

      generalLedgerApi.verify(
        1,
        getRequestedFor(urlPathMatching("/accounts/$accountId/transactions")),
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

      val accountRef = "AE123456"

      generalLedgerApi.stubGetAccountListWithAccount(accountRef = accountRef, returnAccountId = accountId)

      val request = serviceTestHelpers.createTransactionListResponse(
        timestamp = Instant.now(),
        description = "CASH_TO_SAVINGS",
        postings = listOf(
          serviceTestHelpers.createPosting(
            amount = 10L,
            postingType = PrisonerPostingListResponse.Type.DR,
            subAccountRef = "CASH",
            reference = "AB123F33",
            accountType = ParentAccountListResponse.Type.PRISONER,
          ),
          serviceTestHelpers.createPosting(
            amount = 10L,
            postingType = PrisonerPostingListResponse.Type.CR,
            subAccountRef = "SAVINGS",
            reference = "AB123F33",
            accountType = ParentAccountListResponse.Type.PRISONER,
          ),
        ),
      )

      generalLedgerApi.stubGetTransactionList(accountId, listOf(request))

      val responseBody = webTestClient.get()
        .uri("/prisoners/$accountRef/money/transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__PROFILE__RO)))
        .exchange()
        .expectStatus().isOk
        .expectBody<List<PrisonerTransactionResponse>>()
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

      generalLedgerApi.verify(
        1,
        getRequestedFor(urlPathEqualTo("/accounts"))
          .withQueryParam("reference", matching(accountRef)),
      )
    }

    @Test
    fun `return a list of multiple transactions when sent a valid account reference`() {
      val accountId = UUID.randomUUID()
      val accountRef = "AE123456"

      generalLedgerApi.stubGetAccountListWithAccount(accountRef = accountRef, returnAccountId = accountId)

      val requestOne = serviceTestHelpers.createTransactionListResponse(
        timestamp = Instant.now(),
        description = "CASH_TO_CANTEEN",
        postings = listOf(
          serviceTestHelpers.createPosting(
            amount = 10L,
            postingType = PrisonerPostingListResponse.Type.DR,
            subAccountRef = "CASH",
            reference = "AB123F33",
            accountType = ParentAccountListResponse.Type.PRISONER,
          ),
          serviceTestHelpers.createPosting(
            amount = 10L,
            postingType = PrisonerPostingListResponse.Type.CR,
            subAccountRef = "1001:CANT",
            reference = "LEI",
            accountType = ParentAccountListResponse.Type.PRISON,
          ),
        ),
      )

      val requestTwo = serviceTestHelpers.createTransactionListResponse(
        timestamp = Instant.now(),
        description = "CASH_TO_SAVINGS",
        postings = listOf(
          serviceTestHelpers.createPosting(
            amount = 10L,
            postingType = PrisonerPostingListResponse.Type.DR,
            subAccountRef = "CASH",
            reference = "AB123F33",
            accountType = ParentAccountListResponse.Type.PRISONER,
          ),
          serviceTestHelpers.createPosting(
            amount = 10L,
            postingType = PrisonerPostingListResponse.Type.CR,
            subAccountRef = "SAVINGS",
            reference = "AB123F33",
            accountType = ParentAccountListResponse.Type.PRISONER,
          ),
        ),
      )

      // Transactions ordered by timestamp descending
      generalLedgerApi.stubGetTransactionList(accountId, listOf(requestTwo, requestOne))

      val responseBody = webTestClient.get()
        .uri("/prisoners/$accountRef/money/transactions")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__PROFILE__RO)))
        .exchange()
        .expectStatus().isOk
        .expectBody<List<PrisonerTransactionResponse>>().returnResult().responseBody!!

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

      generalLedgerApi.verify(
        1,
        getRequestedFor(urlPathEqualTo("/accounts"))
          .withQueryParam("reference", matching(accountRef)),
      )
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
