import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseSigningFile = providers.environmentVariable("GLIDVIO_SIGNING_PROPERTIES").orNull
    ?.let { file(it) }
    ?: file("${System.getProperty("user.home")}/.config/glidvio/release-signing.properties")
val releaseSigning = Properties().apply {
    if (releaseSigningFile.isFile) releaseSigningFile.inputStream().use { load(it) }
}
val signingFields = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
val hasReleaseSigning = signingFields.all { !releaseSigning.getProperty(it).isNullOrBlank() }
if (releaseSigningFile.isFile && !hasReleaseSigning) {
    throw GradleException("Release signing properties must define: ${signingFields.joinToString()}")
}

android {
    namespace = "com.glidvio.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.glidvio.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17; isCoreLibraryDesugaringEnabled = true }
    buildFeatures { compose = true; buildConfig = true }
    signingConfigs {
        if (hasReleaseSigning) create("release") {
            storeFile = file(releaseSigning.getProperty("storeFile"))
            storePassword = releaseSigning.getProperty("storePassword")
            keyAlias = releaseSigning.getProperty("keyAlias")
            keyPassword = releaseSigning.getProperty("keyPassword")
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-dev"
        }
        release {
            isDebuggable = false
            isJniDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }
    testBuildType = providers.gradleProperty("glidvio.testBuildType").getOrElse("debug")
    packaging { resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/INDEX.LIST") }
}
dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")
    implementation(platform("androidx.compose:compose-bom:2026.02.00"))
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.github.teamnewpipe:NewPipeExtractor:v0.26.5") {
        // The extractor uses Rhino's interpreter directly, not the desktop javax.script adapter.
        exclude(group = "org.mozilla", module = "rhino-engine")
    }
    implementation("org.jsoup:jsoup:1.22.2")
    implementation("androidx.media3:media3-exoplayer:1.10.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.10.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.10.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.10.1")
    implementation("androidx.media3:media3-ui:1.10.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    // Extractor marks Protobuf as runtime-only; its generated message APIs need
    // the same pinned superclasses on the deterministic test compile classpath.
    androidTestImplementation("com.google.protobuf:protobuf-javalite:4.35.1")
    // AndroidX Test references these annotations but omits their artifact from
    // its published POM. Supply them when R8 optimizes the release test APK.
    androidTestImplementation("com.google.errorprone:error_prone_annotations:2.30.0")
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
