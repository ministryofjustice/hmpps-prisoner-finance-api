package uk.gov.justice.digital.hmpps.prisonerfinanceapi.client

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.clients.generalledger.AccountControllerApi
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.clients.generalledger.StatementControllerApi
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.clients.generalledger.SubAccountControllerApi
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.clients.generalledger.TransactionControllerApi
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.exceptions.CustomException
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.AccountBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.AccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.CreateAccountRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.CreateSubAccountRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.CreateTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.PagedResponseStatementEntryResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.PrisonerTransactionListResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.SubAccountBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.SubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.TransactionResponse
import java.time.LocalDate
import java.util.UUID

@Component
class GeneralLedgerApiClient(
  private val transactionApi: TransactionControllerApi,
  private val accountApi: AccountControllerApi,
  private val subAccountApi: SubAccountControllerApi,
  private val statementControllerApi: StatementControllerApi,
) {

  private fun <T> handleExceptions(
    block: () -> T,
    message400: String = "Bad Request from General Ledger",
    message404: String = "Not found",
    message502: String = "Bad Gateway - General Ledger Unreachable or throwing an error",
    message500: String = "Unexpected Error",
  ): T {
    try {
      return block()
    } catch (e: WebClientResponseException) {
      when (e.statusCode) {
        HttpStatus.BAD_REQUEST -> throw CustomException(message400, HttpStatus.BAD_REQUEST, e)
        HttpStatus.NOT_FOUND -> throw CustomException(message404, HttpStatus.NOT_FOUND, e)
        HttpStatus.INTERNAL_SERVER_ERROR -> throw CustomException(message502, HttpStatus.BAD_GATEWAY, e)
        else -> throw CustomException(message500, HttpStatus.INTERNAL_SERVER_ERROR, e)
      }
    }
  }

  // GET /sub-accounts?reference={subRef}&accountReference={parentRef}
  fun findSubAccount(parentReference: String, subAccountReference: String): SubAccountResponse? = subAccountApi.findSubAccounts(subAccountReference, parentReference)
    .block()
    ?.firstOrNull()

  // POST /accounts
  fun createAccount(reference: String, type: CreateAccountRequest.Type): AccountResponse {
    log.info("Creating Account for ref: $reference")

    val request = CreateAccountRequest(accountReference = reference, type = type)

    return accountApi.createAccount(request)
      .block()
      ?: throw IllegalStateException("Received null response when creating account $reference")
  }

  fun getAccountBalance(accountUUID: UUID): AccountBalanceResponse = handleExceptions(
    {
      accountApi.getAccountBalance(accountUUID).block()
        ?: throw IllegalStateException("Received null response when retrieving balance by accountId: $accountUUID")
    },
  )

  fun searchAccountsByReferences(references: List<String>): List<AccountResponse> = handleExceptions(
    {
      accountApi.searchAccounts(references).block()
        ?: throw IllegalStateException("Received null response when searching for accounts by references: $references")
    },
  )

  fun getSubAccountBalance(accountUUID: UUID): SubAccountBalanceResponse = handleExceptions(
    {
      subAccountApi.getSubAccountBalance(accountUUID).block()
        ?: throw IllegalStateException("Received null response when retrieving sub account balance by accountId: $accountUUID")
    },
  )

  fun getAccountByRef(prisonerNumber: String): List<AccountResponse> = handleExceptions(
    {
      accountApi.getAccounts(prisonerNumber).block()
        ?: throw IllegalStateException("Received null response when retrieving account by reference $prisonerNumber")
    },
  )

  // POST /accounts/{parentId}/sub-accounts
  fun createSubAccount(parentId: UUID, subAccountReference: String): SubAccountResponse {
    log.info("Creating Sub-Account $subAccountReference for Parent UUID $parentId")

    val request = CreateSubAccountRequest(subAccountReference = subAccountReference)

    return subAccountApi.createSubAccount(parentId, request)
      .block()
      ?: throw IllegalStateException("Received null response when creating sub-account $subAccountReference")
  }

  fun getListOfTransactionsByAccountId(accountId: UUID): List<PrisonerTransactionListResponse> = handleExceptions(
    {
      transactionApi.getListOfTransactionsByAccountId(accountId).block()
        ?: throw IllegalStateException("Received null response when retrieving a list of transactions for account $accountId")
    },
  )

  fun getStatementForAccountId(accountId: UUID, startDate: LocalDate?, endDate: LocalDate?, credit: Boolean, debit: Boolean, pageNumber: Int = 1, pageSize: Int = 25, subAccountId: UUID?): PagedResponseStatementEntryResponse = handleExceptions(
    {
      try {
        statementControllerApi.getStatementForAccountId(
          accountId,
          startDate,
          endDate,
          pageNumber,
          pageSize,
          credit = credit,
          debit = debit,
          subAccountId = subAccountId,
        ).block()
          ?: throw IllegalStateException("Received null response when retrieving a list of statements for account $accountId")
      } catch (e: WebClientResponseException) {
        if (e.statusCode == HttpStatus.BAD_REQUEST && e.responseBodyAsString.contains("Page requested is out of range")) {
          throw CustomException("Page requested is out of range", HttpStatus.BAD_REQUEST, e)
        } else {
          throw e
        }
      }
    },
  )

  fun postTransaction(idempotencyKey: UUID, createTransactionRequest: CreateTransactionRequest): TransactionResponse = handleExceptions(
    {
      try {
        transactionApi.postTransaction(idempotencyKey, createTransactionRequest).block()
          ?: throw IllegalStateException("Received null response when posting a transaction")
      } catch (e: WebClientResponseException) {
        throw e
      }
    },
  )

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
