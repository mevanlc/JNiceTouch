import com.vanniktech.maven.publish.DeploymentValidation
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.jvm.tasks.Jar
import org.gradle.plugins.signing.Sign

plugins {
    `java-library`
    id("com.vanniktech.maven.publish") version "0.37.0"
}

group = "io.github.mevanlc"
val releaseVersion = providers.gradleProperty("releaseVersion")
releaseVersion.orNull?.let {
    require(it.matches(Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)"))) {
        "releaseVersion must be X.Y.Z without a prefix, qualifier, or leading zeros"
    }
}
version = releaseVersion.orElse("0.0.0-SNAPSHOT").get()

base {
    archivesName = "jnicetouch"
}

mavenPublishing {
    coordinates(group.toString(), "jnicetouch", version.toString())
    publishToMavenCentral(
        automaticRelease = true,
        validateDeployment = DeploymentValidation.PUBLISHED,
    )
    signAllPublications()
    pom {
        name = "JNiceTouch"
        description = "macOS trackpad input for Swing applications on JDK 22 or newer"
        url = "https://github.com/mevanlc/JNiceTouch"
        inceptionYear = "2026"
        licenses {
            license {
                name = "The Apache License, Version 2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "repo"
            }
        }
        developers {
            developer {
                id = "mevanlc"
                name = "Michael Clark"
                url = "https://github.com/mevanlc"
            }
        }
        scm {
            url = "https://github.com/mevanlc/JNiceTouch"
            connection = "scm:git:https://github.com/mevanlc/JNiceTouch.git"
            developerConnection = "scm:git:ssh://git@github.com/mevanlc/JNiceTouch.git"
        }
    }
}

abstract class VerifyReleasePublication : DefaultTask() {
    @get:Input
    abstract val releaseVersion: Property<String>

    @get:Input
    abstract val macOs: Property<Boolean>

    @get:Input
    abstract val credentialsPresent: MapProperty<String, Boolean>

    @TaskAction
    fun verify() {
        check(macOs.get()) { "Maven Central publication requires macOS" }
        check(releaseVersion.get().isNotBlank()) {
            "Maven Central publication requires an explicit -PreleaseVersion=X.Y.Z"
        }
        val missing = credentialsPresent.get().filterValues { !it }.keys
        check(missing.isEmpty()) {
            "Maven Central publication is missing Gradle properties: ${missing.joinToString()}"
        }
    }
}

val verifyReleasePublication by tasks.registering(VerifyReleasePublication::class) {
    group = "verification"
    description = "Checks release version, platform, and credentials before Central publication."
    releaseVersion.set(providers.gradleProperty("releaseVersion").orElse(""))
    macOs.set(providers.systemProperty("os.name").map { it == "Mac OS X" })
    for (name in listOf(
        "mavenCentralUsername", "mavenCentralPassword",
        "signingInMemoryKey", "signingInMemoryKeyPassword",
    )) {
        credentialsPresent.put(name, providers.gradleProperty(name).map { it.isNotBlank() }.orElse(false))
    }
}

// Guard preparation too: the plugin uploads registered deployments when its build service closes.
tasks.matching { it.name == "prepareMavenCentralPublishing" }.configureEach {
    dependsOn(verifyReleasePublication)
}
tasks.withType<PublishToMavenRepository>().configureEach {
    dependsOn(verifyReleasePublication)
}
tasks.withType<Sign>().configureEach {
    mustRunAfter(verifyReleasePublication)
}

tasks.withType<Jar>().configureEach {
    manifest.attributes("Implementation-Title" to "JNiceTouch", "Implementation-Version" to project.version)
    from(rootProject.layout.projectDirectory.file("LICENSE")) {
        into("META-INF")
    }
}

val nativeSourceDir = rootProject.layout.projectDirectory.dir("native")
val nativeOutputDir = layout.buildDirectory.dir("native")
val nativeLibrary = nativeOutputDir.map { it.file("libjnicetouch.dylib") }
val nativeAbiTest = nativeOutputDir.map { it.file("abi-test") }
val nativeQueueTest = nativeOutputDir.map { it.file("queue-test") }

val prepareNativeOutput by tasks.registering(Exec::class) {
    mustRunAfter(tasks.named("clean"))
    commandLine("mkdir", "-p", nativeOutputDir.get().asFile.absolutePath)
}

val compileNative by tasks.registering(Exec::class) {
    group = "build"
    description = "Builds the universal macOS JNiceTouch native library."
    onlyIf("requires macOS") { System.getProperty("os.name") == "Mac OS X" }
    dependsOn(prepareNativeOutput)

    inputs.file(nativeSourceDir.file("src/main/objective-cpp/JNiceTouch.mm"))
    inputs.file(nativeSourceDir.file("include/jnicetouch.h"))
    outputs.file(nativeLibrary)

    commandLine(
        "xcrun", "clang++",
        "-std=c++20",
        "-fobjc-arc",
        "-fvisibility=hidden",
        "-Wall", "-Wextra", "-Werror",
        "-dynamiclib",
        "-arch", "arm64",
        "-arch", "x86_64",
        "-mmacosx-version-min=12.0",
        "-I${nativeSourceDir.dir("include").asFile.absolutePath}",
        nativeSourceDir.file("src/main/objective-cpp/JNiceTouch.mm").asFile.absolutePath,
        "-framework", "AppKit",
        "-framework", "Foundation",
        "-install_name", "@rpath/libjnicetouch.dylib",
        "-o", nativeLibrary.get().asFile.absolutePath,
    )
}

val compileNativeAbiTest by tasks.registering(Exec::class) {
    group = "verification"
    description = "Compiles the C ABI layout and discovery test."
    onlyIf("requires macOS") { System.getProperty("os.name") == "Mac OS X" }
    dependsOn(compileNative)

    inputs.file(nativeSourceDir.file("src/test/c/abi_test.c"))
    inputs.file(nativeSourceDir.file("include/jnicetouch.h"))
    inputs.file(nativeLibrary)
    outputs.file(nativeAbiTest)

    commandLine(
        "xcrun", "clang",
        "-std=c11",
        "-Wall", "-Wextra", "-Werror",
        "-I${nativeSourceDir.dir("include").asFile.absolutePath}",
        nativeSourceDir.file("src/test/c/abi_test.c").asFile.absolutePath,
        "-L${nativeOutputDir.get().asFile.absolutePath}",
        "-Wl,-rpath,${nativeOutputDir.get().asFile.absolutePath}",
        "-ljnicetouch",
        "-o", nativeAbiTest.get().asFile.absolutePath,
    )
}

val testNativeAbi by tasks.registering(Exec::class) {
    group = "verification"
    description = "Runs the native C ABI test."
    onlyIf("requires macOS") { System.getProperty("os.name") == "Mac OS X" }
    dependsOn(compileNativeAbiTest)
    commandLine(nativeAbiTest.get().asFile.absolutePath)
}

val compileNativeQueueTest by tasks.registering(Exec::class) {
    group = "verification"
    description = "Compiles the native queue and sequence-policy tests."
    onlyIf("requires macOS") { System.getProperty("os.name") == "Mac OS X" }
    dependsOn(prepareNativeOutput)

    inputs.file(nativeSourceDir.file("src/main/objective-cpp/JNiceTouch.mm"))
    inputs.file(nativeSourceDir.file("src/test/objective-cpp/queue_test.mm"))
    inputs.file(nativeSourceDir.file("include/jnicetouch.h"))
    outputs.file(nativeQueueTest)

    commandLine(
        "xcrun", "clang++",
        "-std=c++20",
        "-fobjc-arc",
        "-Wall", "-Wextra", "-Werror",
        "-mmacosx-version-min=12.0",
        "-I${nativeSourceDir.dir("include").asFile.absolutePath}",
        nativeSourceDir.file("src/test/objective-cpp/queue_test.mm").asFile.absolutePath,
        "-framework", "AppKit",
        "-framework", "Foundation",
        "-o", nativeQueueTest.get().asFile.absolutePath,
    )
}

val testNativeQueue by tasks.registering(Exec::class) {
    group = "verification"
    description = "Runs the native queue and sequence-policy tests."
    onlyIf("requires macOS") { System.getProperty("os.name") == "Mac OS X" }
    dependsOn(compileNativeQueueTest)
    commandLine(nativeQueueTest.get().asFile.absolutePath)
}

val testNative by tasks.registering {
    group = "verification"
    description = "Runs every native test."
    dependsOn(testNativeAbi, testNativeQueue)
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(libs.testng)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(22)
    }
    withSourcesJar()
}

// The publishing plugin owns the Javadoc artifact; include it in ordinary builds too.
tasks.named("assemble") {
    dependsOn("plainJavadocJar")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.named<Test>("test") {
    useTestNG()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    dependsOn(testNative)
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(compileNative)
    from(nativeLibrary) {
        into("META-INF/native/macos-universal")
    }
}
