plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "massey.hamhuo.timetagger"
    compileSdk = 36

    defaultConfig {
        applicationId = "massey.hamhuo.timetagger"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.1.0"
        
        // 只保留需要的密度资源
        vectorDrawables {
            useSupportLibrary = true
        }
    }
    
    // 只保留中文和英文资源
    androidResources {
        localeFilters += listOf("zh", "en")
    }

    buildTypes {
        release {
            // 启用代码压缩和混淆
            isMinifyEnabled = true
            // 启用资源压缩
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    // 优化打包配置
    packaging {
        resources {
            excludes += listOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/ASL2.0",
                "META-INF/*.kotlin_module"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    useLibrary("wear-sdk")
    buildFeatures {
        compose = true
    }
}

configurations.all {
    // 全局排除 listenablefuture，避免与 Guava 冲突
    exclude(group = "com.google.guava", module = "listenablefuture")
}

dependencies {

    implementation(libs.play.services.wearable)
    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.compose.material)
    implementation(libs.compose.foundation)
    implementation(libs.wear.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.core.splashscreen)
    implementation(libs.wear.remote.interactions)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)
    
    // 只导入需要的图标，不使用 extended
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.wear.tiles:tiles:1.2.0")
    implementation("androidx.wear.tiles:tiles-material:1.2.0")
    implementation("com.google.guava:guava:31.1-android")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
}