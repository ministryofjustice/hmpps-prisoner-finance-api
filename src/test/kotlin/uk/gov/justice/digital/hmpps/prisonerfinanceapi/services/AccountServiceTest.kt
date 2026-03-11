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
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class AccountServiceTest {

  @Mock private lateinit var generalLedgerApiClient: GeneralLedgerApiClient

  @InjectMocks private lateinit var accountService: AccountService

  @Nested
  inner class GetAccountBalance {

    @Test
    fun `Should call general ledger api`() {
    }
  }

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
}
