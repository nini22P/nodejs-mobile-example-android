import java.security.MessageDigest
import java.nio.file.Files
import java.net.URI
import java.util.zip.ZipInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.nodejsmobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.nodejsmobile"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        externalNativeBuild {
            cmake {
                cppFlags("")
                arguments("-DANDROID_STL=c++_shared")
            }
        }
        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86_64"))
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("libnode/bin/")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}

abstract class DownloadNodejsTask : DefaultTask() {
    @TaskAction
    fun run() {
        val url =
            "https://github.com/nodejs-mobile/nodejs-mobile/releases/download/v18.20.4/nodejs-mobile-v18.20.4-android.zip"
        val expectedMD5 = "4fe60de25381937b03642404513ec26b"
        val zipFile = project.file("./libnode/nodejs-mobile-v18.20.4-android.zip")
        val extractDir = project.file("./libnode")

        if (zipFile.exists()) {
            val calculatedMD5 = MessageDigest.getInstance("MD5")
                .digest(Files.readAllBytes(zipFile.toPath()))
                .joinToString("") { "%02x".format(it) }

            if (calculatedMD5 != expectedMD5) {
                zipFile.delete()
                println("MD5 mismatch. File deleted: $zipFile")
            }
        }

        if (!zipFile.exists()) {
            zipFile.parentFile.mkdirs()
            println("Downloading Node.js from: $url")
            zipFile.outputStream().use { os ->
                URI.create(url).toURL().openStream().use { input ->
                    input.copyTo(os)
                }
            }

            val calculatedMD5 = MessageDigest.getInstance("MD5")
                .digest(Files.readAllBytes(zipFile.toPath()))
                .joinToString("") { "%02x".format(it) }

            if (calculatedMD5 != expectedMD5) {
                throw GradleException("MD5 verification failed for $zipFile")
            }

            println("Extracting Node.js to: $extractDir")
            extractDir.mkdirs()
            ZipInputStream(zipFile.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val targetFile = File(extractDir, entry.name)
                    if (entry.isDirectory) {
                        targetFile.mkdirs()
                    } else {
                        targetFile.parentFile.mkdirs()
                        targetFile.outputStream().use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
    }
}

tasks.register<DownloadNodejsTask>("downloadNodejs")

tasks.named("preBuild") {
    dependsOn("downloadNodejs")
}