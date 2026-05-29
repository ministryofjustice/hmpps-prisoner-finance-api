package uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.request

import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.CreatePostingRequest

class CreateBatchTransactionFormRequest(
  val caseloadId: String,
  val caseloadSubAccountRef: String,
  val postingType: CreatePostingRequest.Type,
  val controlAmount: Long,
  val description: String,
  val prisonNumbersPostings: List<PrisonerPosting>,
)

class PrisonerPosting(
  val prisonNumber: String,
  val postingType: CreatePostingRequest.Type,
  val prisonerSubAccountRef: String,
  val amount: Long,
)
