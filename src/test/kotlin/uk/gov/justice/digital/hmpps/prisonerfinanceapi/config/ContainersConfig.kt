package uk.gov.justice.digital.hmpps.prisonerfinanceapi.config

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.PostgreSQLContainer

@TestConfiguration(proxyBeanMethods = false)
class ContainersConfig {

  @Bean
  @ServiceConnection
  fun postgres(): PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16.15")
}
