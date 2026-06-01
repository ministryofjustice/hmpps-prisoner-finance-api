package uk.gov.justice.digital.hmpps.prisonerfinanceapi.integration

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.config.ROLE_PRISONER_FINANCE__PROFILE__RW
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.integration.wiremock.GeneralLedgerApiExtension
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.integration.wiremock.GeneralLedgerApiExtension.Companion.generalLedgerApi
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.integration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.AccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.CreatePostingRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.PostingResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.SubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.TransactionResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.request.CreateBatchTransactionFormRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.request.PrisonerPosting
import java.time.Instant
import java.util.UUID

@ExtendWith(HmppsAuthApiExtension::class, GeneralLedgerApiExtension::class)
class BatchTransactionIntegrationTest : IntegrationTestBase() {

  @Nested
  inner class CreateBatchTransaction {

    val prisonAccountId = UUID.randomUUID()

    val prisonAccount = AccountResponse(
      id = prisonAccountId,
      reference = "LEI",
      createdBy = "TEST",
      createdAt = Instant.now(),
      type = AccountResponse.Type.PRISON,
      subAccounts = listOf(
        SubAccountResponse(
          id = UUID.randomUUID(),
          reference = "CANT:1501",
          parentAccountId = prisonAccountId,
          createdBy = "TEST",
          createdAt = Instant.now(),
        ),
        SubAccountResponse(
          id = UUID.randomUUID(),
          reference = "CAT:1502",
          parentAccountId = prisonAccountId,
          createdBy = "TEST",
          createdAt = Instant.now(),
        ),
      ),
    )

    val prisonerId1 = UUID.randomUUID()

    val prisoner1Account = AccountResponse(
      id = prisonerId1,
      reference = "A213671C",
      createdBy = "TEST",
      createdAt = Instant.now(),
      type = AccountResponse.Type.PRISONER,
      subAccounts = listOf(
        SubAccountResponse(
          id = UUID.randomUUID(),
          reference = "CASH",
          parentAccountId = prisonerId1,
          createdBy = "TEST",
          createdAt = Instant.now(),
        ),
        SubAccountResponse(
          id = UUID.randomUUID(),
          reference = "SPENDS",
          parentAccountId = prisonerId1,
          createdBy = "TEST",
          createdAt = Instant.now(),
        ),
      ),
    )

    val prisonerId2 = UUID.randomUUID()

    val prisoner2Account = AccountResponse(
      id = prisonerId2,
      reference = "A666671D",
      createdBy = "TEST",
      createdAt = Instant.now(),
      type = AccountResponse.Type.PRISONER,
      subAccounts = listOf(
        SubAccountResponse(
          id = UUID.randomUUID(),
          reference = "CASH",
          parentAccountId = prisonerId2,
          createdBy = "TEST",
          createdAt = Instant.now(),
        ),
        SubAccountResponse(
          id = UUID.randomUUID(),
          reference = "SPENDS",
          parentAccountId = prisonerId2,
          createdBy = "TEST",
          createdAt = Instant.now(),
        ),
      ),
    )

    @Test
    fun `Should post a transaction where all the accounts exist within the general ledger`() {
      val generalLedgerAccounts: List<AccountResponse> = listOf(
        prisonAccount,
        prisoner1Account,
        prisoner2Account,
      )

      generalLedgerApi.stubSearchAccountsByReferences(accountsToReturn = generalLedgerAccounts)

      val grantBonusForm = CreateBatchTransactionFormRequest(
        caseloadId = prisonAccount.reference,
        caseloadSubAccountRef = prisonAccount.subAccounts.last().reference,
        postingType = CreatePostingRequest.Type.DR,
        controlAmount = 100L,
        description = "Grant a bonus",
        prisonNumbersPostings = listOf(
          PrisonerPosting(
            prisonNumber = prisoner1Account.reference,
            postingType = CreatePostingRequest.Type.CR,
            prisonerSubAccountRef = prisoner1Account.subAccounts.first().reference,
            amount = 50L,
          ),
          PrisonerPosting(
            prisonNumber = prisoner2Account.reference,
            postingType = CreatePostingRequest.Type.CR,
            prisonerSubAccountRef = prisoner2Account.subAccounts.last().reference,
            amount = 50L,
          ),
        ),
      )

      generalLedgerApi.stubPostTransaction(
        TransactionResponse(
          id = UUID.randomUUID(),
          createdBy = "TEST",
          createdAt = Instant.now(),
          reference = grantBonusForm.description,
          description = grantBonusForm.description,
          timestamp = Instant.now(),
          amount = 100L,
          postings = listOf(
            PostingResponse(
              id = UUID.randomUUID(),
              createdBy = "TEST",
              createdAt = Instant.now(),
              type = PostingResponse.Type.DR,
              amount = 100,
              subAccountID = prisonAccount.subAccounts.last().id,
            ),
            PostingResponse(
              id = UUID.randomUUID(),
              createdBy = "TEST",
              createdAt = Instant.now(),
              type = PostingResponse.Type.CR,
              amount = 50,
              subAccountID = prisoner1Account.subAccounts.first().id,
            ),
            PostingResponse(
              id = UUID.randomUUID(),
              createdBy = "TEST",
              createdAt = Instant.now(),
              type = PostingResponse.Type.CR,
              amount = 50,
              subAccountID = prisoner2Account.subAccounts.last().id,
            ),
          ),
        ),
      )

      webTestClient.post()
        .uri("/transactions/batch")
        .headers(setAuthorisation(roles = listOf(ROLE_PRISONER_FINANCE__PROFILE__RW)))
        .bodyValue(grantBonusForm)
        .exchange()
        .expectStatus().isCreated
        .expectBody<TransactionResponse>()
        .returnResult().responseBody!!
    }
  }
}
