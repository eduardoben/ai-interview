import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

tasks.register("downloadSherpaOnnxAar") {
    val aarFile = layout.projectDirectory.file("libs/sherpa-onnx.aar").asFile
    val tempFile = layout.projectDirectory.file("libs/sherpa-onnx.aar.tmp").asFile
    val expectedSha256 = "6d1bf9a2ede218d9f33115f494058b83ae5c2af8a8275e64353c3de8c83a2603"
    val sherpaVersion = libs.versions.sherpaOnnx.get()

    outputs.file(aarFile)
    doLast {
        fun computeSha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            return digest.digest().joinToString("") { b -> "%02x".format(b) }
        }

        if (aarFile.exists()) {
            val existingHash = computeSha256(aarFile)
            if (!existingHash.equals(expectedSha256, ignoreCase = true)) {
                logger.warn("Existing sherpa-onnx.aar checksum mismatch. Re-downloading...")
                aarFile.delete()
            }
        }

        if (!aarFile.exists()) {
            aarFile.parentFile.mkdirs()
            logger.lifecycle("Downloading sherpa-onnx.aar ($sherpaVersion)...")
            val candidateUrls = listOf(
                "https://huggingface.co/csukuangfj/sherpa-onnx-libs/resolve/main/android/aar/$sherpaVersion/sherpa-onnx-static-link-onnxruntime-$sherpaVersion.aar",
                "https://github.com/k2fsa/sherpa-onnx/releases/download/v$sherpaVersion/sherpa-onnx-static-link-onnxruntime-$sherpaVersion.aar"
            )
            var downloaded = false
            for (candidateUrl in candidateUrls) {
                try {
                    var currentUrl = candidateUrl
                    var connection: HttpURLConnection
                    var redirectCount = 0
                    val maxRedirects = 10
                    while (true) {
                        val url = URI(currentUrl).toURL()
                        connection = url.openConnection() as HttpURLConnection
                        connection.connectTimeout = 15000
                        connection.readTimeout = 30000
                        connection.instanceFollowRedirects = false
                        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        connection.setRequestProperty("Accept", "*/*")
                        val responseCode = connection.responseCode
                        if (responseCode in 300..399) {
                            val location = connection.getHeaderField("Location")
                            if (location != null && redirectCount < maxRedirects) {
                                currentUrl = location
                                connection.disconnect()
                                redirectCount++
                                continue
                            }
                        }
                        if (responseCode !in 200..299) {
                            connection.disconnect()
                            throw IOException("HTTP $responseCode downloading $currentUrl")
                        }
                        break
                    }
                    connection.inputStream.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    val downloadedHash = computeSha256(tempFile)
                    if (downloadedHash.equals(expectedSha256, ignoreCase = true)) {
                        if (aarFile.exists()) aarFile.delete()
                        tempFile.renameTo(aarFile)
                        downloaded = true
                        logger.lifecycle("Downloaded and verified sherpa-onnx.aar successfully from $candidateUrl.")
                        break
                    } else {
                        tempFile.delete()
                        logger.warn("Checksum verification failed for download from $candidateUrl.")
                    }
                } catch (e: Exception) {
                    logger.lifecycle("Failed to download from $candidateUrl: ${e.message}")
                }
            }
            if (!downloaded) {
                throw IOException("Could not download verified sherpa-onnx.aar from any candidate URL.")
            }
        }
    }
}

tasks.named("preBuild") {
    dependsOn("downloadSherpaOnnxAar")
}

android {
    namespace = "com.local.interviewassistant"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.local.interviewassistant"
        minSdk = 34
        targetSdk = 37
        versionCode = 7
        versionName = "1.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        ndk {
            abiFilters += listOf("arm64-v8a")
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        noCompress += listOf("tflite", "litertlm", "bin", "task", "onnx")
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
            pickFirsts += listOf("**/libc++_shared.so", "**/libOpenCL.so")
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    
    implementation(libs.androidx.savedstate)
    
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    
    implementation(libs.litertlm.android)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(files("libs/sherpa-onnx.aar"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")

    debugImplementation(libs.androidx.compose.ui.tooling)
}
