import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

// Same credential sources as the Flutter Android project: environment first, then key.properties.
val keystoreProperties = Properties().apply {
    rootProject.file("key.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
fun signingValue(env: String, key: String, fallbackEnv: String? = null): String? =
    System.getenv(env) ?: fallbackEnv?.let(System::getenv) ?: keystoreProperties.getProperty(key)

val releaseStoreFile = signingValue("KEYSTORE", "storeFile")?.let(::file)
val releaseStorePassword = signingValue("KEYSTORE_PASSWORD", "storePassword", "REVIA_KS_PASS")
val releaseKeyAlias = signingValue("KEY_ALIAS", "keyAlias")
val releaseKeyPassword = signingValue("KEY_PASSWORD", "keyPassword", "REVIA_KS_PASS")
val hasReleaseSigning = releaseStoreFile?.isFile == true &&
    !releaseStorePassword.isNullOrEmpty() && !releaseKeyAlias.isNullOrEmpty() && !releaseKeyPassword.isNullOrEmpty()

android {
    namespace = "app.awavenue.c001apk"
    compileSdk {
        version = release(37) { minorApiLevel = 0 }
    }
    buildToolsVersion = "37.0.0"

    defaultConfig {
        // Installs beside the Flutter package until the Compose app reaches feature parity.
        applicationId = "app.awavenue.c001apk.coui"
        minSdk = 23
        targetSdk = 37
        versionCode = 20100
        versionName = "2.1.0"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            optimization.enable = true
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        jniLibs {
            excludes += "lib/*/libandroidx.graphics.path.so"
        }
    }
}

// Never produce an unsigned or debug-signed release when credentials are missing.
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        if (!hasReleaseSigning) {
            throw GradleException(
                "Release signing is not configured. Set KEYSTORE, KEY_ALIAS and KEYSTORE_PASSWORD/KEY_PASSWORD (or REVIA_KS_PASS); see docs/DEVELOPMENT.md.",
            )
        }
    }
}

dependencies {
    implementation(libs.coui.ui)
    implementation(libs.coui.preference)
    implementation(libs.coui.icons)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.jbcrypt)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.androidx.work.runtime)

    testImplementation(libs.junit)
}
