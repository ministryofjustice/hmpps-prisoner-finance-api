package uk.gov.justice.digital.hmpps.prisonerfinanceapi.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class GeneralLedgerIdempotencyServiceTest {

  private val service = GeneralLedgerIdempotencyService()
  val subAccountCashId = UUID.randomUUID()
  val subAccountSpendsId = UUID.randomUUID()
  val messageId = UUID.randomUUID()

  @Test
  fun `should generate deterministic UUID for same inputs`() {
    val first = service.genTransactionIdempotencyKey(messageId, subAccountCashId)
    val second = service.genTransactionIdempotencyKey(messageId, subAccountCashId)

    assertEquals(first, second)
  }

  @Test
  fun `should generate different UUIDs for different transaction ids`() {
    val first = service.genTransactionIdempotencyKey(UUID.randomUUID(), subAccountCashId)
    val second = service.genTransactionIdempotencyKey(UUID.randomUUID(), subAccountCashId)

    assertNotEquals(first, second)
  }

  @Test
  fun `should generate different UUIDs for different subaccount refs`() {
    val first = service.genTransactionIdempotencyKey(messageId, subAccountSpendsId)
    val second = service.genTransactionIdempotencyKey(messageId, subAccountCashId)

    assertNotEquals(first, second)
  }

  @Test
  fun `should generate valid RFC 4122 UUIDs`() {
    val uuid = service.genTransactionIdempotencyKey(UUID.randomUUID(), subAccountCashId)

    // RFC 4122 requires the Variant to be 2 (IETF Leach-Salz)
    // The PR implementation fails this (it will likely be random or 0)
    assertEquals(2, uuid.variant(), "UUID Variant must be 2 (IETF RFC 4122)")

    // RFC 4122 requires the Version to be 1-5
    // The PR implementation fails this (it will be a random number based on the hash)
    val validVersions = setOf(1, 2, 3, 4, 5)
    if (uuid.version() !in validVersions) {
      throw AssertionError("UUID Version ${uuid.version()} is invalid. Must be one of $validVersions")
    }
  }
}
