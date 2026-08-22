plugins {
    id("com.android.application")
}

val releaseStoreFile = providers.gradleProperty("RELEASE_STORE_FILE").orNull
val releaseStorePassword = providers.gradleProperty("RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.gradleProperty("RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.gradleProperty("RELEASE_KEY_PASSWORD").orNull
val hasReleaseSigning = listOf(
    releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword
).all { it != null }

android {
    namespace = "com.kurcep.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kurcep.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 2
        versionName = "1.2"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        // Unit tests run on the JVM: Android classes (Looper, Handler, Log) return defaults
        // instead of throwing so the repository code paths can execute without a device.
        unitTests.isReturnDefaultValues = true
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

dependencies {
    implementation("androidx.work:work-runtime:2.11.2")
    testImplementation("junit:junit:4.13.2")
    // android.jar ships org.json stubs that cannot parse; local unit tests need the real library.
    testImplementation("org.json:json:20240303")
}
