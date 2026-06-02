package uk.gov.justice.digital.hmpps.prisonerfinanceapi.services

import org.springframework.stereotype.Service
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

    request.prisonNumbersPostings.forEach { posting ->
      getSubAccountByRefOrNull(posting.prisonNumber, accounts, posting.prisonerSubAccountRef)?.let { subAccount ->
        val createdPostingRequest = CreatePostingRequest(
          subAccountId = subAccount.id,
          type = posting.postingType,
          amount = posting.amount,
          entrySequence = postingEntrySequenceCounter,
        )
        postingEntrySequenceCounter += 1
        postings.add(createdPostingRequest)
      }
    }

    postings.add(
      CreatePostingRequest(
        subAccountId = getSubAccountByRefOrNull(request.caseloadId, accounts, request.caseloadSubAccountRef)!!.id,
        type = request.postingType,
        amount = request.controlAmount, // todo update control amount when some prisoners are skipped
        entrySequence = if (prisonPostingIsDebit) 1L else postingEntrySequenceCounter,
      ),
    )

    return CreateTransactionRequest(
      reference = request.description,
      description = request.description,
      timestamp = Instant.now(),
      amount = request.controlAmount,
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
