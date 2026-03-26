plugins {
    java
    application
    id("org.graalvm.buildtools.native") version "0.10.4"
}

val nettyVersion: String by project
val jvectorVersion: String by project
val onnxruntimeVersion: String by project
val djlTokenizersVersion: String by project
val junitBomVersion: String by project
val jedisVersion: String by project
val javaVersion: String by project
val projectVersion: String by project
val slf4jVersion: String by project
val logbackVersion: String by project
val avajeInjectVersion: String by project
val jakartaInjectVersion: String by project
val awaitilityVersion: String by project
val testcontainersVersion: String by project

group = "io.agentis"
version = projectVersion

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaVersion)
    }
}

application {
    applicationName = "agentis-memory"
    mainClass = "io.agentis.memory.AgentisMemory"
    // Enable Java Vector API (SIMD) and Panama FFI
    applicationDefaultJvmArgs = listOf(
        "--enable-preview",
        "--add-modules", "jdk.incubator.vector"
    )
}

repositories {
    mavenCentral()
}

dependencies {
    // Implementation
    implementation("io.netty:netty-all:$nettyVersion")
    implementation("io.github.jbellis:jvector:$jvectorVersion")
    implementation("com.microsoft.onnxruntime:onnxruntime:$onnxruntimeVersion")
    implementation("ai.djl.huggingface:tokenizers:$djlTokenizersVersion")
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("io.avaje:avaje-inject:$avajeInjectVersion")
    implementation("jakarta.inject:jakarta.inject-api:$jakartaInjectVersion")

    // GraalVM SDK for native-image Feature class
    // Version must match the GraalVM JDK installed (Java 26 → GraalVM 25.x)
    compileOnly("org.graalvm.sdk:nativeimage:24.2.1") {
        // Only needed at compile time; native-image provides this at build time
        isTransitive = false
    }

    // Annotation Processor
    annotationProcessor("io.avaje:avaje-inject-generator:$avajeInjectVersion")

    // Test Implementation
    testImplementation(platform("org.junit:junit-bom:$junitBomVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("redis.clients:jedis:$jedisVersion")
    testImplementation("org.awaitility:awaitility:$awaitilityVersion")
    testImplementation("org.testcontainers:testcontainers:$testcontainersVersion")
    testImplementation("org.testcontainers:junit-jupiter:$testcontainersVersion")

    // Test Runtime Only
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform {
        // Exclude Docker-based tests from the default test run.
        // Run `./gradlew integrationTest` to include them.
        excludeTags("docker")
    }
    jvmArgs(
        "--enable-preview",
        "--add-modules", "jdk.incubator.vector"
    )
}

tasks.register<Test>("integrationTest") {
    description = "Runs integration tests that require a running Docker daemon."
    group = "verification"
    useJUnitPlatform {
        includeTags("docker")
    }
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    jvmArgs(
        "--enable-preview",
        "--add-modules", "jdk.incubator.vector"
    )
    // Give Docker container enough time to build + start
    systemProperty("testcontainers.reuse.enable", "false")
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = true
    }
    // Depend on compileTestJava so the test classes themselves are compiled before the task runs.
    // The application under test is built inside the Docker image by the Dockerfile (installDist),
    // so local build output is NOT used by the container — only test bytecode is needed here.
    dependsOn(tasks.named("compileTestJava"))
    val testTask = tasks.test.get()
    testClassesDirs = testTask.testClassesDirs
    classpath = testTask.classpath
    // Run tests in the order they were provided, and only run the ones tagged with @Tag("docker")
}

tasks.compileJava {
    options.compilerArgs.addAll(listOf("--enable-preview", "--release", javaVersion))
}

tasks.compileTestJava {
    options.compilerArgs.addAll(listOf("--enable-preview", "--release", javaVersion))
}

graalvmNative {
    binaries {
        named("main") {
            imageName = "agentis-memory"
            mainClass = "io.agentis.memory.AgentisMemory"
            buildArgs.addAll(
                listOf(
                    "--enable-preview",
                    "--add-modules=jdk.incubator.vector",
                    "-H:+UnlockExperimentalVMOptions",
                    "--no-fallback",

                    // ── Resources to bundle in the binary ──
                    "-H:IncludeResources=models/.*",
                    "-H:IncludeResources=logback.xml",

                    // ── Netty: most classes need runtime init due to static native/unsafe access ──
                    "--initialize-at-run-time=io.netty",

                    // ── ONNX Runtime: JNI-heavy, must init at runtime ──
                    "--initialize-at-run-time=ai.onnxruntime",

                    // ── DJL Tokenizers: loads native lib at init ──
                    "--initialize-at-run-time=ai.djl",

                    // ── jvector: uses Panama Vector API, runtime init ──
                    "--initialize-at-run-time=io.github.jbellis.jvector",

                    // ── Logback: runtime init to avoid build-time logging context issues ──
                    "--initialize-at-run-time=ch.qos.logback",

                    // ── Allow incomplete classpath (some transitive deps may be absent) ──
                    "--allow-incomplete-classpath",

                    // ── Reduce binary size ──
                    "-O2"
                )
            )
        }
    }
}
