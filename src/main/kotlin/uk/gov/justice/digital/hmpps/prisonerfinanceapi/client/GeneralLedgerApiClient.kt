package uk.gov.justice.digital.hmpps.prisonerfinanceapi.client

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.CustomException
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.clients.generalledger.AccountControllerApi
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.clients.generalledger.TransactionControllerApi
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.AccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.PrisonerTransactionListResponse
import java.util.UUID

@Component
class GeneralLedgerApiClient(private val transactionApi: TransactionControllerApi, private val accountApi: AccountControllerApi) {

  fun getAccountByRef(prisonerNumber: String): List<AccountResponse> {
    try {
      return accountApi.getAccounts(prisonerNumber).block()
        ?: throw IllegalStateException("Received null response when retrieving account by reference $prisonerNumber")
    } catch (e: WebClientResponseException) {
      if (e.statusCode == HttpStatus.INTERNAL_SERVER_ERROR) {
        throw CustomException("General Ledger Unavailable", HttpStatus.BAD_GATEWAY, e)
      } else {
        throw CustomException("Unexpected error retrieving transactions", HttpStatus.INTERNAL_SERVER_ERROR, e)
      }
    }
  }

  fun getListOfTransactionsByAccountId(accountId: UUID): List<PrisonerTransactionListResponse> {
    try {
      return transactionApi.getListOfTransactionsByAccountId(accountId).block()
        ?: throw IllegalStateException("Received null response when retrieving a list of transactions for account $accountId")
    } catch (e: WebClientResponseException) {
      if (e.statusCode == HttpStatus.NOT_FOUND) {
        throw CustomException("Account not found", HttpStatus.NOT_FOUND, e)
      } else if (e.statusCode == HttpStatus.INTERNAL_SERVER_ERROR) {
        throw CustomException("General Ledger Unavailable", HttpStatus.SERVICE_UNAVAILABLE, e)
      } else {
        throw CustomException("Unexpected error retrieving transactions", HttpStatus.INTERNAL_SERVER_ERROR, e)
      }
    }
  }
}
