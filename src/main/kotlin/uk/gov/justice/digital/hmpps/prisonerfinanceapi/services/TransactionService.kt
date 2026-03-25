package uk.gov.justice.digital.hmpps.prisonerfinanceapi.services

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.client.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.StatementEntryAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.StatementEntryResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.response.PrisonerTransactionResponse
import java.util.UUID

@Service
class TransactionService(@Autowired private val generalLedgerApiClient: GeneralLedgerApiClient) {

  fun getPrisonerTransactionsByAccountId(accountId: UUID): List<PrisonerTransactionResponse> = generalLedgerApiClient.getStatementForAccountId(accountId).map { statementEntryResponse ->
    val (credit, debit) = getCreditAndDebit(statementEntryResponse)
    return@map PrisonerTransactionResponse(
      date = statementEntryResponse.transactionTimestamp,
      description = statementEntryResponse.description,
      credit = credit,
      debit = debit,
      location = getPrisonLocation(statementEntryResponse),
      accountType = statementEntryResponse.subAccount.reference,
    )
  }

  private fun getPrisonLocation(statementEntryResponse: StatementEntryResponse): String {
    val oppositePostingParent = statementEntryResponse.oppositePostings.first().subAccount.parentAccount
    if (oppositePostingParent.type == StatementEntryAccountResponse.Type.PRISON) {
      return oppositePostingParent.reference
    } else {
      return ""
    }
  }

  private fun getCreditAndDebit(statementEntryResponse: StatementEntryResponse): Pair<Long, Long> {
    if (statementEntryResponse.postingType == StatementEntryResponse.PostingType.CR) {
      return Pair(statementEntryResponse.amount, 0)
    } else {
      return Pair(0, statementEntryResponse.amount)
    }
  }
}
