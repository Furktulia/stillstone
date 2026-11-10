plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    testImplementation(libs.junit)
}

application {
    mainClass.set("com.example.ballsortgame.engine.tools.PuzzleLabKt")
}

tasks.named<JavaExec>("run") {
    workingDir(rootProject.projectDir)
    maxHeapSize = "1g"
}

tasks.test {
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}
