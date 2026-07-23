plugins {
    `java-library`
}

group = "io.github.mevanlc"
version = "0.1.0-SNAPSHOT"

base {
    archivesName = "jnicetouch"
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
    withJavadocJar()
    withSourcesJar()
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
