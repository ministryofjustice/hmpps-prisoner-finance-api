package uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.response

import java.time.Instant

data class PrisonerFinanceTransactionResponse(
  val date: Instant,
  val description: String,
  val credit: Long,
  val debit: Long,
  val location: String,
  val accountType: String,
)
