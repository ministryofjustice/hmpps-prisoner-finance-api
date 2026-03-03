package uk.gov.justice.digital.hmpps.prisonerfinanceapi.services

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.client.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.ParentAccountListResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.PrisonerPostingListResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.PrisonerTransactionListResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.response.PrisonerFinanceTransactionResponse
import java.util.UUID

@Service
class TransactionService(@Autowired private val generalLedgerApiClient: GeneralLedgerApiClient) {

  private fun transformPrisonToPrisonerPosting(
    transaction: PrisonerTransactionListResponse,
  ): List<PrisonerFinanceTransactionResponse> = listOf(
    PrisonerFinanceTransactionResponse(
      transaction.timestamp,
      transaction.description,
      transaction.postings.first { postings -> postings.type == PrisonerPostingListResponse.Type.CR }.amount,
      transaction.postings.first { postings -> postings.type == PrisonerPostingListResponse.Type.DR }.amount,
      transaction.postings.map { posting -> posting.subAccount.parentAccount }
        .first { parentAccount -> parentAccount.type == ParentAccountListResponse.Type.PRISON }.reference,
      transaction.postings.map { posting -> posting.subAccount }
        .first { subAccount -> subAccount.parentAccount.type == ParentAccountListResponse.Type.PRISONER }.subAccountReference,
    ),
  )

  private fun transformPrisonerToPrisonerPosting(
    transaction: PrisonerTransactionListResponse,
  ): List<PrisonerFinanceTransactionResponse> = transaction.postings.map { posting ->
    PrisonerFinanceTransactionResponse(
      transaction.timestamp,
      transaction.description,
      transaction.postings.first { postings -> postings.type == PrisonerPostingListResponse.Type.CR }.amount,
      transaction.postings.first { postings -> postings.type == PrisonerPostingListResponse.Type.DR }.amount,
      "",
      posting.subAccount.subAccountReference,
    )
  }

  private fun isPrisonerToPrisonerPosting(
    transaction: PrisonerTransactionListResponse,
  ): Boolean = transaction.postings
    .all { posting -> posting.subAccount.parentAccount.type == ParentAccountListResponse.Type.PRISONER }

  fun getPrisonerTransactionsByAccountId(accountId: UUID): List<PrisonerFinanceTransactionResponse> {
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
