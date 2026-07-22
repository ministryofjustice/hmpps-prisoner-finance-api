package uk.gov.justice.digital.hmpps.prisonerfinanceapi.services

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.services.domainevents.DomainEventSubscriber
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.services.helpers.mockLogger

fun makePersonCreatedEvent(): String {
  val eventType = "core-person-record.prison.record.created"
  val payload =
    """{"eventType": "$eventType",
     "version":  1,
     "occurredAt": "Test",
     "description": "Test",
     "detailUrl": "/test",
     "personReference": {
        "identifiers": [
          {
            "type": "prisonNumber",
            "value": "Test"
          }
        ]
      }
  }"""
      .replace("\"", "\\\"").replace(Regex("\\s+"), " ")

  return """
    {
        "Type": "Notification",
        "MessageId": "5b90ee7d-67bc-5959-a4d8-b7d420180853",
        "Message":"$payload",
        "Timestamp": "2021-09-01T09:18:28.725Z",
        "MessageAttributes": {
            "eventType": {
                "Type": "String",
                "Value": "$eventType"
            }
        }
    }
  """
}

@ExtendWith(MockitoExtension::class)
class DomainEventSubscriberTest {
  @Mock
  private lateinit var accountService: AccountService

  @InjectMocks
  private lateinit var domainEventSubscriber: DomainEventSubscriber

  @Test
  fun `should call consolidateAccounts with correct order when event is prisoner merged`() {
    val eventString = makePersonCreatedEvent()

    val logger = mockLogger()

    domainEventSubscriber.handleEvents(eventString)

    assert(
      logger.events.any {
        it.formattedMessage.contains("Received CPR person created event")
      },
    )
  }

  @Test
  fun `should throw exception when JSON is malformed to ensure message goes to DLQ`() {
    val malformedJson = "{ \"invalid\": \"json\" " // Missing closing brace

    assertThrows<Exception> {
      domainEventSubscriber.handleEvents(malformedJson)
    }
  }

  @Test
  fun `should throw exception when prisonNumber is missing from event`() {
    val eventString = makePersonCreatedEvent().replace("prisonNumber", "somethingElse")

    assertThrows<IllegalStateException> {
      domainEventSubscriber.handleEvents(eventString)
    }
  }

  @Test
  fun `Log errors when we receive an event of the wrong type`() {
    val eventType = "wrong-event-type"
    val otherEventType = """
      {
      "Message": "{\"eventType\": \"$eventType\"}"
      }
    """.trimIndent()

    val logger = mockLogger()

    domainEventSubscriber.handleEvents(otherEventType)

    assert(
      logger.events.any {
        it.formattedMessage.contains("Ignored unexpected event type: $eventType")
      },
    )
  }
}
