plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.paddle.ocr"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            consumerProguardFiles("proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    implementation(libs.onnxruntime.android)
    implementation(libs.opencv.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit.ktx)
    androidTestImplementation(libs.androidx.test.monitor)
    androidTestImplementation(libs.junit)
}

tasks.register("ensureModelWeights") {
    doLast {
        val detModel = file("src/main/assets/models/det/inference.onnx")
        val recModel = file("src/main/assets/models/rec/inference.onnx")
        val detValid = detModel.exists() && detModel.length() > 100_000
        val recValid = recModel.exists() && recModel.length() > 100_000

        if (!detValid || !recValid) {
            println("[PP-OCR] Model files missing or Git LFS pointer detected. Attempting download via scripts/download_models.py...")
            val isWindows = System.getProperty("os.name").lowercase().contains("windows")
            val pythonCmd = if (isWindows) "python" else "python3"
            try {
                project.exec {
                    commandLine(pythonCmd, "${rootProject.projectDir}/scripts/download_models.py", "--model", "v6small")
                }
                println("[PP-OCR] Models downloaded successfully.")
            } catch (e: Exception) {
                logger.warn("[PP-OCR] Could not run python download script: ${e.message}. Please run: python scripts/download_models.py")
            }
        }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn("ensureModelWeights")
}

