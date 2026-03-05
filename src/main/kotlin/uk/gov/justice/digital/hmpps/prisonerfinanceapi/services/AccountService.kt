package uk.gov.justice.digital.hmpps.prisonerfinanceapi.services

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.client.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.AccountResponse

@Service
class AccountService(@Autowired private val generalLedgerApiClient: GeneralLedgerApiClient) {
  fun getAccountByReference(prisonerNumber: String): AccountResponse? {
    val accountList = generalLedgerApiClient.getAccountByRef(prisonerNumber)
    return if (accountList.isNotEmpty()) accountList[0] else null
  }
}
