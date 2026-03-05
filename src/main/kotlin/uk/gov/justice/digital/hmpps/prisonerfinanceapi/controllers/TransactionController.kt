package uk.gov.justice.digital.hmpps.prisonerfinanceapi.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.config.ROLE_PRISONER_FINANCE__PROFILE__RO
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.config.TAG_PRISONER_FINANCE
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.response.PrisonerTransactionResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.services.TransactionService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.util.UUID

@Tag(name = TAG_PRISONER_FINANCE)
@RestController
class TransactionController(
  private val transactionService: TransactionService,
) {
  @Operation(
    summary = "Get list of transaction for an account",
    description = "Returns a list of transactions for a given account",
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Retrieved the transactions",
        content = [Content(mediaType = "application/json", array = ArraySchema(schema = Schema(implementation = PrisonerTransactionResponse::class)))],
      ),
      ApiResponse(
        responseCode = "400",
        description = "Bad Request - Invalid UUID for accountId",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized - requires a valid OAuth2 token",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Forbidden - requires an appropriate role",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Resource not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "500",
        description = "Internal Server Error - An unexpected error occurred.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "503",
        description = "Service Unavailable - A dependency service is currently unavailable.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @SecurityRequirement(name = "bearer-jwt", scopes = [ROLE_PRISONER_FINANCE__PROFILE__RO])
  @PreAuthorize("hasAnyAuthority('$ROLE_PRISONER_FINANCE__PROFILE__RO')")
  @GetMapping("/accounts/{accountId}/transactions")
  fun getListOfTransactionsByAccountId(@PathVariable accountId: UUID): ResponseEntity<List<PrisonerTransactionResponse>> {
    val transactions = transactionService.getPrisonerTransactionsByAccountId(accountId)
    return ResponseEntity.ok(transactions)
  }
}
