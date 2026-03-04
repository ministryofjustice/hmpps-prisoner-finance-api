package uk.gov.justice.digital.hmpps.prisonerfinanceapi.services.helpers

import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.ParentAccountListResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.PrisonerPostingListResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.PrisonerTransactionListResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.SubAccountListResponse
import java.time.Instant
import java.util.UUID
import kotlin.collections.List

class ServiceTestHelpers {

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

  fun createPosting(amount: Long, postingType: PrisonerPostingListResponse.Type, subAccountRef: String, reference: String, accountType: ParentAccountListResponse.Type) = PrisonerPostingListResponse(
    id = UUID.randomUUID(),
    type = postingType,
    amount = amount,
    subAccount = createSubAccountListResponse(
      subAccountRef = subAccountRef,
      reference = reference,
      type = accountType,
    ),
  )

  fun createSubAccountListResponse(subAccountRef: String, reference: String, type: ParentAccountListResponse.Type) = SubAccountListResponse(
    id = UUID.randomUUID(),
    subAccountReference = subAccountRef,
    parentAccount = ParentAccountListResponse(
      id = UUID.randomUUID(),
      reference = reference,
      type = type,
    ),
  )
}
