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

  fun transformForUI(transactionList: List<PrisonerTransactionListResponse>): List<PrisonerFinanceTransactionResponse> = transactionList.map {
    PrisonerFinanceTransactionResponse(
      it.timestamp,
      it.description,
      it.postings.first { postings -> postings.type == PrisonerPostingListResponse.Type.CR }.amount,
      it.postings.first { postings -> postings.type == PrisonerPostingListResponse.Type.DR }.amount,
      it.postings.map { posting -> posting.subAccount.parentAccount }
        .firstOrNull { parentAccount -> parentAccount.type == ParentAccountListResponse.Type.PRISON }?.reference ?: "",
      it.postings.map { posting -> posting.subAccount }
        .first { subAccount -> subAccount.parentAccount.type == ParentAccountListResponse.Type.PRISONER }.subAccountReference,
    )
  }

  fun getPrisonerTransactionsByAccountId(accountId: UUID): List<PrisonerFinanceTransactionResponse> {
    val response = generalLedgerApiClient.getListOfTransactionsByAccountId(accountId)
    return transformForUI(response)
  }
}
