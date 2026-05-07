package uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Digits
import java.math.BigDecimal
import java.util.UUID

class CreateTransactionFormRequest(
  val creditSubAccountId: UUID,
  val debitSubAccountId: UUID,
  @field:Schema(
    description = "The amount of the transaction, expressed as a decimal, up to 2 decimal places.",
    example = "162.00",
    format = "decimal",
    type = "string",
    required = true,
  )
  @field:Digits(integer = 19, fraction = 2)
  val amount: BigDecimal,
  val description: String,
)
