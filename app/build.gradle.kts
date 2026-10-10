import com.android.build.api.dsl.ManagedVirtualDevice

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-kapt")
    id("dagger.hilt.android.plugin")
    alias(libs.plugins.androidx.navigation.safeargs.kotlin)
}

android {
    namespace = "com.lilyan_lefevre.puzzleit"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lilyan_lefevre.puzzleit"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"

        testInstrumentationRunner = "com.lilyan_lefevre.puzzleit.HiltTestRunner"

        // The production server is built into the app; `-PpuzzleitServer=http://192.168.1.20:8090` points a local build at a test server.
        val server = (project.findProperty("puzzleitServer") as String?) ?: "https://puzzleit.lilyan.app"
        buildConfigField("String", "PUZZLEIT_SERVER", "\"$server\"")
    }

    // The release key never lives in the repository: the CI decodes it from a secret and passes it by environment.
    val releaseKeystore = System.getenv("RELEASE_KEYSTORE_FILE")
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
        animationsDisabled = true
        // The instrumented tests run on virtual devices Gradle creates itself, the same on the PC and on the CI:
        // ./gradlew pixel6api34DebugAndroidTest (a current phone) and ./gradlew smallphoneapi34DebugAndroidTest (320 dp wide,
        // where the half level of the result sheet cannot show everything). Unlike connectedDebugAndroidTest they never touch
        // a phone that happens to be plugged in (that task uninstalls the app and wipes its data).
        managedDevices {
            devices {
                create<ManagedVirtualDevice>("pixel6api34") {
                    device = "Pixel 6"
                    apiLevel = 34
                    systemImageSource = "google"
                }
                create<ManagedVirtualDevice>("smallphoneapi34") {
                    device = "Nexus One"
                    apiLevel = 34
                    systemImageSource = "google"
                }
            }
        }
    }
}

dependencies {
    // To handle image loading
    implementation(libs.glide)

    // HTTP client for the optional sync with the owner's own server (PocketBase)
    implementation(libs.okhttp)
    // PocketBase tells the app the result of a "Sign in with Google" over server-sent events
    implementation(libs.okhttp.sse)
    // The server token is stored encrypted with a key held by the Android Keystore (Jetpack Security, nothing home-made)
    implementation(libs.androidx.security.crypto)

    // OpenCV dependency using version catalog
    implementation(libs.opencv)

    // Piece re-ranker network; the desktop build runs the same model in the JVM benchmarks
    implementation(libs.onnxruntime.android)
    testImplementation(libs.onnxruntime.jvm)
    
    // Android core dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    
    // Material Design 3
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    
    // ViewModel
    implementation(libs.androidx.lifecycle.viewmodel)
    
    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.navigation.runtime.ktx)
    kapt(libs.androidx.room.compiler)
    
    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    
    // Navigation
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    
    // Hilt Dependency Injection
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.fragment)
    kapt(libs.hilt.compiler)
    
    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.runner)
    
    // Hilt Testing
    testImplementation(libs.hilt.android.testing)
    kaptTest(libs.hilt.compiler)
    
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.hilt.android.testing)
    kaptAndroidTest(libs.hilt.compiler)
}
// The desktop onnxruntime replaces the Android one (same classes, different natives) on the JVM test classpath.
configurations.matching { it.name.contains("UnitTest") }.configureEach {
    exclude(group = "com.microsoft.onnxruntime", module = "onnxruntime-android")
}
