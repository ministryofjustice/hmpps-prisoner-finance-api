package uk.gov.justice.digital.hmpps.prisonerfinanceapi

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PrisonerFinanceApi

fun main(args: Array<String>) {
  runApplication<PrisonerFinanceApi>(*args)
}
