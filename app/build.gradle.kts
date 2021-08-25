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

    compileSdk = Build.Android.compileSdk

    defaultConfig {
        applicationId = "ua.gardenapple.itchupdater"
        minSdk = Build.Android.minSdk
        targetSdk = Build.Android.targetSdk
        versionCode = Build.Android.versionCode
        versionName = Build.Android.versionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:${Build.Versions.kotlin}")
    implementation("androidx.appcompat:appcompat:${Build.Versions.appcompat}")
    implementation("com.google.android.material:material:${Build.Versions.material}")
    implementation("androidx.constraintlayout:constraintlayout:${Build.Versions.constraintLayout}")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:${Build.Versions.swipeRefreshLayout}")
    implementation("androidx.preference:preference-ktx:${Build.Versions.preference}")
    testImplementation("junit:junit:${Build.Versions.junit}")
    androidTestImplementation("androidx.test.ext:junit:${Build.Versions.androidxJunit}")
    androidTestImplementation("androidx.test.espresso:espresso-core:${Build.Versions.androidxEspresso}")
    implementation("androidx.work:work-runtime-ktx:${Build.Versions.work}")

    // Room components
    implementation("androidx.room:room-runtime:${Build.Versions.room}")
    implementation("androidx.room:room-ktx:${Build.Versions.room}")
    kapt("androidx.room:room-compiler:${Build.Versions.room}")
    androidTestImplementation("androidx.room:room-testing:${Build.Versions.room}")

    // Lifecycle components
    implementation("androidx.lifecycle:lifecycle-common-java8:${Build.Versions.lifecycle}")
    androidTestImplementation("androidx.arch.core:core-testing:${Build.Versions.androidxArch}")

    // ViewModel Kotlin support
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:${Build.Versions.lifecycle}")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${Build.Versions.coroutines}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Build.Versions.coroutines}")

    //Paging
    implementation("androidx.paging:paging-runtime-ktx:${Build.Versions.paging}")


    // Jsoup
    implementation("org.jsoup:jsoup:${Build.Versions.jsoup}")
    // Glide
    implementation("com.github.bumptech.glide:glide:${Build.Versions.glide}")
    kapt("com.github.bumptech.glide:compiler:${Build.Versions.glide}")
    implementation("com.github.bumptech.glide:recyclerview-integration:${Build.Versions.glide}")
    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:${Build.Versions.okHttp}")
    // FAB Speed Dial
    implementation("com.leinardi.android:speed-dial:${Build.Versions.speedDial}")
    // Material Progress Bar
    implementation("me.zhanghai.android.materialprogressbar:library:${Build.Versions.materialProgressBar}")
    // Colormath (CSS color parsing)
    implementation("com.github.ajalt:colormath:${Build.Versions.colormath}")
    // Application Crash Reports for Android (ACRA)
    implementation("ch.acra:acra-mail:${Build.Versions.acra}")
    implementation("ch.acra:acra-dialog:${Build.Versions.acra}")
}
