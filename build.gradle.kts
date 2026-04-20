plugins {
	java
	id("org.springframework.boot") version "4.0.5"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	// Spring web, with in-built web container
	implementation("org.springframework.boot:spring-boot-starter-webmvc")

	// Thymeleaf — server-side HTML templates (login.html, token.html)
	implementation("org.springframework.boot:spring-boot-starter-thymeleaf")

	// WebJars — serves Bootstrap CSS/JS directly from the JAR instead of a CDN.
	// Avoids CDN latency, regional blocks, and external network dependency.
	implementation("org.webjars:bootstrap:5.3.3")
	implementation("org.webjars:webjars-locator-lite:1.0.0")

	// database
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	runtimeOnly("org.postgresql:postgresql")

	// rate limiter (in-memory JVM)
	implementation("com.bucket4j:bucket4j-core:8.10.1")

	// security
	implementation("org.springframework.boot:spring-boot-starter-security")

	// JWT
	implementation("io.jsonwebtoken:jjwt-api:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

	// parameter validation
	implementation("org.springframework.boot:spring-boot-starter-validation")

	// DB schema migrations
	implementation("org.flywaydb:flyway-core")
	implementation("org.flywaydb:flyway-database-postgresql")

	// Cache (in-memory)
	implementation("org.springframework.boot:spring-boot-starter-cache")
	implementation("com.github.ben-manes.caffeine:caffeine")

	// Server health monitor
	implementation("org.springframework.boot:spring-boot-starter-actuator")

	// open api doc generation
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.8")
}

tasks.withType<Test> {
	useJUnitPlatform()
}
