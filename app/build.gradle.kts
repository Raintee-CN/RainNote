plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val h5Dir = rootProject.layout.projectDirectory.dir("h5-ui")
val h5DistDir = h5Dir.dir("dist")
val webAssetsDir = layout.projectDirectory.dir("src/main/assets")
val npmCommand = if (System.getProperty("os.name").lowercase().contains("windows")) "npm.cmd" else "npm"

val installH5Dependencies by tasks.registering(Exec::class) {
    workingDir = h5Dir.asFile
    commandLine(npmCommand, "install")
    onlyIf { !h5Dir.dir("node_modules").asFile.exists() }
}

val buildH5 by tasks.registering(Exec::class) {
    dependsOn(installH5Dependencies)
    workingDir = h5Dir.asFile
    commandLine(npmCommand, "run", "build")
}

val copyH5ToAssets by tasks.registering(Copy::class) {
    dependsOn(buildH5)
    from(h5DistDir)
    into(webAssetsDir)
}

tasks.named("preBuild") {
    dependsOn(copyH5ToAssets)
}

android {
    namespace = "com.raintee.rainnote"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.raintee.rainnote"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    buildFeatures {
        viewBinding = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

android.applicationVariants.all {
    val buildTime = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
    outputs.all {
        (this as BaseVariantOutputImpl).outputFileName =
            "yujian-v${versionName}-${versionCode}-${name}-${buildTime}.apk"
    }
}

dependencies {
    implementation(project(":server"))
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
