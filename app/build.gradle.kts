import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")
}


val keystorePropertiesFile = rootProject.file("keystore.properties")
val shouldSign = keystorePropertiesFile.canRead()

val keystoreProperties = Properties()

if (shouldSign) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}


android {
    if (shouldSign) {
        signingConfigs {
            create("release") {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
            }
        }
    }

    compileSdk = 31

    defaultConfig {
        applicationId = "ua.gardenapple.itchupdater"
        minSdk = 21
        targetSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        versionCode = 38
        versionName = "1.4.6"
    }
    buildTypes {
        named("release").configure {
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

            if (shouldSign) {
                signingConfig = signingConfigs.getByName("release")
            }

            isMinifyEnabled = false
        }
        named("debug").configure {
            isMinifyEnabled = false
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
    }
    flavorDimensions.add("platform")
    productFlavors {
        create("fdroid") {
        }
        create("itchio") {
        }
    }
    packagingOptions {
        resources.excludes.add("META-INF/atomicfu.kotlin_module")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.6.10")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.0-native-mt")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0-native-mt")

    implementation("androidx.appcompat:appcompat:1.4.1")
    implementation("com.google.android.material:material:1.5.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.3")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.preference:preference-ktx:1.2.0")
    implementation("androidx.work:work-runtime-ktx:2.7.1")
    implementation("androidx.room:room-runtime:2.4.2")
    implementation("androidx.room:room-ktx:2.4.2")
    kapt("androidx.room:room-compiler:2.4.2")
    androidTestImplementation("androidx.room:room-testing:2.4.2")
    implementation("androidx.lifecycle:lifecycle-common-java8:2.4.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.4.1")
    androidTestImplementation("androidx.arch.core:core-testing:2.1.0")
    implementation("androidx.paging:paging-runtime-ktx:3.1.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")


    // Jsoup
    implementation("org.jsoup:jsoup:1.14.3")
    // Glide
    implementation("com.github.bumptech.glide:glide:4.13.1")
    kapt("com.github.bumptech.glide:compiler:4.13.1")
    implementation("com.github.bumptech.glide:recyclerview-integration:4.13.1")
    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.5")
    // FAB Speed Dial
    implementation("com.leinardi.android:speed-dial:3.2.0")
    // Material Progress Bar
    implementation("me.zhanghai.android.materialprogressbar:library:1.6.1")
    // Colormath (CSS color parsing)
    implementation("com.github.ajalt:colormath:1.4.1")
    // Application Crash Reports for Android (ACRA)
    implementation("ch.acra:acra-mail:5.7.0")
    implementation("ch.acra:acra-dialog:5.7.0")
}
