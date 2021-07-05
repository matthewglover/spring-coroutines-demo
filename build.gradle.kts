import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.tasks.bundling.BootBuildImage

plugins {
  id("org.springframework.boot") version "2.5.2"
  id("io.spring.dependency-management") version "1.0.11.RELEASE"
  kotlin("jvm") version "1.5.10"
  kotlin("plugin.spring") version "1.5.10"
  id("org.springframework.experimental.aot") version "0.10.1"
}

tasks.withType<Wrapper> { gradleVersion = "7.1" }

group = "com.example"

version = "0.0.1-SNAPSHOT"

java.sourceCompatibility = JavaVersion.VERSION_11

repositories {
  maven { url = uri("https://repo.spring.io/release") }
  mavenCentral()
}

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-log4j2")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
  implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
  implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.4")
  implementation("io.r2dbc:r2dbc-postgresql")
  implementation("am.ik.yavi:yavi:0.5.0")
  runtimeOnly("org.postgresql:postgresql")
//  testImplementation("org.springframework.boot:spring-boot-starter-test")
//  testImplementation("org.mockito.kotlin:mockito-kotlin:3.2.0")

  testImplementation("org.springframework.boot:spring-boot-starter-test") {
    exclude(module = "junit")
    exclude(module = "mockito-core")
  }
  testImplementation("io.projectreactor:reactor-test")
  testImplementation("org.junit.jupiter:junit-jupiter-api")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
  testImplementation("io.mockk:mockk:1.12.0")
}

tasks.withType<KotlinCompile> {
  kotlinOptions {
    freeCompilerArgs = listOf("-Xjsr305=strict")
    jvmTarget = "11"
  }
}

tasks.withType<Test> { useJUnitPlatform() }

tasks.withType<BootBuildImage> {
  builder = "paketobuildpacks/builder:tiny"
  environment = mapOf("BP_NATIVE_IMAGE" to "true")
}

configurations.all {
  //   spring-boot-starter-logging module causes a conflict with spring-boot-starter-log4j2
  //   As spring-boot-starter-logging is included by multiple dependencies:
  //   spring-boot-starter-webflux, spring-boot-starter-actuator, spring-boot-starter-validation
  //   we globally exclude it here, rather than in each dependency
  exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging")
}
