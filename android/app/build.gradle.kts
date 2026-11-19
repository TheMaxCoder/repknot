plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Only the project URL and publishable key reach the app. They come from the repo's untracked
// .env; never put DB URLs, passwords, or secret keys into BuildConfig.
val dotenv: Map<String, String> = rootProject.file("../.env").takeIf { it.exists() }?.readLines()
    ?.mapNotNull { line -> Regex("^([A-Za-z0-9_]+)=(.*)$").find(line)?.destructured?.let { (k, v) -> k to v.trim() } }
    ?.toMap().orEmpty()
android {
    namespace = "com.repknot.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.repknot.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "SUPABASE_URL", "\"${dotenv["SUPABASE_URL"].orEmpty()}\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"${dotenv["SUPABASE_PUBLISHABLE_KEY"].orEmpty()}\"")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}
dependencies {
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.coroutines.core)
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.reorderable)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
}
