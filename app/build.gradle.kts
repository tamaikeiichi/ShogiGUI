plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.tksoft.shogigui"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.tksoft.shogigui"
        minSdk = 24
        targetSdk = 36
        versionCode = 48
        versionName = "1.13.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // 署名鍵の場所とパスワードはリポジトリに入れず、各PCの ~/.gradle/gradle.properties に書く:
    //   SHOGIGUI_STORE_FILE=C:/Users/<ユーザー>/Documents/android_build_key/shogiGui.jks
    //   SHOGIGUI_STORE_PASSWORD=...
    //   SHOGIGUI_KEY_ALIAS=...
    //   SHOGIGUI_KEY_PASSWORD=...
    // 新しいPCでは shogiGui.jks をコピーし、上の4行を書けば署名付きでビルドできる。
    // 設定が欠けていれば警告を出し、署名なしでリリース版を作る。
    fun signingValue(key: String): String? = providers.gradleProperty(key).orNull?.trim()?.takeIf { it.isNotEmpty() }
    val releaseStoreFile = signingValue("SHOGIGUI_STORE_FILE")
    val releaseStorePassword = signingValue("SHOGIGUI_STORE_PASSWORD")
    val releaseKeyAlias = signingValue("SHOGIGUI_KEY_ALIAS")
    val releaseKeyPassword = signingValue("SHOGIGUI_KEY_PASSWORD")
    val canSignRelease = releaseStoreFile != null && releaseStorePassword != null &&
        releaseKeyAlias != null && releaseKeyPassword != null
    if (!canSignRelease) logger.warn("ShogiGUI: 署名鍵の設定が見つからないため、リリース版は署名なしで作られます")
    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        release {
            if (canSignRelease) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        //jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    packaging { jniLibs { useLegacyPackaging = true } }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.1.1"
    }

    defaultConfig {
        // ...
        externalNativeBuild {
            cmake {
                cppFlags("-std=c++17")
                arguments("-DNO_ASSUME_VLA=ON")
            }
        }
        ndk {
            // ストア公開に必要な 64bit 版とエミュレータ用
            abiFilters.addAll(listOf("arm64-v8a", "x86_64"))
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    //implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material3:material3-window-size-class:1.4.0")
    implementation("androidx.compose.material3:material3-adaptive-navigation-suite:1.5.0-alpha18")
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.compose.material:material-icons-extended:1.7.0")
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}