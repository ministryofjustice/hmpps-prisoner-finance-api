package uk.gov.justice.digital.hmpps.prisonerfinanceapi.client

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.clients.generalledger.TransactionControllerApi
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.PrisonerTransactionListResponse
import java.util.UUID

@Component
class GeneralLedgerApiClient(private val transactionApi: TransactionControllerApi) {

  fun getListOfTransactionsByAccountId(accountId: UUID): List<PrisonerTransactionListResponse> = transactionApi.getListOfTransactionsByAccountId(accountId).block()
    ?: throw IllegalStateException("Received null response when retrieving a list of transactions for account $accountId")

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
