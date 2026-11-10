import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.ballsortgame"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.ballsortgame"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

// Text-encoded originals are imported through IDE buffer-safe tools. Package decoded PNGs only.
abstract class DecodeWorldArtwork : DefaultTask() {
    @get:InputDirectory abstract val originals: org.gradle.api.file.DirectoryProperty
    @get:OutputDirectory abstract val outputDirectory: org.gradle.api.file.DirectoryProperty
    @TaskAction fun decode() {
        val destination = outputDirectory.get().dir("worlds").asFile
        destination.deleteRecursively() // Generated output only; exclude obsolete comparison sheets.
        destination.mkdirs()
        val active = setOf("dusk-depth-v2", "moonlight-depth-v2", "midnight-depth-v5",
            "dusk-frame-v2", "moonlight-frame-v2", "midnight-frame-v2", "midnight-portrait-v3",
            "dusk-wide-v2", "moonlight-wide-v2", "midnight-wide-v2",
            "infinite-entry-v1", "stone-button-v1", "browser-mist-v1")
        active.forEach { name ->
            val source = originals.file("$name.png.base64").get().asFile
            destination.resolve("$name.png").writeBytes(Base64.getMimeDecoder().decode(source.readText()))
        }
    }
}
val decodeWorldArtwork = tasks.register<DecodeWorldArtwork>("decodeWorldArtwork") {
    originals.set(layout.projectDirectory.dir("artwork"))
    outputDirectory.set(layout.buildDirectory.dir("generated/world-artwork"))
}
androidComponents.onVariants { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(decodeWorldArtwork, DecodeWorldArtwork::outputDirectory)
}

// The bundled typeface is decoded into an Android font resource, never fetched at runtime.
abstract class DecodeDisplayFont : DefaultTask() {
    @get:InputFile abstract val original: org.gradle.api.file.RegularFileProperty
    @get:OutputDirectory abstract val outputDirectory: org.gradle.api.file.DirectoryProperty
    @TaskAction fun decode() {
        val destination = outputDirectory.get().dir("font").asFile
        destination.mkdirs()
        destination.resolve("libre_baskerville.ttf").writeBytes(
            Base64.getMimeDecoder().decode(original.get().asFile.readText()))
    }
}
val decodeDisplayFont = tasks.register<DecodeDisplayFont>("decodeDisplayFont") {
    original.set(layout.projectDirectory.file("typography/libre-baskerville.ttf.base64"))
    outputDirectory.set(layout.buildDirectory.dir("generated/display-font"))
}
androidComponents.onVariants { variant ->
    variant.sources.res?.addGeneratedSourceDirectory(decodeDisplayFont, DecodeDisplayFont::outputDirectory)
}

dependencies {
    implementation(project(":puzzle-engine"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.foundation)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}