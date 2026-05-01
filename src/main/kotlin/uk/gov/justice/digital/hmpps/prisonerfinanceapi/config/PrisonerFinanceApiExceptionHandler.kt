package uk.gov.justice.digital.hmpps.prisonerfinanceapi.config

import jakarta.validation.ValidationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.FORBIDDEN
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.CustomException
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

@RestControllerAdvice
class PrisonerFinanceApiExceptionHandler {

  private val envIsProd = System.getenv("ENV_STRING") == "prod"

  @ExceptionHandler(ValidationException::class, HttpMessageNotReadableException::class)
  fun handleValidationException(e: Exception): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(BAD_REQUEST)
    .body(
      ErrorResponse(
        status = BAD_REQUEST,
        userMessage = "Validation failure: ${e.message}",
        developerMessage = if (envIsProd) null else e.message,
      ),
    ).also { log.info("Validation exception: {}", e.message) }

  @ExceptionHandler(NoResourceFoundException::class)
  fun handleNoResourceFoundException(e: NoResourceFoundException): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(NOT_FOUND)
    .body(
      ErrorResponse(
        status = NOT_FOUND,
        userMessage = "No resource found failure: ${e.message}",
        developerMessage = if (envIsProd) null else e.message,
      ),
    ).also { log.info("No resource found exception: {}", e.message) }

  @ExceptionHandler(AccessDeniedException::class)
  fun handleAccessDeniedException(e: AccessDeniedException): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(FORBIDDEN)
    .body(
      ErrorResponse(
        status = FORBIDDEN,
        userMessage = "Forbidden: ${e.message}",
        developerMessage = if (envIsProd) null else e.message,
      ),
    ).also { log.debug("Forbidden (403) returned: {}", e.message) }

  @ExceptionHandler(Exception::class)
  fun handleException(e: Exception): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(INTERNAL_SERVER_ERROR)
    .body(
      ErrorResponse(
        status = INTERNAL_SERVER_ERROR,
        userMessage = "Unexpected error: ${e.message}",
        developerMessage = if (envIsProd) null else e.message,
      ),
    ).also { log.error("Unexpected exception", e) }

  @ExceptionHandler(MethodArgumentTypeMismatchException::class)
  fun handleMethodArgumentTypeMismatchException(e: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> {
    val paramName = e.parameter.parameterName
    val requiredType = e.parameter.parameterType.simpleName

    val userMessage = "Parameter '$paramName' must be of type $requiredType"

    return ResponseEntity
      .status(BAD_REQUEST)
      .body(
        ErrorResponse(
          status = BAD_REQUEST,
          userMessage = userMessage,
          developerMessage = if (envIsProd) null else e.message,
        ),
      ).also { log.info("MethodArgumentTypeMismatchException: {}", e.message) }
  }

  @ExceptionHandler(CustomException::class)
  fun handleCustomException(e: CustomException): ResponseEntity<ErrorResponse> = ResponseEntity
    .status(e.status)
    .body(
      ErrorResponse(
        status = e.status.value(),
        userMessage = e.message,
        developerMessage = if (envIsProd) null else e.cause?.message,
      ),
    ).also { log.info("CustomExceptionThrown: ${e.cause?.message}") }

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
