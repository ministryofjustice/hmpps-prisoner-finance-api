package uk.gov.justice.digital.hmpps.prisonerfinanceapi.services.domainevents

import com.fasterxml.jackson.databind.ObjectMapper
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.health.GeneralLedgerApiHealthPing
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.domainevents.CprPersonCreated
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.domainevents.Event
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.domainevents.HmppsDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.domainevents.HmppsMergeEvent
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.CreatePostingRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.CreateTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.services.AccountService
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.services.TransactionService
import java.time.Instant
import java.util.UUID
import kotlin.math.abs

@Service
class DomainEventSubscriber(
  @Autowired private val accountService: AccountService,
) {

  @Autowired
  private lateinit var transactionService: TransactionService

  @Autowired
  private lateinit var generalLedgerApi: GeneralLedgerApiHealthPing
  private val objectMapper = ObjectMapper()

  @SqsListener("domainevents", factory = "hmppsQueueContainerFactoryProxy")
  fun handleEvents(requestJson: String?) {
    try {
      println("Received event: $requestJson")
      val event = objectMapper.readValue(requestJson, Event::class.java)
      val domainEvent = objectMapper.readValue(event.message, HmppsDomainEvent::class.java)

      when (domainEvent.eventType) {
        PRISONER_ACCOUNT_MERGED -> {
          mergeAPrisonerAccount(event)
        }
        PRISON_RECORD_CREATED -> {
          createAPrisonerAccount(event)
        }
        else -> {
          log.warn("Ignored unexpected event type: ${domainEvent.eventType}")
        }
      }
    } catch (e: Exception) {
      log.error("Failed to process domain event. Message will be retried. Payload: $requestJson", e)
      throw e
    }
  }

  private fun mergeAPrisonerAccount(event: Event) {
    val prisonerMerged = objectMapper.readValue(event.message, HmppsMergeEvent::class.java)

    log.info("Received prisoner merged event: $prisonerMerged")

    val accountToKeep = accountService.verifyOrRepairAccount(prisonerMerged.additionalInformation.nomsNumber)
    val accountToRemove = accountService.verifyOrRepairAccount(prisonerMerged.additionalInformation.removedNomsNumber)

    val accountTypes = listOf("CASH", "SAVINGS", "SPENDS")

    val accountIdsToRemoveAndToKeep: List<Pair<UUID, UUID>> = accountTypes.map { accountType ->
      val subAccountToRemove = accountToRemove.subAccounts.find { it.reference == accountType }!!.id
      val subAccountToKeep = accountToKeep.subAccounts.find { it.reference == accountType }!!.id
      Pair(subAccountToRemove, subAccountToKeep)
    }

    accountIdsToRemoveAndToKeep.forEach { (subAccountToRemove, subAccountToKeep) ->

      val subAccountFinalBalance = accountService.getSubAccountBalance(subAccountToRemove).amount

      if (subAccountFinalBalance != 0L) {
        val adjustmentDescription = "ADJ - MERGED FROM ${prisonerMerged.additionalInformation.removedNomsNumber} TO ${prisonerMerged.additionalInformation.nomsNumber}"

        val absBalance = abs(subAccountFinalBalance)

        val debitingAccount = if (subAccountFinalBalance > 0) subAccountToRemove else subAccountToKeep
        val creditingAccount = if (subAccountFinalBalance > 0) subAccountToKeep else subAccountToRemove

        val adjustmentTxn = CreateTransactionRequest(
          reference = "",
          description = adjustmentDescription,
          timestamp = Instant.now(),
          amount = absBalance,
          entrySequence = 1,
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

        transactionService.postTransaction(UUID.randomUUID(), adjustmentTxn)
      }
    }
  }

  private fun createAPrisonerAccount(event: Event) {
    val personCreated = objectMapper.readValue(event.message, CprPersonCreated::class.java)
    log.info("Received CPR person created event: $personCreated")

    val prisonNumber = personCreated.personReference.identifiers?.firstOrNull { it.type == "prisonNumber" }?.value

    if (prisonNumber == null) {
      log.error("No prison number found in CPR person created event: $personCreated")
      throw IllegalStateException("No prison number found in CPR person created event: $personCreated")
    }
    accountService.createPrisonerSubAccounts(prisonNumber)
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    // These must be configured in the application YAML subscribeFilter
    const val PRISON_RECORD_CREATED = "core-person-record.prison.record.created"
    const val PRISONER_ACCOUNT_MERGED = "prison-offender-events.prisoner.merged"
  }
}
