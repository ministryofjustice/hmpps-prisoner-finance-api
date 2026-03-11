package uk.gov.justice.digital.hmpps.prisonerfinanceapi.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.CustomException
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.config.ROLE_PRISONER_FINANCE__PROFILE__RO
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.AccountBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.response.PrisonerTransactionResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.services.AccountService
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.services.TransactionService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

@Tag(name = "Prisoner money controller")
@RestController
class PrisonerMoneyController(
  private val transactionService: TransactionService,
  private val accountService: AccountService,
) {
  @Operation(
    summary = "Get list of transactions for a prisoner",
    description = "Returns a list of transactions for a given prisoner number",
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Retrieved the transactions",
        content = [Content(mediaType = "application/json", array = ArraySchema(schema = Schema(implementation = PrisonerTransactionResponse::class)))],
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
        description = "Account not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "500",
        description = "Internal Server Error - An unexpected error occurred.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "502",
        description = "Service Unavailable - A dependency service is currently unavailable.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @SecurityRequirement(name = "bearer-jwt", scopes = [ROLE_PRISONER_FINANCE__PROFILE__RO])
  @PreAuthorize("hasAnyAuthority('$ROLE_PRISONER_FINANCE__PROFILE__RO')")
  @GetMapping("/prisoners/{prisonNumber}/money/transactions")
  fun getListOfTransactionsForPrisonNumber(@PathVariable prisonNumber: String): ResponseEntity<List<PrisonerTransactionResponse>> {
    val account = accountService.getAccountByReference(prisonNumber)

    if (account == null) throw CustomException(status = HttpStatus.NOT_FOUND, message = "Account not found")

    val transactions = transactionService.getPrisonerTransactionsByAccountId(account.id)

    return ResponseEntity.ok(transactions)
  }

  @Operation(
    summary = "Gets the total balance of a prisoner",
    description = "Returns the total balance of a prisoner",
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Retrieved the total prisoner's balance",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = AccountBalanceResponse::class))],
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
        description = "Account not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "500",
        description = "Internal Server Error - An unexpected error occurred.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "502",
        description = "Service Unavailable - A dependency service is currently unavailable.",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  @SecurityRequirement(name = "bearer-jwt", scopes = [ROLE_PRISONER_FINANCE__PROFILE__RO])
  @PreAuthorize("hasAnyAuthority('$ROLE_PRISONER_FINANCE__PROFILE__RO')")
  @GetMapping("/prisoners/{prisonNumber}/money/balance")
  fun getAccountBalance(
    @PathVariable prisonNumber: String,
  ): ResponseEntity<AccountBalanceResponse> {
    val account = accountService.getAccountByReference(prisonNumber)

    if (account == null) throw CustomException(status = HttpStatus.NOT_FOUND, message = "Account not found")

    val balance = accountService.getAccountBalance(accountUUID = account.id)

    return ResponseEntity.ok(balance)
  }
}
