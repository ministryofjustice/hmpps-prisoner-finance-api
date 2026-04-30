package uk.gov.justice.digital.hmpps.prisonerfinanceapi.services

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.client.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.AccountBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.AccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.SubAccountBalanceResponse
import java.util.UUID

@Service
class AccountService(@Autowired private val generalLedgerApiClient: GeneralLedgerApiClient) {
  fun getAccountByReference(accountReference: String): AccountResponse? = generalLedgerApiClient.getAccountByRef(accountReference).firstOrNull()

  fun getAccountBalance(accountUUID: UUID): AccountBalanceResponse = generalLedgerApiClient.getAccountBalance(accountUUID)

  fun getSubAccountBalance(accountUUID: UUID): SubAccountBalanceResponse = generalLedgerApiClient.getSubAccountBalance(accountUUID)
}
