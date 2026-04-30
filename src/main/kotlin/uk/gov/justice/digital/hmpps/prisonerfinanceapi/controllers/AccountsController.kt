package uk.gov.justice.digital.hmpps.prisonerfinanceapi.controllers

import io.swagger.v3.oas.annotations.Operation
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
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.AccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.services.AccountService
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

@Tag(name = "Accounts controller")
@RestController
class AccountsController(val accountService: AccountService) {

  @Operation(
    summary = "Get an account by reference",
    description = "Returns an account by reference",
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Retrieved the account",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = AccountResponse::class))],
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
  @GetMapping("/accounts/{accountReference}")
  fun getAccountByReference(
    @PathVariable accountReference: String,
  ): ResponseEntity<AccountResponse> {
    val account = accountService.getAccountByReference(accountReference)
      ?: throw CustomException(status = HttpStatus.NOT_FOUND, message = "Account not found")

    return ResponseEntity.ok(account)
  }
}
