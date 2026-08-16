import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.paddle.ocr.demo"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.paddle.ocr.demo"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    implementation(project(":ppocr-sdk"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.coil.compose)

    debugImplementation(libs.compose.ui.tooling)
}

tasks.register("copyApkAndRecordTime") {
    doLast {
        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val timeFile = rootProject.file("time.txt")
        timeFile.writeText(now)
        println("Updated time.txt: $now")

        val apkDir = layout.buildDirectory.dir("outputs/apk/debug").get().asFile
        val apkFiles = apkDir.listFiles { _, name -> name.endsWith(".apk") }
        if (!apkFiles.isNullOrEmpty()) {
            val srcApk = apkFiles.first()
            val destApk = rootProject.file("app-debug.apk")
            srcApk.copyTo(destApk, overwrite = true)
            println("Copied ${srcApk.name} -> ${destApk.name}")
        }
    }
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
    finalizedBy("copyApkAndRecordTime")
}

