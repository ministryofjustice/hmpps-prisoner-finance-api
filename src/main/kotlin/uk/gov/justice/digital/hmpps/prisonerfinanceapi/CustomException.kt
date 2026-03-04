package uk.gov.justice.digital.hmpps.prisonerfinanceapi

import org.springframework.http.HttpStatusCode

class CustomException constructor(message: String, val status: HttpStatusCode, override val cause: Exception) : Exception(message)
