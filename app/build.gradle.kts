plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

// The tag workflow opts in explicitly; ordinary builds keep the source version.
val ciChannel = providers.gradleProperty("trackAtReleaseChannel").orNull
val ciCodeText = providers.gradleProperty("trackAtDevVersionCode").orNull
require(ciChannel == null || ciChannel in setOf("dev", "production")) {
    "trackAtReleaseChannel must be dev or production"
}
require((ciChannel == "dev") == (ciCodeText != null)) {
    "trackAtDevVersionCode is required for dev and forbidden otherwise"
}
val devCode = ciCodeText?.let {
    require(Regex("[1-9][0-9]{0,9}").matches(it)) { "Invalid Dev version code" }
    requireNotNull(it.toIntOrNull()?.takeIf { code -> code <= 2_100_000_000 }) {
        "Dev version code exceeds the Android distribution limit"
    }
}

val sourceVersion = providers.fileContents(rootProject.layout.projectDirectory.file("version.properties")).asText.get()
val versionMetadata = Regex("""versionName=((?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*))\r?\nversionCode=([1-9][0-9]{0,9})(?:\r?\n)?""")
    .matchEntire(sourceVersion) ?: error("Invalid source version.properties")
val publicVersionName = versionMetadata.groupValues[1]
val publicVersionCode = requireNotNull(versionMetadata.groupValues[2].toIntOrNull()?.takeIf { it <= 2_100_000_000 }) {
    "Public version code exceeds the Android distribution limit"
}

android {
    namespace = "com.jaguar.attendancetracker"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.jaguar.attendancetracker"
        minSdk = 29
        targetSdk = 36
        versionCode = publicVersionCode
        versionName = publicVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
            signingConfig = if (ciChannel == null) signingConfigs.getByName("debug") else null
            if (ciChannel == "dev") {
                applicationIdSuffix = ".dev"
                versionNameSuffix = "-dev"
            }
        }
        getByName("debug") {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        viewBinding = true
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        devCode?.let { code -> variant.outputs.forEach { it.versionCode.set(code) } }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.room.common.jvm)
    implementation(libs.androidx.room.runtime.android)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.hilt.android)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    ksp(libs.hilt.android.compiler)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)
    implementation(libs.gson)
    implementation(libs.font.awesome)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation(libs.reorderable)
}