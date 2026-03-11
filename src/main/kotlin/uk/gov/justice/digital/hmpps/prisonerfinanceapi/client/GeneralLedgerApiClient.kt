package uk.gov.justice.digital.hmpps.prisonerfinanceapi.client

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.CustomException
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.clients.generalledger.AccountControllerApi
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.clients.generalledger.SubAccountControllerApi
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.clients.generalledger.TransactionControllerApi
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.AccountBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.AccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.PrisonerTransactionListResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.SubAccountBalanceResponse
import java.util.UUID

@Component
class GeneralLedgerApiClient(
  private val transactionApi: TransactionControllerApi,
  private val accountApi: AccountControllerApi,
  private val subAccountApi: SubAccountControllerApi,
) {

  private fun <T> handleExceptions(
    block: () -> T,
    message404: String = "Not found",
    message502: String = "Bad Gateway - General Ledger Unreachable or throwing an error",
    message500: String = "Unexpected Error",
  ): T {
    try {
      return block()
    } catch (e: WebClientResponseException) {
      if (e.statusCode == HttpStatus.NOT_FOUND) {
        throw CustomException(message404, HttpStatus.NOT_FOUND, e)
      } else if (e.statusCode == HttpStatus.INTERNAL_SERVER_ERROR) {
        throw CustomException(message502, HttpStatus.BAD_GATEWAY, e)
      } else {
        throw CustomException(message500, HttpStatus.INTERNAL_SERVER_ERROR, e)
      }
    }
  }

  fun getAccountBalance(accountUUID: UUID): AccountBalanceResponse = handleExceptions(
    {
      accountApi.getAccountBalance(accountUUID).block()
        ?: throw IllegalStateException("Received null response when retrieving balance by accountId: $accountUUID")
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

  fun getListOfTransactionsByAccountId(accountId: UUID): List<PrisonerTransactionListResponse> = handleExceptions(
    {
      transactionApi.getListOfTransactionsByAccountId(accountId).block()
        ?: throw IllegalStateException("Received null response when retrieving a list of transactions for account $accountId")
    },
  )
}
