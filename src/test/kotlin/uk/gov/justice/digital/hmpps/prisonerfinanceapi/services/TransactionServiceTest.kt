package uk.gov.justice.digital.hmpps.prisonerfinanceapi.services

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class TransactionServiceTest() {

  private val transactionService = TransactionService()

  @Nested
  inner class TransformForUI {

    @Test
    fun `Should return empty list if given an empty list`() {
      val emptyList = emptyList<Object>()
      val transformedList = transactionService.transformForUI(emptyList)
      assertThat(transformedList).isEmpty()
    }

    // TO-DO
    // 1 - Call the GL for transactions for a particular prisoner, response
    // 2 - Transform the GL response
    // 3 - Return transformed response

    // GL Transaction for an account response
    // [
    //  { 
    //    ID: '<uuid>',
    //    description: '<description>',
    //    timestamp: '<timestamp>'
    //    postings: [
    //      {
    //        ID: '<UUID>'
    //        Type: '<CR/DR>'
    //        Amount: <amount>
    //        SubAccount: {
    //          ID: '<UUID>'
    //          Reference: '<sub-account-ref>'
    //          Account: {
    //             ID: '<UUID'>
    //             Reference: '<account-ref>'
    //             Type: '<Prison/Prisoner>'
    //          }
    //        }
    //      }
    //    ] 
    //  },
    //]

   // {
    //  date: '2026-02-04', 
    //  description: 'Advance',
    //  credit: 5.0,
    //  debit: 0, 
    //  location: 'LEI',
    //  AccountType: 'Spends',


      @Test
    fun `should return a transaction when given a valid prisoner ID`(){
      val prisonerId = "AB1234AA"


    }
  }
}
