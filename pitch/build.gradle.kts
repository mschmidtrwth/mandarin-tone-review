import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

// The sample recordings bundled with the app double as fixtures, read as /samples/<name>.wav.
sourceSets {
    test {
        resources.srcDir("../app/src/main/assets")
    }
}

dependencies {
    testImplementation(libs.junit)
}
