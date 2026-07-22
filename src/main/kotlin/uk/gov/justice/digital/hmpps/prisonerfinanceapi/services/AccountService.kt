package uk.gov.justice.digital.hmpps.prisonerfinanceapi.services

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.client.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.AccountBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.AccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.SubAccountBalanceResponse
import java.util.UUID

@Service
class AccountService(
  @Autowired private val generalLedgerApiClient: GeneralLedgerApiClient,
  @Autowired private val generalLedgerAccountResolver: GeneralLedgerAccountResolver,
) {

  private enum class GeneralLedgerSubAccounts {
    CASH,
    SAVINGS,
    SPENDS,
  }

  fun getAccountByReference(accountReference: String): AccountResponse? = generalLedgerApiClient.getAccountByRef(accountReference).firstOrNull()

  fun getAccountBalance(accountUUID: UUID): AccountBalanceResponse = generalLedgerApiClient.getAccountBalance(accountUUID)

  fun getSubAccountBalance(accountUUID: UUID): SubAccountBalanceResponse = generalLedgerApiClient.getSubAccountBalance(accountUUID)

  fun createPrisonerSubAccounts(prisonNumber: String) {
    val parentAccount = generalLedgerAccountResolver.getOrCreateParentAccount(prisonNumber)

    val subAccountsByRef = parentAccount.subAccounts.associateBy { it.reference }
    for (subAccountReference in GeneralLedgerSubAccounts.entries.map { it.name }) {
      if (!subAccountsByRef.containsKey(subAccountReference)) {
        generalLedgerAccountResolver.getOrCreateSubAccount(parentAccount, subAccountReference)
      }
    }
  }
}
