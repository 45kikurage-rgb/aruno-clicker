plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseKeystorePath = providers.environmentVariable("ARUNO_KEYSTORE_PATH")
val releaseSigningPassword = providers.environmentVariable("ARUNO_SIGNING_PASSWORD")

android {
    namespace = "jp.aruno.clicker"
    compileSdk = 35

    defaultConfig {
        applicationId = "jp.aruno.clicker"
        minSdk = 26
        targetSdk = 35
        versionCode = 15
        versionName = "0.5.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    flavorDimensions += "channel"
    productFlavors {
        create("atlas") {
            dimension = "channel"
            applicationId = "jp.aruno.clicker"
            buildConfigField("boolean", "IS_VER_S", "false")
            buildConfigField("String", "MANAGEMENT_GATE", "\"19801117\"")
            buildConfigField(
                "String",
                "CONFIG_ENDPOINT",
                "\"https://aruno-clicker-config.45kikurage.workers.dev/v1/config\"",
            )
        }
        create("lumen") {
            dimension = "channel"
            applicationId = "jp.aruno.x7c41"
            buildConfigField("boolean", "IS_VER_S", "true")
            buildConfigField("String", "MANAGEMENT_GATE", "\"\"")
            // Hidden from ver.S UI; replace with the custom-domain route when Cloudflare is available.
            buildConfigField(
                "String",
                "CONFIG_ENDPOINT",
                "\"https://aruno-clicker-config.45kikurage.workers.dev/v1/config\"",
            )
        }
    }

    signingConfigs {
        create("stableRelease") {
            if (
                releaseKeystorePath.isPresent &&
                releaseSigningPassword.isPresent
            ) {
                storeFile = file(releaseKeystorePath.get())
                storePassword = releaseSigningPassword.get()
                keyAlias = "aruno-stable"
                keyPassword = releaseSigningPassword.get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("stableRelease")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
