import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    id("com.android.application")
    id("jacoco")
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
        // Core repository/parsing tests must fail loudly if an Android API leaks into them.
        // UI and WorkManager behavior belongs in device/instrumentation tests.
        unitTests.isReturnDefaultValues = false
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

jacoco {
    toolVersion = "0.8.12"
}

// Keep the coverage gate focused on the deterministic domain/network layer. Android UI,
// widget callbacks, and lifecycle glue require an instrumented/device environment.
val coverageClassDirectories = fileTree(
    layout.buildDirectory
        .dir("intermediates/javac/debug/compileDebugJavaWithJavac/classes")
        .get()
        .asFile
) {
    include("com/kurcep/app/Rate*.class")
}
val coverageExecutionData = fileTree(layout.buildDirectory.get().asFile) {
    include("jacoco/testDebugUnitTest.exec")
    include("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")
}

val jacocoTestDebugUnitTestReport = tasks.register<JacocoReport>("jacocoTestDebugUnitTestReport") {
    dependsOn("testDebugUnitTest")
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    sourceDirectories.setFrom(files("$projectDir/src/main/java"))
    classDirectories.setFrom(coverageClassDirectories)
    executionData.setFrom(coverageExecutionData)
}

tasks.register<JacocoCoverageVerification>("jacocoTestDebugUnitTestCoverageVerification") {
    dependsOn(jacocoTestDebugUnitTestReport)
    sourceDirectories.setFrom(files("$projectDir/src/main/java"))
    classDirectories.setFrom(coverageClassDirectories)
    executionData.setFrom(coverageExecutionData)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.60".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn("jacocoTestDebugUnitTestCoverageVerification")
}
