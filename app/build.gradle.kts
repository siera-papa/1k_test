plugins { id("com.android.application") }
android {
    namespace = "rs.project1k.plan"
    compileSdk = 35
    defaultConfig { applicationId = "rs.project1k.plan"; minSdk = 26; targetSdk = 35; versionCode = 19; versionName = "0.1.18" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
