import com.fasterxml.jackson.databind.ObjectMapper
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.tasks.KtLintCheckTask
import org.jlleitschuh.gradle.ktlint.tasks.KtLintFormatTask
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import java.net.URI
import java.nio.file.Files

plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "10.0.4"
  kotlin("plugin.spring") version "2.3.10"
  id("org.openapi.generator") version "7.19.0"
}

dependencies {
  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:2.0.1")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-webclient")
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.1")

  testImplementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter-test:2.0.1")
  testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
  testImplementation("org.wiremock:wiremock-standalone:3.13.2")
  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.38") {
    exclude(group = "io.swagger.core.v3")
  }
}

kotlin {
  jvmToolchain(25)
  compilerOptions {
    freeCompilerArgs.addAll("-Xannotation-default-target=param-property")
  }
}

// ==============================================================================
// OPEN API GENERATION CONFIGURATION
// ==============================================================================

val apiSpecs = mapOf(
  "generalledger" to "https://prisoner-finance-general-ledger-api-dev.hmpps.service.justice.gov.uk/v3/api-docs",
)

apiSpecs.forEach { (name, url) ->

  tasks.register("write${name.replaceFirstChar { it.titlecase() }}Json") {
    group = "openapi tools"
    description = "Downloads the $name API specification"

    doLast {
      val destDir = file("$rootDir/openapi-specs")
      if (!destDir.exists()) destDir.mkdirs()
      val destFile = file("$destDir/$name.json")

      println("Downloading $name API spec from $url...")

      val json = URI.create(url).toURL().readText()
      val formattedJson = ObjectMapper().let { mapper ->
        mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapper.readTree(json))
      }
      Files.write(destFile.toPath(), formattedJson.toByteArray())

      println("Saved to $destFile")
    }
  }

  val generateTask = tasks.register<GenerateTask>("build${name.replaceFirstChar { it.titlecase() }}ApiClient") {
    group = "openapi tools"
    description = "Generates Kotlin client and models for $name"

    generatorName.set("kotlin")
    library.set("jvm-spring-webclient")

    inputSpec.set("$rootDir/openapi-specs/$name.json")
    outputDir.set(layout.buildDirectory.dir("generated/openapi").get().asFile.path)

    modelPackage.set("uk.gov.justice.digital.hmpps.prisonerfinanceapi.models.$name")
    apiPackage.set("uk.gov.justice.digital.hmpps.prisonerfinanceapi.clients.$name")

    configOptions.set(
      mapOf(
        "serializationLibrary" to "jackson",
        "dateLibrary" to "java8",
        "enumPropertyNaming" to "original",
        "modelMutable" to "false",
        "useSpringBoot3" to "true",
        "useTags" to "true",
      ),
    )

    typeMappings.set(
      mapOf(
        "OffsetDateTime" to "java.time.Instant",
        "DateTime" to "java.time.Instant",
      ),
    )
    importMappings.set(
      mapOf(
        "java.time.LocalDateTime" to "java.time.Instant",
      ),
    )

    globalProperties.set(
      mapOf(
        "models" to "",
        "apis" to "",
        "supportingFiles" to "",
        "modelDocs" to "false",
        "modelTests" to "false",
        "apiDocs" to "false",
        "apiTests" to "false",
      ),
    )

    doFirst {
      val dir = file(outputDir.get())
      if (dir.exists()) {
        dir.deleteRecursively()
      }
    }
  }

  tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    dependsOn(generateTask)
  }

  tasks.withType<KtLintCheckTask> {
    mustRunAfter(generateTask)
  }
  tasks.withType<KtLintFormatTask> {
    mustRunAfter(generateTask)
  }
}

sourceSets {
  main {
    java {
      srcDir(layout.buildDirectory.dir("generated/openapi/src/main/kotlin"))
    }
  }
}

configure<KtlintExtension> {
  filter {
    exclude {
      it.file.path.contains("generated/")
    }
  }
}

// ==============================================================================
// TEST TASKS
// ==============================================================================

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25
  }
}
