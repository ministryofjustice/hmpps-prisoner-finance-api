package uk.gov.justice.digital.hmpps.prisonerfinanceapi.services

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.client.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.ParentAccountListResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.PrisonerPostingListResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.PrisonerTransactionListResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.response.PrisonerTransactionResponse
import java.util.UUID

@Service
class TransactionService(@Autowired private val generalLedgerApiClient: GeneralLedgerApiClient) {

  private fun transformPrisonToPrisonerPosting(
    transaction: PrisonerTransactionListResponse,
  ): List<PrisonerTransactionResponse> {
    val prisonerPosting = transaction.postings.first { it.subAccount.parentAccount.type == ParentAccountListResponse.Type.PRISONER }
    val isPrisonerDebit = prisonerPosting.type == PrisonerPostingListResponse.Type.DR
    val prisonPosting = transaction.postings.first { it.subAccount.parentAccount.type == ParentAccountListResponse.Type.PRISON }

    return listOf(
      PrisonerTransactionResponse(
        date = transaction.timestamp,
        description = transaction.description,
        debit = if (isPrisonerDebit) prisonerPosting.amount else 0,
        credit = if (!isPrisonerDebit) prisonerPosting.amount else 0,
        location = prisonPosting.subAccount.parentAccount.reference,
        accountType = prisonerPosting.subAccount.subAccountReference,
      ),
    )
  }

  private fun transformPrisonerToPrisonerPosting(
    transaction: PrisonerTransactionListResponse,
  ): List<PrisonerTransactionResponse> = transaction.postings.map { posting ->
    PrisonerTransactionResponse(
      date = transaction.timestamp,
      description = transaction.description,
      credit = if (posting.type == PrisonerPostingListResponse.Type.CR) posting.amount else 0,
      debit = if (posting.type == PrisonerPostingListResponse.Type.DR) posting.amount else 0,
      location = "",
      accountType = posting.subAccount.subAccountReference,
    )
  }

  private fun isPrisonerToPrisonerPosting(
    transaction: PrisonerTransactionListResponse,
  ): Boolean = transaction.postings
    .all { posting -> posting.subAccount.parentAccount.type == ParentAccountListResponse.Type.PRISONER }

  fun getPrisonerTransactionsByAccountId(accountId: UUID): List<PrisonerTransactionResponse> {
    val response = generalLedgerApiClient.getListOfTransactionsByAccountId(accountId)

    return response.flatMap {
      if (isPrisonerToPrisonerPosting(it)) {
        return@flatMap transformPrisonerToPrisonerPosting(it)
      } else {
        return@flatMap transformPrisonToPrisonerPosting(it)
      }
    }
  }
}
