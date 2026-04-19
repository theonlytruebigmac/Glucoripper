import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.syschimp.glucoripper"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.syschimp.glucoripper"
        minSdk = 31
        targetSdk = 35
        versionCode = 7
        versionName = "0.4.1"
    }

    signingConfigs {
        create("release") {
            // Populated from env vars in CI; falls back to keystore.properties for local
            // signed builds. If neither is present the release build stays unsigned.
            val envStore = System.getenv("KEYSTORE_FILE")
            val envPw = System.getenv("KEYSTORE_PASSWORD")
            val envAlias = System.getenv("KEY_ALIAS")
            val envKeyPw = System.getenv("KEY_PASSWORD")
            val localPropsFile = rootProject.file("keystore.properties")

            when {
                envStore != null && envPw != null && envAlias != null && envKeyPw != null -> {
                    storeFile = file(envStore)
                    storePassword = envPw
                    keyAlias = envAlias
                    keyPassword = envKeyPw
                }
                localPropsFile.exists() -> {
                    val props = Properties().apply {
                        localPropsFile.inputStream().use { load(it) }
                    }
                    storeFile = rootProject.file(props.getProperty("storeFile"))
                    storePassword = props.getProperty("storePassword")
                    keyAlias = props.getProperty("keyAlias")
                    keyPassword = props.getProperty("keyPassword")
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // ComponentActivity (not FragmentActivity) hosts our registerForActivityResult
        // calls — the fragment version check doesn't apply.
        disable += "InvalidFragmentVersionForActivityResult"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.health.connect)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.play.services.wearable)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
}
