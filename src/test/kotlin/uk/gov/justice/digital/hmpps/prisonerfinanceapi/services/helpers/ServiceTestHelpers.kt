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
    transactionId,
    description = description,
    timestamp = timestamp,
    postings = postings,
  )

  fun createPosting(amount: Long, postingType: PrisonerPostingListResponse.Type, subAccountRef: String, reference: String, accountType: ParentAccountListResponse.Type) = PrisonerPostingListResponse(
    UUID.randomUUID(),
    postingType,
    amount = amount,
    createSubAccountListResponse(
      subAccountRef,
      reference,
      accountType,
    ),
  )

  fun createSubAccountListResponse(subAccountRef: String, reference: String, type: ParentAccountListResponse.Type) = SubAccountListResponse(
    UUID.randomUUID(),
    subAccountRef,
    ParentAccountListResponse(
      UUID.randomUUID(),
      reference,
      type,
    ),
  )
}
