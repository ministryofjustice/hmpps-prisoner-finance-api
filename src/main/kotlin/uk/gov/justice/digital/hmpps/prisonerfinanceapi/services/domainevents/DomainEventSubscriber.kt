package uk.gov.justice.digital.hmpps.prisonerfinanceapi.services.domainevents

import com.fasterxml.jackson.databind.ObjectMapper
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.domainevents.CprPersonCreated
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.domainevents.Event
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.domainevents.HmppsDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.domainevents.HmppsMergeEvent
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.domainevents.OffenderInsertedEvent
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.services.AccountService
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.services.MergeService

@Service
class DomainEventSubscriber(
  @Autowired private val accountService: AccountService,
  @Autowired private val mergeService: MergeService,
  @Autowired private val objectMapper: ObjectMapper,
) {
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

        // at present, PRISON_RECORD_CREATED does not exist in prod
        // this can be removed when that changes
        OFFENDER_INSERTED -> {
          createAPrisonerAccountWithLegacyEvent(event)
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
    mergeService.mergeAPrisonerAccount(prisonerMerged, event.messageId, event.timestamp)
  }

  private fun createAPrisonerAccount(event: Event) {
    val personCreated = objectMapper.readValue(event.message, CprPersonCreated::class.java)
    log.info("Received CPR person created event: $personCreated")

    val prisonNumber = personCreated.personReference.identifiers?.firstOrNull { it.type == "prisonNumber" }?.value

    if (prisonNumber == null) {
      log.error("No prison number found in CPR person created event: $personCreated")
      throw IllegalStateException("No prison number found in CPR person created event: $personCreated")
    }
    accountService.getOrCreatePrisonerAccountStructure(prisonNumber)
  }

  private fun createAPrisonerAccountWithLegacyEvent(event: Event) {
    val offenderInserted = objectMapper.readValue(event.message, OffenderInsertedEvent::class.java)
    log.info("Received Offender Inserted event: $offenderInserted")

    val prisonNumber = offenderInserted.offenderIdDisplay

    accountService.getOrCreatePrisonerAccountStructure(prisonNumber)
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    // These must be configured in the application YAML subscribeFilter
    const val PRISON_RECORD_CREATED = "core-person-record.prison.record.created"
    const val PRISONER_ACCOUNT_MERGED = "prison-offender-events.prisoner.merged"
    const val OFFENDER_INSERTED = "OFFENDER-INSERTED"
  }
}
