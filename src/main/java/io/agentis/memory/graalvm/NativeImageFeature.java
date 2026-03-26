package io.agentis.memory.graalvm;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;

/**
 * GraalVM native-image Feature that configures runtime class initialization
 * for libraries that cannot be safely initialized at build time.
 *
 * <p>Key issues addressed:
 * <ul>
 *   <li>ONNX Runtime loads native shared libraries (.dylib/.so) via JNI at startup</li>
 *   <li>DJL Tokenizers loads native shared libraries via JNI at startup</li>
 *   <li>jvector uses the Panama Vector API which must be detected at runtime</li>
 *   <li>Logback configures itself from XML at startup and maintains static context</li>
 * </ul>
 *
 * <p>Registered as a service via META-INF/services/org.graalvm.nativeimage.hosted.Feature
 */
public class NativeImageFeature implements Feature {

    @Override
    public String getDescription() {
        return "Agentis Memory native-image configuration";
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        // ONNX Runtime: JNI-based, loads native .dylib/.so at static init
        RuntimeClassInitialization.initializeAtRunTime("ai.onnxruntime");

        // DJL Tokenizers: JNI-based, loads native .dylib/.so at static init
        RuntimeClassInitialization.initializeAtRunTime("ai.djl");

        // jvector: uses Panama Vector API, detects capabilities at init
        RuntimeClassInitialization.initializeAtRunTime("io.github.jbellis.jvector");

        // Logback: static LoggerContext, XML config parsing at init
        RuntimeClassInitialization.initializeAtRunTime("ch.qos.logback");

        // SLF4J static binding
        RuntimeClassInitialization.initializeAtRunTime("org.slf4j");
    }
}
