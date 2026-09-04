package uk.gov.justice.digital.hmpps.prisonerfinanceapi.integration

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.integration.wiremock.GeneralLedgerApiExtension.Companion.generalLedgerApi
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.domainevents.AdditionalInformation
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.domainevents.CprPersonCreated
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.domainevents.HmppsMergeEvent
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.domainevents.OffenderInsertedEvent
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.domainevents.PersonIdentifier
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.domainevents.PersonReference
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.CreatePostingRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.CreateTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.PostingResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.SubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.TransactionResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.services.domainevents.DomainEventSubscriber
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.services.domainevents.DomainEventSubscriber.Companion.OFFENDER_INSERTED
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.services.domainevents.DomainEventSubscriber.Companion.PRISONER_ACCOUNT_MERGED
import java.time.Instant
import java.util.UUID
import kotlin.math.abs
import kotlin.random.Random
import kotlin.random.nextLong

class DomainEventsTest : SqsIntegrationTestBase() {

  @BeforeEach
  fun beforeEach() {
    generalLedgerApi.resetAll()
  }

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

  open fun publishOffenderInsertedEvent(prisonNumber: String) {
    domainEventsTopicSnsClient.publish(
      PublishRequest.builder()
        .topicArn(domainEventsTopicArn)
        .message(
          jsonString(
            OffenderInsertedEvent(
              eventType = "OFFENDER-INSERTED",
              eventDateTime = "2026-09-04T10:59:47",
              offenderId = "2123456",
              offenderIdDisplay = prisonNumber,
            ),
          ),
        ).messageAttributes(
          mapOf(
            "eventType" to
              MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(OFFENDER_INSERTED).build(),
          ),
        ).build(),
    )
  }

  private fun publishMergeEvent(removedNomsNumber: String, nomsNumber: String) {
    domainEventsTopicSnsClient.publish(
      PublishRequest.builder()
        .topicArn(domainEventsTopicArn)
        .message(
          jsonString(
            HmppsMergeEvent(
              eventType = PRISONER_ACCOUNT_MERGED,
              additionalInformation = AdditionalInformation(
                nomsNumber = nomsNumber,
                removedNomsNumber = removedNomsNumber,
                reason = "merged",
              ),
            ),
          ),
        )
        .messageAttributes(
          mapOf(
            "eventType" to
              MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(PRISONER_ACCOUNT_MERGED).build(),
          ),
        )
        .build(),
    )
  }

  @Nested
  inner class MergePrisonerEventTest {

    @Test
    fun `Should send an adjustment for each sub-account balance that is positive on the removed prisoner account`() {
      val realAccountPrisonNumber = "A1234AA"
      val realAccountId = UUID.randomUUID()
      val realAccountSubAccountUUIDs = listOf(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())

      generalLedgerApi.stubGetAccountListWithAccount(
        accountRef = realAccountPrisonNumber,
        returnAccountId = realAccountId,
        subAccounts = listOf(
          SubAccountResponse(
            id = realAccountSubAccountUUIDs.first(),
            reference = "CASH",
            parentAccountId = realAccountId,
            createdBy = "test",
            createdAt = Instant.now(),
          ),
          SubAccountResponse(
            id = realAccountSubAccountUUIDs[1],
            reference = "SAVINGS",
            parentAccountId = realAccountId,
            createdBy = "test",
            createdAt = Instant.now(),
          ),
          SubAccountResponse(
            id = realAccountSubAccountUUIDs.last(),
            reference = "SPENDS",
            parentAccountId = realAccountId,
            createdBy = "test",
            createdAt = Instant.now(),
          ),
        ),
      )

      val fakeAccountPrisonNumber = "A1234BB"
      val fakeAccountId = UUID.randomUUID()
      val fakeAccountSubAccountUUIDs = listOf(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())

      generalLedgerApi.stubGetAccountListWithAccount(
        accountRef = fakeAccountPrisonNumber,
        returnAccountId = fakeAccountId,
        subAccounts = listOf(
          SubAccountResponse(
            id = fakeAccountSubAccountUUIDs.first(),
            reference = "CASH",
            parentAccountId = realAccountId,
            createdBy = "test",
            createdAt = Instant.now(),
          ),
          SubAccountResponse(
            id = fakeAccountSubAccountUUIDs[1],
            reference = "SAVINGS",
            parentAccountId = realAccountId,
            createdBy = "test",
            createdAt = Instant.now(),
          ),
          SubAccountResponse(
            id = fakeAccountSubAccountUUIDs.last(),
            reference = "SPENDS",
            parentAccountId = realAccountId,
            createdBy = "test",
            createdAt = Instant.now(),
          ),
        ),
      )

      fakeAccountSubAccountUUIDs.withIndex().forEach { (index, fakeSubAccountUUID) ->

        val realSubAccountUUID = realAccountSubAccountUUIDs[index]
        val balance = Random.nextLong(1, 999)

        generalLedgerApi.stubGetSubAccountBalance(
          subAccountId = fakeSubAccountUUID,
          balanceAmount = balance,
        )

        val adjustmentDescription = "ADJ - MERGED FROM $fakeAccountPrisonNumber TO $realAccountPrisonNumber"

        generalLedgerApi.stubPostTransactionForRequest(
          request = CreateTransactionRequest(
            reference = "",
            description = adjustmentDescription,
            timestamp = Instant.now(),
            amount = balance,
            entrySequence = 1,
            postings = listOf(
              CreatePostingRequest(
                subAccountId = fakeSubAccountUUID,
                type = CreatePostingRequest.Type.DR,
                amount = balance,
                entrySequence = 1,
              ),
              CreatePostingRequest(
                subAccountId = realSubAccountUUID,
                type = CreatePostingRequest.Type.CR,
                amount = balance,
                entrySequence = 2,
              ),
            ),
          ),
          payload = TransactionResponse(
            id = UUID.randomUUID(),
            legacyTransactionId = null,
            createdBy = "TEST",
            createdAt = Instant.now(),
            reference = "Merged",
            description = adjustmentDescription,
            amount = balance,
            postings = listOf(
              PostingResponse(
                id = UUID.randomUUID(),
                createdBy = "TEST",
                createdAt = Instant.now(),
                type = PostingResponse.Type.DR,
                amount = balance,
                subAccountID = fakeSubAccountUUID,
              ),
              PostingResponse(
                id = UUID.randomUUID(),
                createdBy = "TEST",
                createdAt = Instant.now(),
                type = PostingResponse.Type.CR,
                amount = balance,
                subAccountID = realSubAccountUUID,
              ),
            ),
            timestamp = Instant.now(),
          ),
        )
      }

      publishMergeEvent(removedNomsNumber = fakeAccountPrisonNumber, nomsNumber = realAccountPrisonNumber)

      waitUntilEmpty(
        hmppsQueueService = hmppsQueueService,
      )

      generalLedgerApi.verify(2, getRequestedFor(urlPathMatching("/accounts*")))
      generalLedgerApi.verify(3, getRequestedFor(urlPathMatching("/sub-accounts/[^/]+/balance")))
      generalLedgerApi.verify(3, postRequestedFor(urlPathMatching("/transactions")))
    }

    @Test
    fun `Should not send an adjustment for a sub-account if the balance is zero on the removed prisoner account`() {
      val realAccountPrisonNumber = "A1234AA"
      val realAccountId = UUID.randomUUID()
      val realAccountSubAccountUUIDs = listOf(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())

      generalLedgerApi.stubGetAccountListWithAccount(
        accountRef = realAccountPrisonNumber,
        returnAccountId = realAccountId,
        subAccounts = listOf(
          SubAccountResponse(
            id = realAccountSubAccountUUIDs.first(),
            reference = "CASH",
            parentAccountId = realAccountId,
            createdBy = "test",
            createdAt = Instant.now(),
          ),
          SubAccountResponse(
            id = realAccountSubAccountUUIDs[1],
            reference = "SAVINGS",
            parentAccountId = realAccountId,
            createdBy = "test",
            createdAt = Instant.now(),
          ),
          SubAccountResponse(
            id = realAccountSubAccountUUIDs.last(),
            reference = "SPENDS",
            parentAccountId = realAccountId,
            createdBy = "test",
            createdAt = Instant.now(),
          ),
        ),
      )

      val fakeAccountPrisonNumber = "A1234BB"
      val fakeAccountId = UUID.randomUUID()
      val fakeAccountSubAccountUUIDs = listOf(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())

      generalLedgerApi.stubGetAccountListWithAccount(
        accountRef = fakeAccountPrisonNumber,
        returnAccountId = fakeAccountId,
        subAccounts = listOf(
          SubAccountResponse(
            id = fakeAccountSubAccountUUIDs.first(),
            reference = "CASH",
            parentAccountId = realAccountId,
            createdBy = "test",
            createdAt = Instant.now(),
          ),
          SubAccountResponse(
            id = fakeAccountSubAccountUUIDs[1],
            reference = "SAVINGS",
            parentAccountId = realAccountId,
            createdBy = "test",
            createdAt = Instant.now(),
          ),
          SubAccountResponse(
            id = fakeAccountSubAccountUUIDs.last(),
            reference = "SPENDS",
            parentAccountId = realAccountId,
            createdBy = "test",
            createdAt = Instant.now(),
          ),
        ),
      )

      fakeAccountSubAccountUUIDs.withIndex().forEach { (index, fakeSubAccountUUID) ->

        val realSubAccountUUID = realAccountSubAccountUUIDs[index]
        val balance = if (index == 0) 0L else (Random.nextLong(1, 9999))

        generalLedgerApi.stubGetSubAccountBalance(
          subAccountId = fakeSubAccountUUID,
          balanceAmount = balance,
        )

        if (balance == 0L)return@forEach

        val adjustmentDescription = "ADJ - MERGED FROM $fakeAccountPrisonNumber TO $realAccountPrisonNumber"

        generalLedgerApi.stubPostTransactionForRequest(
          request = CreateTransactionRequest(
            reference = "",
            description = adjustmentDescription,
            timestamp = Instant.now(),
            amount = balance,
            entrySequence = 1,
            postings = listOf(
              CreatePostingRequest(
                subAccountId = fakeSubAccountUUID,
                type = CreatePostingRequest.Type.DR,
                amount = balance,
                entrySequence = 1,
              ),
              CreatePostingRequest(
                subAccountId = realSubAccountUUID,
                type = CreatePostingRequest.Type.CR,
                amount = balance,
                entrySequence = 2,
              ),
            ),
          ),
          payload = TransactionResponse(
            id = UUID.randomUUID(),
            legacyTransactionId = null,
            createdBy = "TEST",
            createdAt = Instant.now(),
            reference = "Merged",
            description = "ADJ - MERGED",
            amount = balance,
            postings = listOf(
              PostingResponse(
                id = UUID.randomUUID(),
                createdBy = "TEST",
                createdAt = Instant.now(),
                type = PostingResponse.Type.DR,
                amount = balance,
                subAccountID = fakeSubAccountUUID,
              ),
              PostingResponse(
                id = UUID.randomUUID(),
                createdBy = "TEST",
                createdAt = Instant.now(),
                type = PostingResponse.Type.CR,
                amount = balance,
                subAccountID = realSubAccountUUID,
              ),
            ),
            timestamp = Instant.now(),
          ),
        )
      }

      publishMergeEvent(removedNomsNumber = fakeAccountPrisonNumber, nomsNumber = realAccountPrisonNumber)

      waitUntilEmpty(
        hmppsQueueService = hmppsQueueService,
      )

      generalLedgerApi.verify(2, getRequestedFor(urlPathMatching("/accounts*")))
      generalLedgerApi.verify(3, getRequestedFor(urlPathMatching("/sub-accounts/[^/]+/balance")))
      generalLedgerApi.verify(2, postRequestedFor(urlPathMatching("/transactions")))
    }

    @Test
    fun `Should send an negative adjustment for each sub-account balance that is negative on the removed prisoner account`() {
      val realAccountPrisonNumber = "A1234AA"
      val realAccountId = UUID.randomUUID()
      val realAccountSubAccountUUIDs = listOf(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())

      generalLedgerApi.stubGetAccountListWithAccount(
        accountRef = realAccountPrisonNumber,
        returnAccountId = realAccountId,
        subAccounts = listOf(
          SubAccountResponse(
            id = realAccountSubAccountUUIDs.first(),
            reference = "CASH",
            parentAccountId = realAccountId,
            createdBy = "test",
            createdAt = Instant.now(),
          ),
          SubAccountResponse(
            id = realAccountSubAccountUUIDs[1],
            reference = "SAVINGS",
            parentAccountId = realAccountId,
            createdBy = "test",
            createdAt = Instant.now(),
          ),
          SubAccountResponse(
            id = realAccountSubAccountUUIDs.last(),
            reference = "SPENDS",
            parentAccountId = realAccountId,
            createdBy = "test",
            createdAt = Instant.now(),
          ),
        ),
      )

      val fakeAccountPrisonNumber = "A1234BB"
      val fakeAccountId = UUID.randomUUID()
      val fakeAccountSubAccountUUIDs = listOf(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())

      generalLedgerApi.stubGetAccountListWithAccount(
        accountRef = fakeAccountPrisonNumber,
        returnAccountId = fakeAccountId,
        subAccounts = listOf(
          SubAccountResponse(
            id = fakeAccountSubAccountUUIDs.first(),
            reference = "CASH",
            parentAccountId = realAccountId,
            createdBy = "test",
            createdAt = Instant.now(),
          ),
          SubAccountResponse(
            id = fakeAccountSubAccountUUIDs[1],
            reference = "SAVINGS",
            parentAccountId = realAccountId,
            createdBy = "test",
            createdAt = Instant.now(),
          ),
          SubAccountResponse(
            id = fakeAccountSubAccountUUIDs.last(),
            reference = "SPENDS",
            parentAccountId = realAccountId,
            createdBy = "test",
            createdAt = Instant.now(),
          ),
        ),
      )

      fakeAccountSubAccountUUIDs.withIndex().forEach { (index, fakeSubAccountUUID) ->

        val realSubAccountUUID = realAccountSubAccountUUIDs[index]
        val balance = if (index == 0) -10L else Random.nextLong(1, 999)

        generalLedgerApi.stubGetSubAccountBalance(
          subAccountId = fakeSubAccountUUID,
          balanceAmount = balance,
        )

        val absBalance = abs(balance)

        val debitingAccount = if (balance > 0) fakeSubAccountUUID else realSubAccountUUID
        val creditingAccount = if (balance > 0) realSubAccountUUID else fakeSubAccountUUID

        val adjustmentDescription = "ADJ - MERGED FROM $fakeAccountPrisonNumber TO $realAccountPrisonNumber"

        generalLedgerApi.stubPostTransactionForRequest(
          request = CreateTransactionRequest(
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
          ),
          payload = TransactionResponse(
            id = UUID.randomUUID(),
            legacyTransactionId = null,
            createdBy = "TEST",
            createdAt = Instant.now(),
            reference = "Merged",
            description = "ADJ - MERGED",
            amount = absBalance,
            postings = listOf(
              PostingResponse(
                id = UUID.randomUUID(),
                createdBy = "TEST",
                createdAt = Instant.now(),
                type = PostingResponse.Type.DR,
                amount = absBalance,
                subAccountID = debitingAccount,
              ),
              PostingResponse(
                id = UUID.randomUUID(),
                createdBy = "TEST",
                createdAt = Instant.now(),
                type = PostingResponse.Type.CR,
                amount = absBalance,
                subAccountID = creditingAccount,
              ),
            ),
            timestamp = Instant.now(),
          ),
        )
      }

      publishMergeEvent(removedNomsNumber = fakeAccountPrisonNumber, nomsNumber = realAccountPrisonNumber)

      waitUntilEmpty(
        hmppsQueueService = hmppsQueueService,
      )

      generalLedgerApi.verify(2, getRequestedFor(urlPathMatching("/accounts*")))
      generalLedgerApi.verify(3, getRequestedFor(urlPathMatching("/sub-accounts/[^/]+/balance")))
      generalLedgerApi.verify(3, postRequestedFor(urlPathMatching("/transactions")))
    }
  }

  @Nested
  inner class OffenderInsertedEventTest {
    @Test
    fun `When receiving an offender inserted event, it should check if the parent account and subAccounts already exist in GL`() {
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

      publishOffenderInsertedEvent(prisonNumber)

      waitUntilEmpty(
        hmppsQueueService = hmppsQueueService,
      )

      generalLedgerApi.verify(1, getRequestedFor(urlPathMatching("/accounts*")))
    }

    @Test
    fun `When receiving an offender inserted event, it should check the parent account and create all subAccounts`() {
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

      publishOffenderInsertedEvent(prisonNumber)

      waitUntilEmpty(
        hmppsQueueService = hmppsQueueService,
      )

      generalLedgerApi.verify(1, getRequestedFor(urlPathMatching("/accounts*")))
      generalLedgerApi.verify(3, postRequestedFor(urlPathMatching("/accounts/$parentAccountId/sub-accounts")))
    }

    @Test
    fun `When receiving an offender inserted event, it should check the parent account and create any missing subAccounts`() {
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

      publishOffenderInsertedEvent(prisonNumber)

      waitUntilEmpty(
        hmppsQueueService = hmppsQueueService,
      )

      generalLedgerApi.verify(1, getRequestedFor(urlEqualTo("/accounts?reference=$prisonNumber")))
      generalLedgerApi.verify(1, postRequestedFor(urlPathMatching("/accounts/$parentAccountId/sub-accounts")))
    }

    @Test
    fun `When receiving an offender inserted event, it should check the parent account and create the parent account and all subAccounts`() {
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

      publishOffenderInsertedEvent(prisonNumber)

      waitUntilEmpty(
        hmppsQueueService = hmppsQueueService,
      )

      generalLedgerApi.verify(1, getRequestedFor(urlEqualTo("/accounts?reference=$prisonNumber")))
      generalLedgerApi.verify(1, postRequestedFor(urlPathMatching("/accounts")))

      generalLedgerApi.verify(3, postRequestedFor(urlPathMatching("/accounts/$parentAccountId/sub-accounts")))
    }
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
    fun `When receiving an account created event, it should check the parent account and create any missing subAccounts`() {
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

      generalLedgerApi.verify(1, getRequestedFor(urlEqualTo("/accounts?reference=$prisonNumber")))
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

      generalLedgerApi.verify(1, getRequestedFor(urlEqualTo("/accounts?reference=$prisonNumber")))
      generalLedgerApi.verify(1, postRequestedFor(urlPathMatching("/accounts")))

      generalLedgerApi.verify(3, postRequestedFor(urlPathMatching("/accounts/$parentAccountId/sub-accounts")))
    }
  }
}
