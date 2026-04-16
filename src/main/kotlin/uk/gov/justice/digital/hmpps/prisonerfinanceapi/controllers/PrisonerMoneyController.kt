package uk.gov.justice.digital.hmpps.prisonerfinanceapi.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.CustomException
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.config.ROLE_PRISONER_FINANCE__PROFILE__RO
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.AccountBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.SubAccountBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.response.PagedPrisonerTransactionResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.services.AccountService
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.services.TransactionService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.time.LocalDate
import java.util.UUID

@Tag(name = "Prisoner money controller")
@RestController
class PrisonerMoneyController(
  private val transactionService: TransactionService,
  private val accountService: AccountService,
) {
  @Operation(
    summary = "Get list of transactions for a prisoner",
    description = "Returns a list of transactions for a given prisoner number",
    parameters = [
      Parameter(
        name = "startDate",
        description = "Filter statements from start date (inclusive) in a yyyy-MM-dd format",
        required = false,
        example = "2025-12-24",
      ),
      Parameter(
        name = "endDate",
        description = "Filter statements to end date (inclusive) in a yyyy-MM-dd format",
        required = false,
        example = "2025-12-25",
      ),
      Parameter(name = "pageNumber", description = "Filter statements from page number"),
      Parameter(name = "pageSize", description = "Sets the page size when returning the pages results"),
      Parameter(name = "credit", description = "Filter statements using the PostingType CR"),
      Parameter(name = "debit", description = "Filter statements using the PostingType DR"),
      Parameter(name = "subAccountReference", description = "Filter statements using the sub account reference"),
    ],
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Retrieved the transactions",
        content = [Content(mediaType = "application/json", array = ArraySchema(schema = Schema(implementation = PagedPrisonerTransactionResponse::class)))],
      ),
      ApiResponse(
        responseCode = "400",
        description = "Bad Request",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "400",
        description = "Page requested is out of range",
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
        description = "Account not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Sub Account not found",
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
  @SecurityRequirement(name = "bearer-jwt", scopes = [ROLE_PRISONER_FINANCE__PROFILE__RO])
  @PreAuthorize("hasAnyAuthority('$ROLE_PRISONER_FINANCE__PROFILE__RO')")
  @GetMapping("/prisoners/{prisonNumber}/money/transactions")
  fun getListOfTransactionsForPrisonNumber(
    @PathVariable prisonNumber: String,
    @RequestParam(required = false) startDate: LocalDate?,
    @RequestParam(required = false) endDate: LocalDate?,
    @RequestParam @Min(1) pageNumber: Int = 1,
    @RequestParam @Min(1) pageSize: Int = 25,
    @RequestParam(required = false) credit: Boolean = false,
    @RequestParam(required = false) debit: Boolean = false,
    @RequestParam(required = false) subAccountReference: String?,
  ): ResponseEntity<PagedPrisonerTransactionResponse> {
    val account = accountService.getAccountByReference(prisonNumber)
    if (account == null) throw CustomException(status = HttpStatus.NOT_FOUND, message = "Account not found")

    var subAccountId: UUID? = null

    if (!subAccountReference.isNullOrBlank()) {
      subAccountId = account.subAccounts.firstOrNull({ acc -> acc.reference == subAccountReference.uppercase() })?.id
        ?: throw CustomException(status = HttpStatus.NOT_FOUND, message = "Sub account not found")
    }
    val transactions = transactionService.getPrisonerTransactionsByAccountId(
      accountId = account.id,
      startDate = startDate,
      endDate = endDate,
      credit = credit,
      debit = debit,
      pageNumber = pageNumber,
      pageSize = pageSize,
      subAccountId = subAccountId,
    )

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
        description = "Bad Gateway - A dependency service is currently unreachable or throwing an error.",
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

  @Operation(
    summary = "Gets the sub account balance of a prisoner",
    description = "Returns the sub account balance of a prisoner",
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Retrieved the sub account balance",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = SubAccountBalanceResponse::class))],
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
        description = "Parent Account or Sub Account Not Found",
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
  @SecurityRequirement(name = "bearer-jwt", scopes = [ROLE_PRISONER_FINANCE__PROFILE__RO])
  @PreAuthorize("hasAnyAuthority('$ROLE_PRISONER_FINANCE__PROFILE__RO')")
  @GetMapping("/prisoners/{prisonNumber}/money/balance/{subAccountReference}")
  fun getSubAccountBalance(
    @PathVariable prisonNumber: String,
    @PathVariable subAccountReference: String,
  ): ResponseEntity<SubAccountBalanceResponse> {
    val account = accountService.getAccountByReference(prisonNumber)
      ?: throw CustomException(status = HttpStatus.NOT_FOUND, message = "Account not found")

    val subAccountId = account.subAccounts.firstOrNull { acc -> acc.reference == subAccountReference }?.id
      ?: throw CustomException(status = HttpStatus.NOT_FOUND, message = "Sub account not found")

    val balance = accountService.getSubAccountBalance(accountUUID = subAccountId)

    return ResponseEntity.ok(balance)
  }
}
