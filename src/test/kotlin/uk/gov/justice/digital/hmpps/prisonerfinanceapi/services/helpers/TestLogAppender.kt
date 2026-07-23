package uk.gov.justice.digital.hmpps.prisonerfinanceapi.services.helpers

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.services.domainevents.DomainEventSubscriber

class TestLogAppender : AppenderBase<ILoggingEvent>() {
  val events = mutableListOf<ILoggingEvent>()

  override fun append(event: ILoggingEvent) {
    events.add(event)
  }
}

fun mockLogger(): TestLogAppender {
  val logger = LoggerFactory.getLogger(
    DomainEventSubscriber::class.java,
  ) as Logger
  val testLogAppender = TestLogAppender().apply {
    context = logger.loggerContext
    start()
  }
  logger.addAppender(testLogAppender)
  return testLogAppender
}
