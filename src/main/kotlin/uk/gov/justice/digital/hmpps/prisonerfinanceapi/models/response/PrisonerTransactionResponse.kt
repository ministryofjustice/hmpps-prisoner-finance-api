package uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.response

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
@Schema(description = "A prisoner transaction response for the UI")
data class PrisonerTransactionResponse(
  @field:Schema(description = "Timestamp of the transaction")
  val date: Instant,
  @field:Schema(description = "Description the transaction")
  val description: String,
  @field:Schema(description = "Amount credited to the account")
  val credit: Long,
  @field:Schema(description = "Amount debited from the account")
  val debit: Long,
  @field:Schema(description = "Caseload of the prison if the transaction included a prison account")
  val location: String,
  @field:Schema(description = "Sub-account reference involved")
  val accountType: String,
)
