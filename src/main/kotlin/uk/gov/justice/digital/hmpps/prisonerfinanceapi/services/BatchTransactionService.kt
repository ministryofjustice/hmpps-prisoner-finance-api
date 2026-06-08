package uk.gov.justice.digital.hmpps.prisonerfinanceapi.services

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.CustomException
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.client.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.AccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.CreatePostingRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.CreateTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.SubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.TransactionResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.request.CreateBatchTransactionFormRequest
import java.time.Instant
import java.util.UUID

@Service
class BatchTransactionService(
  private val generalLedgerApiClient: GeneralLedgerApiClient,
) {

  private fun getSubAccountByRefOrNull(accountRef: String, accounts: List<AccountResponse>, subAccountRef: String): SubAccountResponse? {
    val account = accounts.firstOrNull { it.reference == accountRef } ?: return null
    return account.subAccounts.find { subAccount -> subAccount.reference == subAccountRef }
  }

  private fun buildCreateTransactionRequest(
    request: CreateBatchTransactionFormRequest,
    accounts: List<AccountResponse>,
  ): CreateTransactionRequest {
    val postings = mutableListOf<CreatePostingRequest>()

    val prisonPostingIsDebit = (request.postingType == CreatePostingRequest.Type.DR)

    var postingEntrySequenceCounter = if (prisonPostingIsDebit) 2L else 1L

    var transactionAmount = request.controlAmount

    val prisonSubAccount = getSubAccountByRefOrNull(request.caseloadId, accounts, request.caseloadSubAccountRef)

    if (prisonSubAccount == null) {
      throw CustomException(
        message = "Prison sub account not found for caseload ${request.caseloadId} and sub account ${request.caseloadSubAccountRef}",
        status = HttpStatus.NOT_FOUND,
      )
    }

    val prisonNumberToSubAccounts = request.prisonNumbersPostings.associate {
      it.prisonNumber to getSubAccountByRefOrNull(it.prisonNumber, accounts, it.prisonerSubAccountRef)
    }

    // builds prisoners' postings
    request.prisonNumbersPostings.forEach { posting ->
      val prisonerSubAccount = prisonNumberToSubAccounts.getValue(posting.prisonNumber)
      if (prisonerSubAccount == null) {
        transactionAmount -= posting.amount
      } else {
        val createdPostingRequest = CreatePostingRequest(
          subAccountId = prisonerSubAccount.id,
          type = posting.postingType,
          amount = posting.amount,
          entrySequence = postingEntrySequenceCounter,
        )
        postingEntrySequenceCounter += 1
        postings.add(createdPostingRequest)
      }
    }

    if (postings.isEmpty()) {
      throw CustomException(
        message = "Cannot create a transaction, no prisoner subAccounts found",
        status = HttpStatus.NOT_FOUND,
      )
    }

    // builds prison posting
    postings.add(
      CreatePostingRequest(
        subAccountId = prisonSubAccount.id,
        type = request.postingType,
        amount = transactionAmount,
        entrySequence = if (prisonPostingIsDebit) 1L else postingEntrySequenceCounter,
      ),
    )

    return CreateTransactionRequest(
      reference = request.description,
      description = request.description,
      timestamp = Instant.now(),
      amount = transactionAmount,
      entrySequence = 1,
      postings = postings,
    )
  }

  fun createBatchTransaction(request: CreateBatchTransactionFormRequest): TransactionResponse {
    val references = request.prisonNumbersPostings.map { it.prisonNumber }.toMutableList()
    references.add(request.caseloadId)

    val accounts = generalLedgerApiClient.searchAccountsByReferences(references)

    val transactionRequest = buildCreateTransactionRequest(request = request, accounts = accounts)

    return generalLedgerApiClient.postTransaction(idempotencyKey = UUID.randomUUID(), createTransactionRequest = transactionRequest)
  }
}
