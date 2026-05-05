package uk.gov.justice.digital.hmpps.prisonerfinanceapi.services

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.CustomException
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.client.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.CreatePostingRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.CreateTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.StatementEntryAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.StatementEntryResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.TransactionResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.request.CreateTransactionFormRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.response.PagedPrisonerTransactionResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.response.PrisonerTransactionResponse
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Service
class TransactionService(@Autowired private val generalLedgerApiClient: GeneralLedgerApiClient) {

  fun getPrisonerTransactionsByAccountId(
    accountId: UUID,
    startDate: LocalDate?,
    endDate: LocalDate?,
    credit: Boolean,
    debit: Boolean,
    pageNumber: Int,
    pageSize: Int,
    subAccountId: UUID?,
  ): PagedPrisonerTransactionResponse {
    val statementPage = generalLedgerApiClient.getStatementForAccountId(accountId = accountId, startDate = startDate, endDate = endDate, credit = credit, debit = debit, pageNumber = pageNumber, pageSize = pageSize, subAccountId = subAccountId)

    val transactions = statementPage.content.map { statementEntryResponse ->
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

    return PagedPrisonerTransactionResponse(
      content = transactions,
      totalPages = statementPage.totalPages,
      pageNumber = statementPage.pageNumber,
      pageSize = statementPage.pageSize,
      totalElements = statementPage.totalElements,
      isLastPage = statementPage.isLastPage,
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

  fun createTransaction(createTransactionFormRequest: CreateTransactionFormRequest): TransactionResponse? {
    val idempotencyKey = UUID.randomUUID()
    val txReference = UUID.randomUUID().toString()

    val postings = listOf(
      CreatePostingRequest(
        subAccountId = createTransactionFormRequest.debitSubAccountId,
        type = CreatePostingRequest.Type.DR,
        amount = createTransactionFormRequest.amount,
        entrySequence = 1,
      ),
      CreatePostingRequest(
        subAccountId = createTransactionFormRequest.creditSubAccountId,
        type = CreatePostingRequest.Type.CR,
        amount = createTransactionFormRequest.amount,
        entrySequence = 2,
      ),
    )

    val createTransactionRequest = CreateTransactionRequest(
      reference = txReference,
      description = createTransactionFormRequest.description,
      timestamp = Instant.now(),
      amount = createTransactionFormRequest.amount,
      postings = postings,
      entrySequence = 1,
    )

    val createdTransaction = generalLedgerApiClient.postTransaction(idempotencyKey, createTransactionRequest)

    return createdTransaction
  }
}
