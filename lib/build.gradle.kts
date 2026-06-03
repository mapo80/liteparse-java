plugins {
    `java-library`
    `maven-publish`
    signing
}

val moduleArtifactId = "liteparse-java"

group = rootProject.group
version = rootProject.version

base { archivesName.set(moduleArtifactId) }

java {
    // Target Java 17 bytecode; compiles on any JDK >= 17 (CI uses 17, local may be newer).
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

repositories { mavenCentral() }

dependencies {
    api("com.fasterxml.jackson.core:jackson-databind:2.17.2")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    // For local dev / CI: point at a directory that already contains the native
    // library + libpdfium (+ tessdata). Passed via `-PnativeDir=...`.
    (findProperty("nativeDir") as String?)?.let { systemProperty("liteparse.native.dir", it) }
    testLogging { events("passed", "skipped", "failed") }
}

// Run the bundled CLI for manual testing:
//   ./gradlew :lib:runCli -PnativeDir=<dir> -PcliArgs="parse file.pdf --text"
tasks.register<JavaExec>("runCli") {
    group = "application"
    description = "Run the LiteParse CLI (io.liteparse.cli.Main)."
    mainClass.set("io.liteparse.cli.Main")
    classpath = sourceSets["main"].runtimeClasspath
    (findProperty("nativeDir") as String?)?.let { systemProperty("liteparse.native.dir", it) }
    val raw = (findProperty("cliArgs") as String?) ?: ""
    args = if (raw.isBlank()) emptyList() else raw.trim().split(Regex("\\s+"))
}

// ---------------------------------------------------------------------------
// Per-platform native jars (Maven classifiers).
//
// Each `native-staging/<classifier>/` directory (populated by CI from the
// per-platform build artifacts) becomes a `liteparse-java-<classifier>.jar`
// containing `io/liteparse/native/<classifier>/**` plus a `manifest` file
// listing the bundled files (read by NativeLoader at runtime).
// ---------------------------------------------------------------------------
val stagingDir = rootProject.layout.projectDirectory.dir("native-staging").asFile
val nativeJarTasks = mutableListOf<TaskProvider<Jar>>()

if (stagingDir.isDirectory) {
    stagingDir.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }?.forEach { dir ->
        val classifier = dir.name

        val manifestTask = tasks.register("nativeManifest-$classifier") {
            val outFile = layout.buildDirectory.file("native-manifests/$classifier/manifest")
            outputs.file(outFile)
            doLast {
                val names = dir.listFiles()
                    ?.filter { it.isFile }
                    ?.map { it.name }
                    ?.sorted()
                    ?: emptyList()
                val f = outFile.get().asFile
                f.parentFile.mkdirs()
                f.writeText(names.joinToString("\n"))
            }
        }

        val jarTask = tasks.register<Jar>("nativeJar-$classifier") {
            dependsOn(manifestTask)
            archiveBaseName.set(moduleArtifactId)
            archiveClassifier.set(classifier)
            into("io/liteparse/native/$classifier") {
                from(dir)
                from(manifestTask.map { it.outputs.files.singleFile })
            }
        }
        nativeJarTasks.add(jarTask)
    }
}

tasks.named("assemble") { dependsOn(nativeJarTasks) }

// ---------------------------------------------------------------------------
// Publishing
// ---------------------------------------------------------------------------
publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = moduleArtifactId
            from(components["java"])
            nativeJarTasks.forEach { artifact(it) }

            pom {
                name.set(providers.gradleProperty("pomName"))
                description.set(providers.gradleProperty("pomDescription"))
                url.set(providers.gradleProperty("pomUrl"))
                licenses {
                    license {
                        name.set(providers.gradleProperty("pomLicenseName"))
                        url.set(providers.gradleProperty("pomLicenseUrl"))
                    }
                }
                developers {
                    developer {
                        id.set("liteparse-java")
                        name.set("LiteParse Java contributors")
                    }
                }
                scm {
                    url.set(providers.gradleProperty("pomScmUrl"))
                    connection.set(providers.gradleProperty("pomScmUrl").map { "scm:git:$it.git" })
                }
            }
        }
    }
}

signing {
    val key = providers.environmentVariable("SIGNING_KEY").orNull
    val password = providers.environmentVariable("SIGNING_PASSWORD").orNull
    isRequired = key != null && key.isNotBlank()
    if (isRequired) {
        useInMemoryPgpKeys(key, password)
        sign(publishing.publications["maven"])
    }
}
