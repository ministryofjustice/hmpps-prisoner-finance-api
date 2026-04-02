package uk.gov.justice.digital.hmpps.prisonerfinanceapi.services

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.CustomException
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.client.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.StatementEntryAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.StatementEntryResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.response.PrisonerTransactionResponse
import java.time.LocalDate
import java.util.UUID

@Service
class TransactionService(@Autowired private val generalLedgerApiClient: GeneralLedgerApiClient) {

  fun getPrisonerTransactionsByAccountId(accountId: UUID, startDate: LocalDate?, endDate: LocalDate?): List<PrisonerTransactionResponse> = generalLedgerApiClient.getStatementForAccountId(accountId, startDate, endDate).content.map { statementEntryResponse ->
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
    val firstOppositePosting = statementEntryResponse.oppositePostings.firstOrNull()
      ?: throw CustomException(
        message = "Unexpected posting without an opposite posting",
        status = HttpStatus.INTERNAL_SERVER_ERROR,
      )
    val oppositePostingParent = firstOppositePosting.subAccount.parentAccount
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
