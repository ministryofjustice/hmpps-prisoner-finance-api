package uk.gov.justice.digital.hmpps.prisonerfinanceapi.services

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.client.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.exceptions.RetryAfterConflictException
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.AccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.CreateAccountRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.SubAccountResponse
import java.util.UUID

@Service
class GeneralLedgerAccountResolver(
  private val apiClient: GeneralLedgerApiClient,
) {

  private companion object {
    private val log = LoggerFactory.getLogger(GeneralLedgerAccountResolver::class.java)
  }

  fun getOrCreateParentAccount(reference: String): AccountResponse {
    val response = apiClient.getAccountByRef(reference).firstOrNull()
    if (response != null) {
      return response
    } else {
      log.info("General Ledger account not found for '$reference'. Creating new account.")

      try {
        return apiClient.createAccount(reference, CreateAccountRequest.Type.PRISONER)
      } catch (e: WebClientResponseException) {
        if (e.statusCode == HttpStatus.CONFLICT) {
          return apiClient.getAccountByRef(reference).firstOrNull()
            ?: throw RetryAfterConflictException("Account not found after server responded with 409 for reference: $reference")
        } else {
          throw e
        }
      }
    }
  }

  fun getOrCreateSubAccount(
    parent: AccountResponse,
    subRef: String,
  ): AccountResponse {
    parent.subAccounts
      .firstOrNull { it.reference == subRef }
      ?.let { return parent }

    val created = createSubAccount(parent.id, subRef, parent.reference)

    return parent.copy(
      subAccounts = parent.subAccounts + created,
    )
  }

  private fun createSubAccount(parentAccountId: UUID, reference: String, parentReference: String): SubAccountResponse {
    log.info("General Ledger sub-account not found for '$reference'. Creating new sub-account.")
    try {
      return apiClient.createSubAccount(parentAccountId, reference)
    } catch (e: WebClientResponseException) {
      if (e.statusCode == HttpStatus.CONFLICT) {
        return apiClient.findSubAccount(parentReference, reference)
          ?: throw RetryAfterConflictException("Sub account not found after server responded with 409 for reference: $reference")
      } else {
        throw e
      }
    }
  }
}
