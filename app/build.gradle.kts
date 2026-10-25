import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

tasks.register("downloadSherpaOnnxAar") {
    val aarFile = file("libs/sherpa-onnx.aar")
    outputs.file(aarFile)
    doLast {
        if (!aarFile.exists()) {
            aarFile.parentFile.mkdirs()
            logger.lifecycle("Downloading sherpa-onnx.aar...")
            val candidateUrls = listOf(
                "https://huggingface.co/csukuangfj/sherpa-onnx-libs/resolve/main/android/aar/1.12.21/sherpa-onnx-static-link-onnxruntime-1.12.21.aar",
                "https://github.com/k2fsa/sherpa-onnx/releases/download/v1.12.21/sherpa-onnx-static-link-onnxruntime-1.12.21.aar"
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
                        aarFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    downloaded = true
                    logger.lifecycle("Downloaded sherpa-onnx.aar successfully from $candidateUrl.")
                    break
                } catch (e: Exception) {
                    logger.lifecycle("Failed to download from $candidateUrl: ${e.message}")
                }
            }
            if (!downloaded) {
                throw IOException("Could not download sherpa-onnx.aar from any candidate URL.")
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
        versionCode = 4
        versionName = "1.3"

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
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    
    implementation(libs.androidx.savedstate.ktx)
    
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    
    implementation(libs.mediapipe.tasks.genai)
    implementation(libs.litertlm.android)
    implementation(libs.aicore)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
