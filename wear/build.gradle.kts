import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.syschimp.glucoripper.wear"
    compileSdk = 35

    defaultConfig {
        // Must match the phone applicationId so DataClient routes events between them.
        applicationId = "com.syschimp.glucoripper"
        minSdk = 30
        targetSdk = 35
        // Wear versionCode = phone versionCode + 1_000_000. Play Console requires unique
        // versionCodes per applicationId; the offset keeps them in lockstep for humans
        // while guaranteeing no collision with phone builds.
        versionCode = 1_000_010
        versionName = "0.4.2"
    }

    signingConfigs {
        create("release") {
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
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
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
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.wear.compose.material)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.compose.navigation)

    implementation(libs.play.services.wearable)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.wear.tiles.material)
    implementation(libs.androidx.wear.protolayout)
    implementation(libs.androidx.wear.protolayout.material)
    implementation(libs.androidx.wear.protolayout.expression)
    implementation(libs.androidx.wear.complications)
    implementation(libs.androidx.concurrent.futures)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
}
