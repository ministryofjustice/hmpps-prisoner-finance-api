package uk.gov.justice.digital.hmpps.prisonerfinanceapi.integration

import com.fasterxml.jackson.databind.ObjectMapper
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest
import software.amazon.awssdk.services.sqs.model.QueueAttributeName
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.integration.config.LocalStackConfig
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.HmppsSqsConfiguration
import uk.gov.justice.hmpps.sqs.MissingQueueException
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
import java.time.Duration
import java.time.Instant

@Import(HmppsSqsConfiguration::class)
class SqsIntegrationTestBase : IntegrationTestBase() {

  companion object {
    @Suppress("unused")
    @JvmStatic
    @DynamicPropertySource
    fun dynamicProperties(registry: DynamicPropertyRegistry) {
      LocalStackConfig.setLocalStackProperties(LocalStackConfig.instance, registry)
    }

    fun waitUntilEmpty(queueId: String = "domainevents", hmppsQueueService: HmppsQueueService, waitSeconds: Long = 10) {
      val hmppsQueue = hmppsQueueService.findByQueueId(queueId)
        ?: throw IllegalArgumentException("Queue $queueId not found")

      val startWaitTime = Instant.now()

      val sqsClient = hmppsQueue.sqsClient
      val queueUrl = hmppsQueue.queueUrl
      val queueUrlDlq = hmppsQueue.dlqUrl

      await()
        .with().pollInterval(Duration.ofMillis(100))
        .alias("Wait for SQS queue '$queueId' to become empty")
        .atMost(Duration.ofSeconds(waitSeconds)).until {
          val response = sqsClient.getQueueAttributes { builder ->
            builder.queueUrl(queueUrl)
              .attributeNames(
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE,
              )
          }.get()

          val visible = response.attributes()[QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES]?.toInt() ?: 0
          val inFlight = response.attributes()[QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE]?.toInt() ?: 0
          println("[$queueId] Checking if empty -> Visible: $visible, In-Flight: $inFlight")

          return@until (visible + inFlight) == 0
        }

      await()
        .with().pollInterval(Duration.ofMillis(100))
        .alias("Wait for SQS dlq '$queueId' to become empty")
        .atMost(Duration.ofSeconds(waitSeconds)).until {
          val response = sqsClient.getQueueAttributes { builder ->
            builder.queueUrl(queueUrlDlq)
              .attributeNames(
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE,
              )
          }.get()

          val visible = response.attributes()[QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES]?.toInt() ?: 0
          val inFlight = response.attributes()[QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE]?.toInt() ?: 0
          println("[$queueId] Checking DLQ if empty -> Visible: $visible, In-Flight: $inFlight")

          return@until (visible + inFlight) == 0
        }

      println("[$queueId] Queue is empty after ${Duration.between(startWaitTime, Instant.now())}")
    }
  }

  @Autowired
  lateinit var hmppsQueueService: HmppsQueueService

  @Autowired
  lateinit var objectMapper: ObjectMapper

  protected val domainEventQueue by lazy {
    hmppsQueueService.findByQueueId("domainevents")
      ?: throw MissingQueueException("HmppsQueue domainevents not found")
  }

  protected val domainEventsTopic by lazy {
    hmppsQueueService.findByTopicId("domainevents")
      ?: throw MissingQueueException("HmppsTopic domainevents not found")
  }

  protected val domainEventsTopicSnsClient by lazy { domainEventsTopic.snsClient }
  protected val domainEventsTopicArn by lazy { domainEventsTopic.arn }

  @BeforeEach
  fun cleanQueue() {
    domainEventQueue.sqsClient.purgeQueue(
      PurgeQueueRequest.builder().queueUrl(domainEventQueue.queueUrl).build(),
    )
    domainEventQueue.sqsClient.countMessagesOnQueue(domainEventQueue.queueUrl).get()
  }

  protected fun jsonString(any: Any) = objectMapper.writeValueAsString(any) as String
}
