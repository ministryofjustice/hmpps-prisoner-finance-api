package uk.gov.justice.digital.hmpps.prisonerfinanceapi.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.client.GeneralLedgerApiClient
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.AccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.CreatePostingRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.CreateTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.generalledger.SubAccountResponse
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.request.CreateBatchTransactionFormRequest
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.request.PrisonerPosting
import java.time.Instant
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class BatchTransactionServiceTest {

  @Mock
  private lateinit var generalLedgerApiClient: GeneralLedgerApiClient

  @InjectMocks
  private lateinit var batchTransactionService: BatchTransactionService

  fun createBatchTransactionFormReq(requestPostingType: CreatePostingRequest.Type) = CreateBatchTransactionFormRequest(
    caseloadId = "LEI",
    caseloadSubAccountRef = "1504:DEM",
    postingType = requestPostingType,
    controlAmount = 200L,
    description = "Test",
    prisonNumbersPostings = listOf(
      PrisonerPosting(
        prisonNumber = "A1234AA",
        postingType = requestPostingType.opposite(),
        amount = 100L,
        prisonerSubAccountRef = "CASH",
      ),
      PrisonerPosting(
        prisonNumber = "A1234BB",
        postingType = requestPostingType.opposite(),
        amount = 100L,
        prisonerSubAccountRef = "CASH",
      ),
    ),
  )

  fun createAccountResponseFrom(prisonerPosting: PrisonerPosting, extraSubAccountReferences: List<String> = emptyList()): AccountResponse {
    val listOfSubAccounts = mutableListOf<SubAccountResponse>(
      SubAccountResponse(
        id = UUID.randomUUID(),
        reference = prisonerPosting.prisonerSubAccountRef,
        parentAccountId = UUID.randomUUID(),
        createdBy = "test",
        createdAt = Instant.now(),
      ),
    )

    for ((i, ref) in extraSubAccountReferences.withIndex()) {
      val subAccountResponse = SubAccountResponse(
        id = UUID.randomUUID(),
        reference = ref,
        parentAccountId = UUID.randomUUID(),
        createdBy = "test",
        createdAt = Instant.now(),
      )

      if (i % 2 == 0) {
        listOfSubAccounts.add(
          subAccountResponse,
        )
      } else {
        listOfSubAccounts.addFirst(
          subAccountResponse,
        )
      }
    }

    return AccountResponse(
      id = UUID.randomUUID(),
      reference = prisonerPosting.prisonNumber,
      type = AccountResponse.Type.PRISONER,
      createdBy = "Test User",
      createdAt = Instant.now(),
      subAccounts = listOfSubAccounts,
    )
  }

  fun buildAccountResponsesFromBatchTransactionRequest(request: CreateBatchTransactionFormRequest): List<AccountResponse> {
    val accountResponses = request.prisonNumbersPostings.map { createAccountResponseFrom(it) }.toMutableList()
    accountResponses.add(
      AccountResponse(
        id = UUID.randomUUID(),
        reference = request.caseloadId,
        type = AccountResponse.Type.PRISON,
        createdBy = "Test User",
        createdAt = Instant.now(),
        subAccounts = listOf(
          SubAccountResponse(
            id = UUID.randomUUID(),
            reference = request.caseloadSubAccountRef,
            parentAccountId = UUID.randomUUID(),
            createdBy = "test",
            createdAt = Instant.now(),
          ),
        ),
      ),
    )
    return accountResponses.toList()
  }

  fun CreatePostingRequest.Type.opposite() = when (this) {
    CreatePostingRequest.Type.CR -> CreatePostingRequest.Type.DR
    CreatePostingRequest.Type.DR -> CreatePostingRequest.Type.CR
  }

  fun verifyTransaction(request: CreateBatchTransactionFormRequest, references: List<String>, accountResponses: List<AccountResponse>): Pair<CreateTransactionRequest, Map<String, UUID>> {
    val createTransactionRequestCaptor = argumentCaptor<CreateTransactionRequest>()

    verify(generalLedgerApiClient, times(1)).postTransaction(
      idempotencyKey = any(),
      createTransactionRequest = createTransactionRequestCaptor.capture(),
    )
    val createTransactionRequest = createTransactionRequestCaptor.firstValue
    assertThat(createTransactionRequest.reference).isEqualTo(request.description)
    assertThat(createTransactionRequest.description).isEqualTo(request.description)
    assertThat(createTransactionRequest.amount).isEqualTo(request.controlAmount)
    assertThat(createTransactionRequest.entrySequence).isEqualTo(1)

    val postings = createTransactionRequest.postings

    assertThat(postings).hasSize(references.size)

    // TODO: change from first to find
    val accountRefToSubAccountId = accountResponses.associate { acc -> acc.reference to acc.subAccounts.first().id }
    val postingSubAccountIds = postings.map { it.subAccountId }
    val referencesSubAccountsIds = references.map { accountRefToSubAccountId[it] }

    assertThat(postingSubAccountIds).hasSize(referencesSubAccountsIds.size)
    assertThat(postingSubAccountIds).containsExactlyInAnyOrderElementsOf(referencesSubAccountsIds)

    val (prisonPostings, prisonersPosting) = postings.partition { it.subAccountId == accountRefToSubAccountId[request.caseloadId] }

    assertThat(prisonPostings).hasSize(1)
    assertThat(prisonPostings.first().type).isEqualTo(request.postingType)

    assertThat(prisonersPosting).hasSize(references.size - 1)
    assertTrue(prisonersPosting.all { it.type == request.postingType.opposite() })

    assertTrue(prisonersPosting.sumOf { it.amount } == request.controlAmount)
    assertThat(prisonPostings.first().amount).isEqualTo(request.controlAmount)

    return Pair(createTransactionRequest, accountRefToSubAccountId)
  }

  @Test
  fun `Should get accounts from GL for prisoners references`() {
    val request = createBatchTransactionFormReq(CreatePostingRequest.Type.DR)
    val references = request.prisonNumbersPostings.map { it.prisonNumber }.toMutableList()
    references.add(request.caseloadId)

    val accountResponses = buildAccountResponsesFromBatchTransactionRequest(request)

    whenever(generalLedgerApiClient.searchAccountsByReferences(references)).thenReturn(
      accountResponses,
    )

    batchTransactionService.createBatchTransaction(request)

    verify(generalLedgerApiClient, times(1)).searchAccountsByReferences(references)
  }

  @Test
  fun `Should create one to many transaction with all accounts that have matches`() {
    val request = createBatchTransactionFormReq(CreatePostingRequest.Type.DR)

    val references = request.prisonNumbersPostings.map { it.prisonNumber }.toMutableList()
    references.add(request.caseloadId)

    val accountResponses = buildAccountResponsesFromBatchTransactionRequest(request)

    whenever(generalLedgerApiClient.searchAccountsByReferences(references)).thenReturn(
      accountResponses,
    )

    batchTransactionService.createBatchTransaction(request = request)

    verifyTransaction(request = request, references = references, accountResponses = accountResponses)
  }

  @ParameterizedTest()
  @CsvSource("DR, 1", "CR, 3")
  fun `Should create one to many transaction with the correct entrySequence order`(
    postingType: CreatePostingRequest.Type,
    expectedPrisonPostingEntrySequence: Long,
  ) {
    val request = createBatchTransactionFormReq(postingType)

    val references = request.prisonNumbersPostings.map { it.prisonNumber }.toMutableList()
    references.add(request.caseloadId)

    val accountResponses = buildAccountResponsesFromBatchTransactionRequest(request)

    whenever(generalLedgerApiClient.searchAccountsByReferences(references)).thenReturn(
      accountResponses,
    )

    batchTransactionService.createBatchTransaction(request = request)

    val (createTransactionRequest, accountRefToSubAccountId) =
      verifyTransaction(request = request, references = references, accountResponses = accountResponses)

    val prisonPosting = createTransactionRequest.postings.first { it.subAccountId == accountRefToSubAccountId[request.caseloadId] }

    assertThat(prisonPosting.entrySequence).isEqualTo(expectedPrisonPostingEntrySequence)

    assertThat(createTransactionRequest.postings.map { it.entrySequence }.sorted()).containsExactly(1L, 2L, 3L)
  }

  @Test
  fun `Should use correct subAccount when multiple exist in general ledger`() {
    val request = createBatchTransactionFormReq(CreatePostingRequest.Type.DR)

    val references = request.prisonNumbersPostings.map { it.prisonNumber }.toMutableList()

    references.add(request.caseloadId)

    val prisonAccountId = UUID.randomUUID()

    val correctPrisonSubAccountId = UUID.randomUUID()

    val prisonAccountResponse = AccountResponse(
      id = prisonAccountId,
      reference = request.caseloadId,
      createdBy = "TEST",
      createdAt = Instant.now(),
      type = AccountResponse.Type.PRISON,
      subAccounts = listOf(
        SubAccountResponse(
          id = UUID.randomUUID(),
          reference = "SOME_ACCOUNT",
          parentAccountId = prisonAccountId,
          createdBy = "TEST",
          createdAt = Instant.now(),
        ),
        SubAccountResponse(
          id = correctPrisonSubAccountId,
          reference = request.caseloadSubAccountRef,
          parentAccountId = prisonAccountId,
          createdBy = "TEST",
          createdAt = Instant.now(),
        ),
      ),
    )

    val accountResponses = request.prisonNumbersPostings.map { createAccountResponseFrom(it, listOf("SPENDS")) }.toMutableList()

    accountResponses.add(prisonAccountResponse)

    whenever(generalLedgerApiClient.searchAccountsByReferences(references)).thenReturn(
      accountResponses,
    )

    batchTransactionService.createBatchTransaction(request = request)

    val createTransactionRequestCaptor = argumentCaptor<CreateTransactionRequest>()

    verify(generalLedgerApiClient, times(1)).postTransaction(
      idempotencyKey = any(),
      createTransactionRequest = createTransactionRequestCaptor.capture(),
    )
    val createTransactionRequest = createTransactionRequestCaptor.firstValue
    assertThat(createTransactionRequest.reference).isEqualTo(request.description)
    assertThat(createTransactionRequest.description).isEqualTo(request.description)
    assertThat(createTransactionRequest.amount).isEqualTo(request.controlAmount)
    assertThat(createTransactionRequest.entrySequence).isEqualTo(1)

    val postings = createTransactionRequest.postings

    assertThat(postings).hasSize(references.size)

    val postingSubAccountIds = postings.map { it.subAccountId }

    val correctSubAccountIds = mutableListOf<UUID>(correctPrisonSubAccountId)

    accountResponses.forEach { accountResponse ->
      accountResponse.subAccounts.forEach { subAccount ->
        if (subAccount.reference == "CASH") {
          correctSubAccountIds.add(subAccount.id)
        }
      }
    }

    assertThat(correctSubAccountIds).hasSize(3)

    assertThat(postingSubAccountIds).containsExactlyInAnyOrderElementsOf(correctSubAccountIds)
  }
}
