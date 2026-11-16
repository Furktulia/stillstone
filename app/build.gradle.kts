import java.util.Base64
import java.awt.image.BufferedImage
import java.awt.RenderingHints

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

    // Debug builds are signed with a key kept in the project, not the one Android Studio
    // creates per machine in ~/.android. Android only updates an installed app when the
    // signature matches, so a per-machine key made playtest phones refuse new builds
    // (and lose their saves on reinstall) whenever Android Studio was set up again.
    // This is a debug key with the standard public password; never use it for a release.
    signingConfigs {
        getByName("debug") {
            storeFile = file("playtest-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
        }
        // What testers should get. Debug builds are unoptimised and debuggable, and Compose runs
        // several times slower in them: a friend's Snapdragon 778G phone found the debug build laggy.
        // This is the release build (shrunk and optimised, with the baseline profile), signed with
        // the playtest key so it installs over earlier debug builds and keeps their saves.
        create("playtest") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
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
        val active = setOf("dusk-frame-v2", "moonlight-frame-v2", "midnight-frame-v2", "midnight-portrait-v3",
            "dusk-wide-v2", "moonlight-wide-v2", "midnight-wide-v2",
            "stone-button-v1")
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

// Launcher sources follow the same portable Base64 convention as world art.
// Resize at build time; no image service, external tool or local generated_images path is needed.
abstract class DecodeLauncherArtwork : DefaultTask() {
    @get:InputFile abstract val original: org.gradle.api.file.RegularFileProperty
    @get:OutputDirectory abstract val outputDirectory: org.gradle.api.file.DirectoryProperty
    @TaskAction fun decode() {
        val bytes = Base64.getMimeDecoder().decode(original.get().asFile.readText())
        val sheet = javax.imageio.ImageIO.read(bytes.inputStream())
            ?: error("Approved launcher reference is not a PNG")
        check(sheet.width == 1536 && sheet.height == 1024) { "Approved reference dimensions changed; review crop coordinates" }

        // Extract the actual approved B pixels. No regeneration, recolouring or invented texture.
        // Separate circle/squircle artwork retains each reference's own corner details.
        fun extract(left: Int, top: Int, right: Int, bottom: Int): BufferedImage {
            fun painted(x: Int, y: Int): Boolean {
                val pixel = sheet.getRGB(x, y)
                val red = pixel shr 16 and 255
                val green = pixel shr 8 and 255
                val blue = pixel and 255
                return blue - green > 6 && red - green > 4
            }
            var minX = right; var maxX = left; var minY = bottom; var maxY = top
            for (y in top until bottom) for (x in left until right) if (painted(x, y)) {
                minX = minOf(minX, x); maxX = maxOf(maxX, x)
                minY = minOf(minY, y); maxY = maxOf(maxY, y)
            }
            val size = maxOf(maxX - minX + 1, maxY - minY + 1)
            check(size in 290..330) { "Could not locate the approved B icon" }
            val x0 = (minX + maxX + 1 - size) / 2
            val y0 = (minY + maxY + 1 - size) / 2
            val image = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
            fun interiorStone(x: Int, y: Int): Boolean {
                val pixel = sheet.getRGB(x, y)
                val red = pixel shr 16 and 255
                val green = pixel shr 8 and 255
                val blue = pixel and 255
                // Reject the pale gray/stone blend from the mockup's already-antialiased rim.
                return blue - green > 18 && red - green > 10
            }
            for (y in 0 until size) {
                val sy = (y0 + y).coerceIn(minY + 2, maxY - 2)
                var first = minX; var last = maxX
                while (first < last && !interiorStone(first, sy)) first++
                while (last > first && !interiorStone(last, sy)) last--
                check(last > first) { "Missing stone material in approved launcher crop" }
                // Extend clean material from two pixels inside the rim. This removes only
                // the gray-matted fringe; the original interior texture and mark stay intact.
                val inset = minOf(2, (last - first) / 2)
                first += inset; last -= inset
                for (x in 0 until size) image.setRGB(x, y, sheet.getRGB((x0 + x).coerceIn(first, last), sy))
            }
            logger.lifecycle("Approved B crop: x=$x0 y=$y0 size=$size")
            return image
        }
        fun overscan(visible: BufferedImage): BufferedImage {
            val side = visible.width * 3 / 2
            val margin = (side - visible.width) / 2
            val image = BufferedImage(side, side, BufferedImage.TYPE_INT_RGB)
            // Android reveals the middle 72dp of its 108dp layer. Add overscan OUTSIDE
            // the approved crop instead of cropping away the top-left/bottom-right details.
            for (y in 0 until side) for (x in 0 until side) image.setRGB(x, y,
                visible.getRGB((x - margin).coerceIn(0, visible.width - 1),
                    (y - margin).coerceIn(0, visible.height - 1)))
            return image
        }
        fun write(source: BufferedImage, folder: String, name: String, size: Int) {
            val target = outputDirectory.get().dir(folder).asFile.apply { mkdirs() }.resolve(name)
            val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
            val graphics = image.createGraphics()
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            graphics.drawImage(source, 0, 0, size, size, null)
            graphics.dispose()
            javax.imageio.ImageIO.write(image, "png", target)
        }
        val square = extract(1136, 8, 1460, 342)
        val circle = extract(800, 8, 1136, 342)
        write(overscan(square), "drawable-nodpi", "launcher_stone.png", 512)
        write(overscan(circle), "drawable-nodpi", "launcher_stone_round.png", 512)
        mapOf("mdpi" to 48, "hdpi" to 72, "xhdpi" to 96, "xxhdpi" to 144, "xxxhdpi" to 192).forEach { (density, size) ->
            write(square, "mipmap-$density", "ic_stillstone.png", size)
            write(circle, "mipmap-$density", "ic_stillstone_round.png", size)
        }
    }
}
val decodeLauncherArtwork = tasks.register<DecodeLauncherArtwork>("decodeLauncherArtwork") {
    original.set(layout.projectDirectory.file("artwork/launcher-carved-approved-reference.png.base64"))
    outputDirectory.set(layout.buildDirectory.dir("generated/launcher-artwork"))
}
androidComponents.onVariants { variant ->
    variant.sources.res?.addGeneratedSourceDirectory(decodeLauncherArtwork, DecodeLauncherArtwork::outputDirectory)
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