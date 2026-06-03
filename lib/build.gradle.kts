import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    `java-library`
    id("com.gradleup.shadow") version "8.3.5"
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
    useJUnitPlatform {
        // Exclude JUnit tags via `-PexcludeTags=conversion` (the main CI excludes the
        // conversion suite, which needs LibreOffice/ImageMagick — see conversion-tests.yml).
        (findProperty("excludeTags") as String?)
            ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?.forEach { excludeTags(it) }
    }
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

// The generic shadowJar (no natives) would be misleading; we build per-platform bundles instead.
tasks.named<ShadowJar>("shadowJar") { enabled = false }

// ---------------------------------------------------------------------------
// Per-platform self-contained "bundle" jars (GitHub Release distribution).
//
// Each `native-staging/<classifier>/` directory (populated by CI from the
// per-platform build artifacts) yields a fat jar:
//   liteparse-java-bundle-<classifier>-<version>.jar
// containing the Java classes + a relocated copy of Jackson + the native files
// for that platform (under io/liteparse/native/<classifier>/ with a manifest).
// A consumer just drops this single jar on the classpath — no other deps.
// ---------------------------------------------------------------------------
val stagingDir = rootProject.layout.projectDirectory.dir("native-staging").asFile
val bundleJarTasks = mutableListOf<TaskProvider<ShadowJar>>()

if (stagingDir.isDirectory) {
    val platformDirs = stagingDir.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name } ?: emptyList()
    val manifestTasks = linkedMapOf<String, TaskProvider<Task>>()

    platformDirs.forEach { dir ->
        val classifier = dir.name
        manifestTasks[classifier] = tasks.register("nativeManifest-$classifier") {
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
    }

    // Per-platform self-contained bundles.
    platformDirs.forEach { dir ->
        val classifier = dir.name
        val manifestTask = manifestTasks.getValue(classifier)
        val bundle = tasks.register<ShadowJar>("bundleJar-$classifier") {
            group = "build"
            description = "Self-contained jar for $classifier (classes + Jackson + natives)."
            dependsOn(manifestTask)
            archiveBaseName.set("liteparse-java-bundle")
            archiveClassifier.set(classifier)

            from(sourceSets["main"].output)
            configurations = listOf(project.configurations["runtimeClasspath"])
            // Relocate Jackson so the bundle never clashes with a consumer's own copy.
            relocate("com.fasterxml.jackson", "io.liteparse.shaded.jackson")
            mergeServiceFiles()
            // Mirror the excludes the shadow plugin auto-applies only to the `shadowJar` task (a
            // manually registered ShadowJar does not inherit them); otherwise the relocated Jackson
            // module-info survives and misdescribes the jar.
            exclude("module-info.class")
            exclude("META-INF/versions/**/module-info.class")
            exclude("META-INF/INDEX.LIST")
            exclude("META-INF/*.SF")
            exclude("META-INF/*.DSA")
            exclude("META-INF/*.RSA")

            from(dir) { into("io/liteparse/native/$classifier") }
            from(manifestTask.map { it.outputs.files.singleFile }) {
                into("io/liteparse/native/$classifier")
            }
        }
        bundleJarTasks.add(bundle)
    }

    // Single cross-platform bundle: the Java classes + Jackson + native binaries for EVERY platform.
    // liteparse-java selects the right native at runtime, so one download runs everywhere — the same
    // convenience artifact liteparse-markdown ships. Assembled only when all platform staging dirs
    // are present (i.e. in the release workflow's assemble-publish job).
    if (platformDirs.isNotEmpty()) {
        val allBundle = tasks.register<ShadowJar>("bundleJar-all-platforms") {
            group = "build"
            description = "Self-contained jar with native binaries for ALL platforms (single download)."
            archiveBaseName.set("liteparse-java-bundle")
            archiveClassifier.set("all-platforms")

            from(sourceSets["main"].output)
            configurations = listOf(project.configurations["runtimeClasspath"])
            relocate("com.fasterxml.jackson", "io.liteparse.shaded.jackson")
            mergeServiceFiles()
            exclude("module-info.class")
            exclude("META-INF/versions/**/module-info.class")
            exclude("META-INF/INDEX.LIST")
            exclude("META-INF/*.SF")
            exclude("META-INF/*.DSA")
            exclude("META-INF/*.RSA")

            platformDirs.forEach { dir ->
                val classifier = dir.name
                val manifestTask = manifestTasks.getValue(classifier)
                dependsOn(manifestTask)
                from(dir) { into("io/liteparse/native/$classifier") }
                from(manifestTask.map { it.outputs.files.singleFile }) {
                    into("io/liteparse/native/$classifier")
                }
            }
        }
        bundleJarTasks.add(allBundle)
    }
}

tasks.named("assemble") { dependsOn(bundleJarTasks) }
