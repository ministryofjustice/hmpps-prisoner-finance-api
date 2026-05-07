package uk.gov.justice.digital.hmpps.prisonerfinanceapi.utils

import java.math.BigDecimal
import java.math.RoundingMode

fun BigDecimal.toPence(): Long = this.setScale(2, RoundingMode.UNNECESSARY)
  .movePointRight(2)
  .toLong()
