import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.clickandsaveai.app"
    minSdk = 24
    targetSdk = 36
    versionCode = 2
    versionName = "1.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  val productionReleaseCandidate = System.getenv("PRODUCTION_RELEASE_CANDIDATE")
    ?.equals("true", ignoreCase = true) == true
  val productionWebClientId = System.getenv("PRODUCTION_GOOGLE_WEB_CLIENT_ID")?.trim().orEmpty()
  val stagingWebClientId = "716864421960-hnt5709tqk9qp79si8ggplf5jif1ulfu.apps.googleusercontent.com"

  fun requireProductionInput(name: String): String {
    return System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }
      ?: throw GradleException("Production release candidate requires $name")
  }

  val productionUploadKeystore = if (productionReleaseCandidate) {
    val path = requireProductionInput("PRODUCTION_UPLOAD_KEYSTORE_PATH")
    file(path).also {
      if (!it.isFile) throw GradleException("PRODUCTION_UPLOAD_KEYSTORE_PATH does not reference a file")
    }
  } else null

  if (productionReleaseCandidate) {
    if (productionWebClientId.isBlank()) {
      throw GradleException("Production release candidate requires PRODUCTION_GOOGLE_WEB_CLIENT_ID")
    }
    if (productionWebClientId == stagingWebClientId) {
      throw GradleException("Production release candidate cannot use the staging Google OAuth client")
    }
    if (!productionWebClientId.endsWith(".apps.googleusercontent.com")) {
      throw GradleException("PRODUCTION_GOOGLE_WEB_CLIENT_ID has an invalid format")
    }
  }

  val stagingDebugKeystorePath = System.getenv("STAGING_DEBUG_KEYSTORE_PATH")
  val stagingDebugKeystore = stagingDebugKeystorePath?.let(::file)?.takeIf { it.exists() }
  val stagingDebugKeystorePassword = System.getenv("STAGING_DEBUG_KEYSTORE_PASSWORD")

  signingConfigs {
    if (stagingDebugKeystore != null && !stagingDebugKeystorePassword.isNullOrBlank()) {
      create("stagingDebug") {
        storeFile = stagingDebugKeystore
        storePassword = stagingDebugKeystorePassword
        keyAlias = "clickandsaveai-staging"
        keyPassword = stagingDebugKeystorePassword
      }
    }

    if (productionReleaseCandidate && productionUploadKeystore != null) {
      create("productionUpload") {
        storeFile = productionUploadKeystore
        storePassword = requireProductionInput("PRODUCTION_UPLOAD_STORE_PASSWORD")
        keyAlias = requireProductionInput("PRODUCTION_UPLOAD_KEY_ALIAS")
        keyPassword = requireProductionInput("PRODUCTION_UPLOAD_KEY_PASSWORD")
      }
    }
  }

  buildTypes {
    debug {
      if (stagingDebugKeystore != null && !stagingDebugKeystorePassword.isNullOrBlank()) {
        signingConfig = signingConfigs.getByName("stagingDebug")
      }
    }

    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      resValue("string", "google_web_client_id", productionWebClientId)
      if (productionReleaseCandidate) {
        signingConfig = signingConfigs.getByName("productionUpload")
      }
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  buildFeatures {
    compose = true
    buildConfig = true
    resValues = true
  }

  testOptions {
    unitTests { isIncludeAndroidResources = true }
  }
}

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.firebase.auth)
  implementation("com.google.firebase:firebase-functions")
  implementation("com.google.firebase:firebase-messaging")
  implementation("com.google.firebase:firebase-appcheck-playintegrity")
  debugImplementation("com.google.firebase:firebase-appcheck-debug")
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  implementation("com.google.android.gms:play-services-auth:21.6.0")
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
  implementation(libs.okhttp)

  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)

  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)

  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)

  "ksp"(libs.androidx.room.compiler)
}
