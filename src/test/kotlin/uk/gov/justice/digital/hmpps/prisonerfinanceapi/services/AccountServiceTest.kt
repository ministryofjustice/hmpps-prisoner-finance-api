package uk.gov.justice.digital.hmpps.prisonerfinanceapi.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.client.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.AccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.SubAccountResponse
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class AccountServiceTest {

  @Mock private lateinit var generalLedgerApiClient: GeneralLedgerApiClient

  @Mock private lateinit var generalLedgerAccountResolver: GeneralLedgerAccountResolver

  @InjectMocks private lateinit var accountService: AccountService

  @Nested
  inner class GetAccountByRef {
    @Test
    fun `If account has no reference should return exception error`() {
      val prisonNumber = "A1234AA"

      whenever(generalLedgerApiClient.getAccountByRef(prisonNumber)).thenReturn(listOf())

      val account = accountService.getAccountByReference(prisonNumber)

      assertThat(account).isEqualTo(null)
    }

    @Test
    fun `If account has referenced prisoner should return valid account response`() {
      val prisonNumber = "A1234AA"
      val accountUUID = UUID.randomUUID()
      val account = AccountResponse(id = accountUUID, reference = prisonNumber, type = AccountResponse.Type.PRISONER, createdAt = Instant.now(), createdBy = "", subAccounts = emptyList())

      whenever(generalLedgerApiClient.getAccountByRef(prisonNumber)).thenReturn(listOf(account))

      val result = accountService.getAccountByReference(prisonNumber)

      assertThat(result).isEqualTo(account)
    }
  }

  @Nested
  inner class VerifyOrRepairAccount {

    @Test
    fun `If an account has all subaccounts return the account`() {
      val prisonNumber = "A1234AA"
      val accountUUID = UUID.randomUUID()

      val subAccounts = listOf(
        SubAccountResponse(
          id = UUID.randomUUID(),
          reference = "CASH",
          parentAccountId = accountUUID,
          createdBy = "TEST_USER",
          createdAt = Instant.now(),
        ),
        SubAccountResponse(
          id = UUID.randomUUID(),
          reference = "SPENDS",
          parentAccountId = accountUUID,
          createdBy = "TEST_USER",
          createdAt = Instant.now(),
        ),
        SubAccountResponse(
          id = UUID.randomUUID(),
          reference = "SAVINGS",
          parentAccountId = accountUUID,
          createdBy = "TEST_USER",
          createdAt = Instant.now(),
        ),
      )

      val account = AccountResponse(id = accountUUID, reference = prisonNumber, type = AccountResponse.Type.PRISONER, createdAt = Instant.now(), createdBy = "", subAccounts = subAccounts)

      whenever(generalLedgerAccountResolver.getOrCreateParentAccount(prisonNumber)).thenReturn(account)
      val result = accountService.verifyOrRepairAccount(prisonNumber)

      assertThat(result).isEqualTo(account)
    }

    @Test
    fun `If an account does not exist, then create the account with all subaccounts and return it`() {
      val prisonNumber = "A1234AA"
      val accountUUID = UUID.randomUUID()

      val subAccounts = listOf(
        SubAccountResponse(
          id = UUID.randomUUID(),
          reference = "CASH",
          parentAccountId = accountUUID,
          createdBy = "TEST_USER",
          createdAt = Instant.now(),
        ),
        SubAccountResponse(
          id = UUID.randomUUID(),
          reference = "SAVINGS",
          parentAccountId = accountUUID,
          createdBy = "TEST_USER",
          createdAt = Instant.now(),
        ),
        SubAccountResponse(
          id = UUID.randomUUID(),
          reference = "SPENDS",
          parentAccountId = accountUUID,
          createdBy = "TEST_USER",
          createdAt = Instant.now(),
        ),
      )

      val accountWithSubAccounts = AccountResponse(id = accountUUID, reference = prisonNumber, type = AccountResponse.Type.PRISONER, createdAt = Instant.now(), createdBy = "", subAccounts = subAccounts)
      val accountWithoutSubAccounts = AccountResponse(id = accountUUID, reference = prisonNumber, type = AccountResponse.Type.PRISONER, createdAt = Instant.now(), createdBy = "", subAccounts = emptyList())

      whenever(generalLedgerAccountResolver.getOrCreateParentAccount(prisonNumber)).thenReturn(accountWithoutSubAccounts, accountWithSubAccounts)

      val result = accountService.verifyOrRepairAccount(prisonNumber)
      assertThat(result).isEqualTo(accountWithSubAccounts)
    }
  }
}
