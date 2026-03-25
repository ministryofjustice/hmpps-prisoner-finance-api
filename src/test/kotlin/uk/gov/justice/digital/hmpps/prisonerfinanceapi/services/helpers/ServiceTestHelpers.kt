package uk.gov.justice.digital.hmpps.prisonerfinanceapi.services.helpers

import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.ParentAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.PrisonerPostingListResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.PrisonerTransactionListResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.StatementEntryAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.StatementEntryOppositePostingsResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.StatementEntryResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.SubAccountListResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.SubAccountWithParentResponse
import java.time.Instant
import java.util.UUID
import kotlin.collections.List

class ServiceTestHelpers {

  fun createStatementEntryResponse(
    subAccount: SubAccountWithParentResponse,
    postingType: StatementEntryResponse.PostingType,
    amount: Long,
    statementOppositePosting: List<StatementEntryOppositePostingsResponse>,
  ): StatementEntryResponse = StatementEntryResponse(
    transactionId = UUID.randomUUID(),
    postingCreatedAt = Instant.now(),
    transactionTimestamp = Instant.now(),
    description = "test description",
    oppositePostings = statementOppositePosting,
    amount = amount,
    postingType = postingType,
    subAccount = subAccount,
  )

  fun createTransactionListResponse(
    postings: List<PrisonerPostingListResponse>,
    description: String = "DESC",
    timestamp: Instant = Instant.now(),
    transactionId: UUID = UUID.randomUUID(),
  ): PrisonerTransactionListResponse = PrisonerTransactionListResponse(
    id = transactionId,
    description = description,
    timestamp = timestamp,
    postings = postings,
  )

  fun createPosting(amount: Long, postingType: PrisonerPostingListResponse.Type, subAccountRef: String, reference: String, accountType: ParentAccountResponse.Type) = PrisonerPostingListResponse(
    id = UUID.randomUUID(),
    type = postingType,
    amount = amount,
    subAccount = createSubAccountListResponse(
      subAccountRef = subAccountRef,
      reference = reference,
      type = accountType,
    ),
  )

  fun createSubAccountListResponse(subAccountRef: String, reference: String, type: ParentAccountResponse.Type) = SubAccountListResponse(
    id = UUID.randomUUID(),
    subAccountReference = subAccountRef,
    parentAccount = ParentAccountResponse(
      id = UUID.randomUUID(),
      reference = reference,
      type = type,
    ),
  )

  fun createSubAccountWithParentResponse(parentAccount: StatementEntryAccountResponse, reference: String) = SubAccountWithParentResponse(
    id = UUID.randomUUID(),
    reference = reference,
    parentAccount = parentAccount,
    createdBy = "Test User",
    createdAt = Instant.now(),
  )

  fun createParentAccountResponse(reference: String, type: StatementEntryAccountResponse.Type) = StatementEntryAccountResponse(
    id = UUID.randomUUID(),
    reference = reference,
    type = type,
    createdBy = "Test User",
    createdAt = Instant.now(),
  )

  fun createStatementEntryOppositePostingResponse(subAccount: SubAccountWithParentResponse, amount: Long, type: StatementEntryOppositePostingsResponse.Type): StatementEntryOppositePostingsResponse = StatementEntryOppositePostingsResponse(
    id = UUID.randomUUID(),
    createdBy = "Test User",
    createdAt = Instant.now(),
    type = type,
    amount = amount,
    subAccount = subAccount,
  )
}
