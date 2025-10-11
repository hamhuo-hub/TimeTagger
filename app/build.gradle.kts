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
        versionCode = 2
        versionName = "1.1.0"
        
        // 只保留需要的密度资源
        vectorDrawables {
            useSupportLibrary = true
        }
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

    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.ui.tooling.preview)
    implementation(libs.compose.material)
    implementation(libs.compose.foundation)
    implementation(libs.activity.compose)
    implementation(libs.core.splashscreen)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)
    
    // Tiles（表盘磁贴）
    implementation(libs.tiles)
    implementation(libs.tiles.material)
    
    // Guava（Tiles 需要）
    implementation(libs.guava)
    
    // ViewModel
    implementation(libs.lifecycle.viewmodel.compose)
    
    // Kotlin 协程（Flow 支持）
    implementation(libs.kotlinx.coroutines.android)
}