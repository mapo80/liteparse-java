plugins {
    // Publishes staged artifacts to Maven Central (Sonatype). Only used by the
    // release workflow; safe to apply unconditionally.
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
}

group = providers.gradleProperty("group").get()
version = providers.gradleProperty("version").get()

nexusPublishing {
    repositories {
        sonatype {
            // New Sonatype Central OSSRH-compatible staging API.
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(
                uri("https://central.sonatype.com/repository/maven-snapshots/"))
            username.set(providers.environmentVariable("OSSRH_USERNAME").orElse(""))
            password.set(providers.environmentVariable("OSSRH_PASSWORD").orElse(""))
        }
    }
}
