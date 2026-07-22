package uk.gov.justice.digital.hmpps.prisonerfinanceapi.integration

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.integration.wiremock.GeneralLedgerApiExtension.Companion.generalLedgerApi
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.domainevents.CprPersonCreated
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.domainevents.PersonIdentifier
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.domainevents.PersonReference
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.SubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.services.domainevents.DomainEventSubscriber
import java.time.Instant
import java.util.UUID

class DomainEventsTest : SqsIntegrationTestBase() {
  private fun publishPersonCreatedEvent(prisonNumber: String) {
    domainEventsTopicSnsClient.publish(
      PublishRequest.builder()
        .topicArn(domainEventsTopicArn)
        .message(
          jsonString(
            CprPersonCreated(
              eventType = DomainEventSubscriber.PRISON_RECORD_CREATED,
              version = 1,
              occurredAt = Instant.now().toString(),
              description = "",
              detailUrl = "/test",
              personReference = PersonReference(
                identifiers = listOf(
                  PersonIdentifier(
                    type = "prisonNumber",
                    value = prisonNumber,
                  ),
                ),
              ),
            ),
          ),
        )
        .messageAttributes(
          mapOf(
            "eventType" to
              MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(DomainEventSubscriber.PRISON_RECORD_CREATED).build(),
          ),
        )
        .build(),
    )
  }

  @Nested
  inner class CreatedPersonEventTest {

    @Test
    fun `When receiving an account created event, it should check if the parent account and subAccounts already exist in GL`() {
      val prisonNumber = "A1234AA"

      val parentAccountId = UUID.randomUUID()

      generalLedgerApi.stubGetAccountListWithAccount(
        accountRef = prisonNumber,
        returnAccountId = parentAccountId,
        subAccounts = listOf(
          SubAccountResponse(
            id = UUID.randomUUID(),
            reference = "CASH",
            parentAccountId = parentAccountId,
            createdBy = "test",
            createdAt = Instant.now(),
          ),
          SubAccountResponse(
            id = UUID.randomUUID(),
            reference = "SAVINGS",
            parentAccountId = parentAccountId,
            createdBy = "test",
            createdAt = Instant.now(),
          ),
          SubAccountResponse(
            id = UUID.randomUUID(),
            reference = "SPENDS",
            parentAccountId = parentAccountId,
            createdBy = "test",
            createdAt = Instant.now(),
          ),
        ),
      )

      publishPersonCreatedEvent(prisonNumber)

      waitUntilEmpty(
        hmppsQueueService = hmppsQueueService,
      )

      generalLedgerApi.verify(1, getRequestedFor(urlPathMatching("/accounts*")))
    }

    @Test
    fun `When receiving an account created event, it should check the parent account and create all subAccounts`() {
      val prisonNumber = "A1234AA"

      val parentAccountId = UUID.randomUUID()

      generalLedgerApi.stubGetAccountListWithAccount(
        accountRef = prisonNumber,
        returnAccountId = parentAccountId,
      )

      generalLedgerApi.stubCreateSubAccount(
        parentId = parentAccountId,
        reference = "CASH",
        returnUuid = UUID.randomUUID().toString(),
      )
      generalLedgerApi.stubCreateSubAccount(
        parentId = parentAccountId,
        reference = "SPENDS",
        returnUuid = UUID.randomUUID().toString(),
      )
      generalLedgerApi.stubCreateSubAccount(
        parentId = parentAccountId,
        reference = "SAVINGS",
        returnUuid = UUID.randomUUID().toString(),
      )

      publishPersonCreatedEvent(prisonNumber)

      waitUntilEmpty(
        hmppsQueueService = hmppsQueueService,
      )

      generalLedgerApi.verify(1, getRequestedFor(urlPathMatching("/accounts*")))
      generalLedgerApi.verify(3, postRequestedFor(urlPathMatching("/accounts/$parentAccountId/sub-accounts")))
    }

    @Test
    fun `When receiving an account created event, it should check the parent account and create any missing subAccount`() {
      val prisonNumber = "A1234AA"

      val parentAccountId = UUID.randomUUID()

      generalLedgerApi.stubGetAccountListWithAccount(
        accountRef = prisonNumber,
        returnAccountId = parentAccountId,
        subAccounts = listOf(
          SubAccountResponse(
            id = UUID.randomUUID(),
            reference = "CASH",
            parentAccountId = parentAccountId,
            createdBy = "test",
            createdAt = Instant.now(),
          ),
          SubAccountResponse(
            id = UUID.randomUUID(),
            reference = "SPENDS",
            parentAccountId = parentAccountId,
            createdBy = "test",
            createdAt = Instant.now(),
          ),
        ),
      )

      generalLedgerApi.stubCreateSubAccount(
        parentId = parentAccountId,
        reference = "SAVINGS",
        returnUuid = UUID.randomUUID().toString(),
      )

      publishPersonCreatedEvent(prisonNumber)

      waitUntilEmpty(
        hmppsQueueService = hmppsQueueService,
      )

      generalLedgerApi.verify(1, getRequestedFor(urlPathMatching("/accounts*")))
      generalLedgerApi.verify(1, postRequestedFor(urlPathMatching("/accounts/$parentAccountId/sub-accounts")))
    }

    @Test
    fun `When receiving an account created event, it should check the parent account and create the parent account and all subAccounts`() {
      val prisonNumber = "A1234AA"

      val parentAccountId = UUID.randomUUID()

      generalLedgerApi.stubGetAccountListWithAccountReturningAnEmptyList(accountRef = prisonNumber)

      generalLedgerApi.stubCreateAccount(
        reference = prisonNumber,
        returnUuid = parentAccountId,
      )

      generalLedgerApi.stubCreateSubAccount(
        parentId = parentAccountId,
        reference = "CASH",
        returnUuid = UUID.randomUUID().toString(),
      )
      generalLedgerApi.stubCreateSubAccount(
        parentId = parentAccountId,
        reference = "SPENDS",
        returnUuid = UUID.randomUUID().toString(),
      )
      generalLedgerApi.stubCreateSubAccount(
        parentId = parentAccountId,
        reference = "SAVINGS",
        returnUuid = UUID.randomUUID().toString(),
      )

      publishPersonCreatedEvent(prisonNumber)

      waitUntilEmpty(
        hmppsQueueService = hmppsQueueService,
      )

      generalLedgerApi.verify(1, getRequestedFor(urlPathMatching("/accounts*")))
      generalLedgerApi.verify(1, postRequestedFor(urlPathMatching("/accounts*")))

      generalLedgerApi.verify(3, postRequestedFor(urlPathMatching("/accounts/$parentAccountId/sub-accounts")))
    }
  }
}
