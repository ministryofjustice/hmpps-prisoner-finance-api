package uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.response

class PagedPrisonerTransactionResponse(
  val content: List<PrisonerTransactionResponse>,
  val pageNumber: Int,
  val pageSize: Int,
  val totalElements: Long,
  val totalPages: Int,
  val isLastPage: Boolean,
)
