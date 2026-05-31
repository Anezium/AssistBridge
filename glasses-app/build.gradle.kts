plugins {
    id("com.android.application")
}

android {
    namespace = "com.anezium.assistbridge.glasses"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.anezium.assistbridge.glasses"
        minSdk = 31
        targetSdk = 36
        versionCode = 3
        versionName = "0.3.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":protocol"))
    implementation("com.rokid.cxr:cxr-service-bridge:1.0-20260212.103714-88")
}
