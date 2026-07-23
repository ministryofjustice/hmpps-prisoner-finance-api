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
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.services.AccountService

@Service
class DomainEventSubscriber(
  @Autowired private val accountService: AccountService,
) {

  private val objectMapper = ObjectMapper()

  @SqsListener("domainevents", factory = "hmppsQueueContainerFactoryProxy")
  fun handleEvents(requestJson: String?) {
    try {
      val event = objectMapper.readValue(requestJson, Event::class.java)
      val domainEvent = objectMapper.readValue(event.message, HmppsDomainEvent::class.java)

      when (domainEvent.eventType) {
        PRISON_RECORD_CREATED -> {
          val personCreated = objectMapper.readValue(event.message, CprPersonCreated::class.java)
          log.info("Received CPR person created event: $personCreated")

          val prisonNumber = personCreated.personReference.identifiers?.firstOrNull { it.type == "prisonNumber" }?.value

          if (prisonNumber == null) {
            log.error("No prison number found in CPR person created event: $personCreated")
            throw IllegalStateException("No prison number found in CPR person created event: $personCreated")
          }
          accountService.createPrisonerSubAccounts(prisonNumber)
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

  companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    const val PRISON_RECORD_CREATED = "core-person-record.prison.record.created"
  }
}
