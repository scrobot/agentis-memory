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

group = "io.agentis"
version = projectVersion

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaVersion)
    }
}

application {
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
    // TCP server, RESP protocol
    implementation("io.netty:netty-all:$nettyVersion")

    // HNSW vector index (DataStax, Apache 2.0)
    implementation("io.github.jbellis:jvector:$jvectorVersion")

    // ONNX Runtime — inference (Java API, no Panama FFI required for MVP)
    implementation("com.microsoft.onnxruntime:onnxruntime:$onnxruntimeVersion")

    // HuggingFace-compatible tokenizer (DJL)
    implementation("ai.djl.huggingface:tokenizers:$djlTokenizersVersion")

    // Test
    testImplementation(platform("org.junit:junit-bom:$junitBomVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("redis.clients:jedis:$jedisVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs(
        "--enable-preview",
        "--add-modules", "jdk.incubator.vector"
    )
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
                    // Include models/ directory as resources in the binary
                    "-H:IncludeResources=models/.*",
                    "--initialize-at-build-time=io.netty",
                    // Reduce binary size
                    "-O2"
                )
            )
        }
    }
}
