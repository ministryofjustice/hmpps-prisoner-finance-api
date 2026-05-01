package uk.gov.justice.digital.hmpps.prisonerfinanceapi.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.config.ROLE_PRISONER_FINANCE__PROFILE__RW
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.TransactionResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.request.CreateTransactionFormRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.services.TransactionService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

@Tag(name = "Transaction controller")
@RestController
class TransactionController(private val transactionService: TransactionService) {

  @Operation(
    summary = "Create a transaction",
    description = "Returns the created transaction from General Ledger",
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Transaction Created",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = TransactionResponse::class))],
      ),
      ApiResponse(
        responseCode = "400",
        description = "Bad Request",
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
        description = "Sub-Account not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "500",
        description = "Internal Server Error - An unexpected error occurred.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "502",
        description = "Bad Gateway - A dependency service is currently unreachable or throwing an error.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @SecurityRequirement(name = "bearer-jwt", scopes = [ROLE_PRISONER_FINANCE__PROFILE__RW])
  @PreAuthorize("hasAnyAuthority('$ROLE_PRISONER_FINANCE__PROFILE__RW')")
  @PostMapping("/transaction")
  fun createTransaction(@RequestBody @Valid payload: CreateTransactionFormRequest): ResponseEntity<TransactionResponse> {
    val createdTransaction = transactionService.createTransaction(payload)
    return ResponseEntity.ok(createdTransaction)
  }
}
