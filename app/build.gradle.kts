import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    kotlin("android")
    id("com.google.devtools.ksp")
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
    compileSdk = 34

    defaultConfig {
        applicationId = "ua.gardenapple.itchupdater"
        minSdk = 21
        targetSdk = 33
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
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.22")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    implementation("androidx.lifecycle:lifecycle-common-java8:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.3")
    androidTestImplementation("androidx.arch.core:core-testing:2.2.0")
    implementation("androidx.paging:paging-runtime-ktx:3.3.0")
    //TODO: https://stackoverflow.com/questions/64290141/android-studio-class-file-for-com-google-common-util-concurrent-listenablefuture#64733418
    implementation("com.google.guava:guava:29.0-android")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")


    // Jsoup
    implementation("org.jsoup:jsoup:1.15.3")
    // Glide
    implementation("com.github.bumptech.glide:glide:4.16.0")
    ksp("com.github.bumptech.glide:ksp:4.16.0")
    implementation("com.github.bumptech.glide:recyclerview-integration:4.16.0")
    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.14")
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
