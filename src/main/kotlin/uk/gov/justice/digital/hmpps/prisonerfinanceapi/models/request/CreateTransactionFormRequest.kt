package uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.request

import java.util.UUID

class CreateTransactionFormRequest(
  val creditSubAccountId: UUID,
  val debitSubAccountId: UUID,
  val amount: Long,
  val description: String,
)
