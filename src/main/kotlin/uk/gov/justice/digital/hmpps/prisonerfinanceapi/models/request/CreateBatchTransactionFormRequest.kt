package uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.request

import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.StatementEntryResponse

class CreateBatchTransactionFormRequest(
  val caseloadId: String,
  val caseloadSubAccount: String,
  val postingType: StatementEntryResponse.PostingType,
  val controlAmount: Long,
  val description: String,
  val prisonNumbersPostings: List<PrisonNumberPosting>,
)

class PrisonNumberPosting(
  val prisonNumber: String,
  val postingType: StatementEntryResponse.PostingType,
  val amount: Long,
)
