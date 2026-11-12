// Pure-JVM library: no Android dependencies, so every parser here is unit-tested on a plain JDK.
plugins {
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<JavaCompile>().configureEach {
    // Restrict to the Java 11 API surface, which Android 11 (API 30) provides.
    options.release.set(11)
    options.compilerArgs.add("-Xlint:all")
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
