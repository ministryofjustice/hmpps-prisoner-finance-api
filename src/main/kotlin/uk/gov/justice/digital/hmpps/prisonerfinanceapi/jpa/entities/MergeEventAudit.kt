package uk.gov.justice.digital.hmpps.prisonerfinanceapi.jpa.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.Immutable
import java.time.Instant
import java.util.UUID

@Immutable
@Entity
@Table(
  name = "merge_event_audit",
)
data class MergeEventAudit(
  @Id
  @Column(name = "merge_event_audit_id", nullable = false, unique = true)
  val id: UUID = UUID.randomUUID(),

  @Column(name = "surviving_prison_number")
  val survivingPrisonNumber: String,

  @Column(name = "merged_prison_number")
  val mergedPrisonNumber: String,

  @Column(name = "message_received_at")
  val messageReceivedAt: Instant = Instant.now(),
)
