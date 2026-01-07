plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.swiftmail.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.swiftmail.app"
        minSdk = 24
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

dependencies {
    implementation(files("libs/ssh.jar"))

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)

    // Navigation libraries are fine to keep if you still use them.
    // If you removed fragments/nav graph completely, we can remove these later.
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)

    implementation(libs.activity)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // Replaced old Paho library with a fork that supports Android 12+ (PendingIntent fix)
    implementation("com.github.hannesa2:paho.mqtt.android:3.3.5")
    implementation("androidx.legacy:legacy-support-v4:1.0.0")

}