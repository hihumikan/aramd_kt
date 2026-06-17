plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val releaseStoreFile = providers.environmentVariable("ANDROID_SIGNING_STORE_FILE")
val releaseStorePassword = providers.environmentVariable("ANDROID_SIGNING_STORE_PASSWORD")
val releaseKeyAlias = providers.environmentVariable("ANDROID_SIGNING_KEY_ALIAS")
val releaseKeyPassword = providers.environmentVariable("ANDROID_SIGNING_KEY_PASSWORD")
val hasReleaseSigningConfig = releaseStoreFile.isPresent &&
    releaseStorePassword.isPresent &&
    releaseKeyAlias.isPresent &&
    releaseKeyPassword.isPresent
val appVersionCode = providers.environmentVariable("VERSION_CODE").map(String::toInt).orElse(1)
val appVersionName = providers.environmentVariable("VERSION_NAME").orElse("1.0")

android {
    namespace = "net.qqey.aramd"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "net.qqey.aramd"
        minSdk = 34
        targetSdk = 36
        versionCode = appVersionCode.get()
        versionName = appVersionName.get()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = file(releaseStoreFile.get())
                storePassword = releaseStorePassword.get()
                keyAlias = releaseKeyAlias.get()
                keyPassword = releaseKeyPassword.get()
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
            optimization {
                enable = false
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

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
