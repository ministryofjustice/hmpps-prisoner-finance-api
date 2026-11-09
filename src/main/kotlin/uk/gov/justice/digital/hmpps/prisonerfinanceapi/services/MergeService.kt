package uk.gov.justice.digital.hmpps.prisonerfinanceapi.services

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.jpa.entities.MergeEventAudit
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.jpa.repositories.MergeEventAuditRepository
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.domainevents.HmppsMergeEvent
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.CreatePostingRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.CreateTransactionRequest
import java.time.Instant
import java.util.UUID
import kotlin.math.abs

@Service
class MergeService(
  @Autowired private val accountService: AccountService,
  @Autowired private val transactionService: TransactionService,
  @Autowired private val idempotencyKeyService: GeneralLedgerIdempotencyService,
  @Autowired private val mergeEventAuditRepository: MergeEventAuditRepository,
) {
  private val accountTypes = listOf("CASH", "SAVINGS", "SPENDS")

  fun mergeAPrisonerAccount(prisonerMergeEvent: HmppsMergeEvent, messageId: UUID, timestamp: Instant) {
    val keepNomsNumber = prisonerMergeEvent.additionalInformation.nomsNumber
    val removedNomsNumber = prisonerMergeEvent.additionalInformation.removedNomsNumber

    val accountToKeep = accountService.getOrCreatePrisonerAccountStructure(keepNomsNumber)
    val accountToRemove = accountService.getOrCreatePrisonerAccountStructure(removedNomsNumber)

    val accountIdsToRemoveAndToKeep: List<Pair<UUID, UUID>> = accountTypes.map { accountType ->
      val subAccountToRemove = accountToRemove.subAccounts.find { it.reference == accountType }!!.id
      val subAccountToKeep = accountToKeep.subAccounts.find { it.reference == accountType }!!.id
      Pair(subAccountToRemove, subAccountToKeep)
    }

    accountIdsToRemoveAndToKeep.forEach { (subAccountToRemove, subAccountToKeep) ->

      val subAccountFinalBalance = accountService.getSubAccountBalance(subAccountToRemove).amount

      if (subAccountFinalBalance != 0L) {
        val adjustmentDescription = "ADJ - MERGED FROM $removedNomsNumber TO $keepNomsNumber"

        val absBalance = abs(subAccountFinalBalance)

        val debitingAccount = if (subAccountFinalBalance > 0) subAccountToRemove else subAccountToKeep
        val creditingAccount = if (subAccountFinalBalance > 0) subAccountToKeep else subAccountToRemove

        val adjustmentTxn = CreateTransactionRequest(
          description = adjustmentDescription,
          timestamp = timestamp,
          amount = absBalance,
          entrySequence = 1,
          reference = messageId.toString(),
          postings = listOf(
            CreatePostingRequest(
              subAccountId = debitingAccount,
              type = CreatePostingRequest.Type.DR,
              amount = absBalance,
              entrySequence = 1,
            ),
            CreatePostingRequest(
              subAccountId = creditingAccount,
              type = CreatePostingRequest.Type.CR,
              amount = absBalance,
              entrySequence = 2,
            ),
          ),
          legacyTransactionId = null,
        )

        val idempotencyKey = idempotencyKeyService.genTransactionIdempotencyKey(messageId, subAccountToKeep)
        transactionService.postTransaction(idempotencyKey, adjustmentTxn)
      }
    }

    mergeEventAuditRepository.save(
      MergeEventAudit(
        survivingPrisonNumber = keepNomsNumber,
        mergedPrisonNumber = removedNomsNumber,
        messageReceivedAt = timestamp,
        messageId = messageId,
      ),
    )
  }
}
