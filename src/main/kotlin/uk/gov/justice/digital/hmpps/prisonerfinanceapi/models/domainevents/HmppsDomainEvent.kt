package uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.domainevents

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
open class HmppsDomainEvent(
  @JsonProperty("eventType") open val eventType: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CprPersonCreated(
  @JsonProperty("eventType")
  override val eventType: String,
  @JsonProperty("version")
  val version: Int = 1,
  @JsonProperty("occurredAt")
  val occurredAt: String,
  @JsonProperty("description")
  val description: String,
  @JsonProperty("detailUrl")
  val detailUrl: String,
  @JsonProperty("personReference")
  val personReference: PersonReference,
) : HmppsDomainEvent(eventType)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PersonReference(
  @JsonProperty("identifiers")
  val identifiers: List<PersonIdentifier>? = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PersonIdentifier(
  @JsonProperty("type")
  val type: String,
  @JsonProperty("value")
  val value: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Event(
  @JsonProperty("Message")
  val message: String,
)
