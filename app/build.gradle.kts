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
    lint {
        baseline = file("lint-baseline.xml")
    }

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

    namespace = "garden.appl.mitch"
    compileSdk = 33

    defaultConfig {
        applicationId = "ua.gardenapple.itchupdater"
        minSdk = 21 //do NOT support 16 or lower due to JavascriptInterface being broken
        targetSdk = 29
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        versionCode = 106
        versionName = "2.2.3"
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
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.7.10")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")

    implementation("androidx.appcompat:appcompat:1.5.0")
    implementation("com.google.android.material:material:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.preference:preference-ktx:1.2.0")
    implementation("androidx.work:work-runtime-ktx:2.7.1")
    implementation("androidx.room:room-runtime:2.4.3")
    implementation("androidx.room:room-ktx:2.4.3")
    kapt("androidx.room:room-compiler:2.4.3")
    androidTestImplementation("androidx.room:room-testing:2.4.3")
    implementation("androidx.lifecycle:lifecycle-common-java8:2.5.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.5.1")
    androidTestImplementation("androidx.arch.core:core-testing:2.1.0")
    implementation("androidx.paging:paging-runtime-ktx:3.1.1")
    //TODO: not sure why I need to include this, reason is probably stupid
    implementation("com.google.guava:guava:29.0-android")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")


    // Jsoup
    implementation("org.jsoup:jsoup:1.15.3")
    // Glide
    implementation("com.github.bumptech.glide:glide:4.13.2")
    kapt("com.github.bumptech.glide:compiler:4.13.2")
    implementation("com.github.bumptech.glide:recyclerview-integration:4.13.2")
    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.7")
    // FAB Speed Dial
    implementation("com.leinardi.android:speed-dial:3.3.0")
    // Material Progress Bar
    implementation("me.zhanghai.android.materialprogressbar:library:1.6.1")
    // Colormath (CSS color parsing)
    implementation("com.github.ajalt:colormath:1.4.1")
    // Application Crash Reports for Android (ACRA)
    implementation("ch.acra:acra-mail:5.9.6")
    implementation("ch.acra:acra-dialog:5.9.6")
}
