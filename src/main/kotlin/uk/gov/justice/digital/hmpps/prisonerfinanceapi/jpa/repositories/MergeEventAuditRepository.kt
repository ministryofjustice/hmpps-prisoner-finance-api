package uk.gov.justice.digital.hmpps.prisonerfinanceapi.jpa.repositories

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import uk.gov.justice.digital.hmpps.prisonerfinanceapi.jpa.entities.MergeEventAudit
import java.util.UUID

@Repository
interface MergeEventAuditRepository : JpaRepository<MergeEventAudit, UUID>
