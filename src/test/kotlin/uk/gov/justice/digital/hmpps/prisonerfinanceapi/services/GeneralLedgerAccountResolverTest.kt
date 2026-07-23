package uk.gov.justice.digital.hmpps.prisonerfinanceapi.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.client.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.exceptions.RetryAfterConflictException
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.AccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.CreateAccountRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.SubAccountResponse
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class GeneralLedgerAccountResolverTest {

  @Mock
  private lateinit var apiClient: GeneralLedgerApiClient

  @InjectMocks
  private lateinit var accountResolver: GeneralLedgerAccountResolver

  private val parentAccountId = UUID.randomUUID()
  private val prisonNumber = "AB123XZ"
  private val subReference = "SAVINGS"

  private val subAccountResponse = SubAccountResponse(
    id = UUID.randomUUID(),
    reference = subReference,
    createdBy = "TEST",
    createdAt = Instant.now(),
    parentAccountId = parentAccountId,
  )
  private val accountResponse = AccountResponse(
    id = parentAccountId,
    reference = prisonNumber,
    subAccounts = emptyList(),
    createdBy = "TEST",
    createdAt = Instant.now(),
    type = AccountResponse.Type.PRISONER,
  )

  @Nested
  inner class GetOrCreateParentAccount {

    @Test
    fun `should return existing account when found by reference`() {
      whenever(apiClient.getAccountByRef(prisonNumber)).thenReturn(listOf(accountResponse))

      val result = accountResolver.getOrCreateParentAccount(prisonNumber)

      assertThat(result).isEqualTo(accountResponse)
      verify(apiClient, never()).createAccount(anyString(), any())
    }

    @Test
    fun `should create and return new account when not found`() {
      whenever(apiClient.getAccountByRef(prisonNumber)).thenReturn(emptyList())
      whenever(apiClient.createAccount(prisonNumber, CreateAccountRequest.Type.PRISONER)).thenReturn(accountResponse)

      val result = accountResolver.getOrCreateParentAccount(prisonNumber)

      assertThat(result).isEqualTo(accountResponse)
      verify(apiClient).createAccount(prisonNumber, CreateAccountRequest.Type.PRISONER)
    }

    @Test
    fun `should fetch account if creation throws 409 CONFLICT (race condition)`() {
      val conflictException = mock(WebClientResponseException::class.java)
      whenever(conflictException.statusCode).thenReturn(HttpStatus.CONFLICT)

      // First call returns empty, second call (after conflict) returns the account
      whenever(apiClient.getAccountByRef(prisonNumber))
        .thenReturn(emptyList())
        .thenReturn(listOf(accountResponse))

      whenever(apiClient.createAccount(prisonNumber, CreateAccountRequest.Type.PRISONER))
        .thenThrow(conflictException)

      val result = accountResolver.getOrCreateParentAccount(prisonNumber)

      assertThat(result).isEqualTo(accountResponse)
      verify(apiClient, times(2)).getAccountByRef(prisonNumber)
    }

    @Test
    fun `should throw RetryAfterConflictException if creation throws 409 but account is still not found`() {
      val conflictException = mock(WebClientResponseException::class.java)
      whenever(conflictException.statusCode).thenReturn(HttpStatus.CONFLICT)

      whenever(apiClient.getAccountByRef(prisonNumber)).thenReturn(emptyList())
      whenever(apiClient.createAccount(prisonNumber, CreateAccountRequest.Type.PRISONER)).thenThrow(conflictException)

      val exception = assertThrows<RetryAfterConflictException> {
        accountResolver.getOrCreateParentAccount(prisonNumber)
      }

      assertThat(exception.message).contains("Account not found after server responded with 409 for reference: $prisonNumber")
    }

    @Test
    fun `should throw original exception if creation throws a non-409 WebClientResponseException`() {
      val serverErrorException = mock(WebClientResponseException::class.java)
      whenever(serverErrorException.statusCode).thenReturn(HttpStatus.INTERNAL_SERVER_ERROR)

      whenever(apiClient.getAccountByRef(prisonNumber)).thenReturn(emptyList())
      whenever(apiClient.createAccount(prisonNumber, CreateAccountRequest.Type.PRISONER)).thenThrow(serverErrorException)

      assertThrows<WebClientResponseException> {
        accountResolver.getOrCreateParentAccount(prisonNumber)
      }
    }
  }

  @Nested
  inner class GetOrCreateSubAccount {

    @Test
    fun `should return parent account unmodified if sub-account already exists in parent`() {
      val parentWithSub = accountResponse.copy(subAccounts = listOf(subAccountResponse))

      val result = accountResolver.getOrCreateSubAccount(parentWithSub, subReference)

      assertThat(result).isEqualTo(parentWithSub)
      verify(apiClient, never()).createSubAccount(any(), anyString())
    }

    @Test
    fun `should create sub-account and return updated parent if not found`() {
      whenever(apiClient.createSubAccount(parentAccountId, subReference)).thenReturn(subAccountResponse)

      val result = accountResolver.getOrCreateSubAccount(accountResponse, subReference)

      assertThat(result.subAccounts).hasSize(1)
      assertThat(result.subAccounts.first()).isEqualTo(subAccountResponse)
      verify(apiClient).createSubAccount(parentAccountId, subReference)
    }

    @Test
    fun `should fetch sub-account and return updated parent if creation throws 409 CONFLICT`() {
      val conflictException = mock(WebClientResponseException::class.java)
      whenever(conflictException.statusCode).thenReturn(HttpStatus.CONFLICT)

      whenever(apiClient.createSubAccount(parentAccountId, subReference)).thenThrow(conflictException)
      whenever(apiClient.findSubAccount(prisonNumber, subReference)).thenReturn(subAccountResponse)

      val result = accountResolver.getOrCreateSubAccount(accountResponse, subReference)

      assertThat(result.subAccounts).hasSize(1)
      assertThat(result.subAccounts.first()).isEqualTo(subAccountResponse)
      verify(apiClient).findSubAccount(prisonNumber, subReference)
    }

    @Test
    fun `should throw RetryAfterConflictException if creation throws 409 but sub-account is still not found`() {
      val conflictException = mock(WebClientResponseException::class.java)
      whenever(conflictException.statusCode).thenReturn(HttpStatus.CONFLICT)

      whenever(apiClient.createSubAccount(parentAccountId, subReference)).thenThrow(conflictException)
      whenever(apiClient.findSubAccount(prisonNumber, subReference)).thenReturn(null)

      val exception = assertThrows<RetryAfterConflictException> {
        accountResolver.getOrCreateSubAccount(accountResponse, subReference)
      }

      assertThat(exception.message).contains("Sub account not found after server responded with 409 for reference: $subReference")
    }

    @Test
    fun `should throw original exception if sub-account creation throws a non-409 error`() {
      val badRequestException = mock(WebClientResponseException::class.java)
      whenever(badRequestException.statusCode).thenReturn(HttpStatus.BAD_REQUEST)

      whenever(apiClient.createSubAccount(parentAccountId, subReference)).thenThrow(badRequestException)

      assertThrows<WebClientResponseException> {
        accountResolver.getOrCreateSubAccount(accountResponse, subReference)
      }
    }
  }
}
