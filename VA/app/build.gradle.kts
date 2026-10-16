import java.io.File
import java.net.URL
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}
fun localPropertyAsBuildConfig(name: String): String {
    val value = localProperties.getProperty(name, "")
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
    return "\"$value\""
}

val openWakeWordAssetsDir = layout.buildDirectory.dir("generated/openWakeWordAssets")
val openWakeWordModelUrls = linkedMapOf(
    "hey_jarvis_v0.1.onnx" to "https://github.com/dscripka/openWakeWord/releases/download/v0.5.1/hey_jarvis_v0.1.onnx",
    "melspectrogram.onnx" to "https://github.com/dscripka/openWakeWord/releases/download/v0.5.1/melspectrogram.onnx",
    "embedding_model.onnx" to "https://github.com/dscripka/openWakeWord/releases/download/v0.5.1/embedding_model.onnx"
)
val downloadOpenWakeWordModels by tasks.registering {
    val outputDir = openWakeWordAssetsDir
    outputs.dir(outputDir)
    outputs.upToDateWhen {
        openWakeWordModelUrls.keys.all { name ->
            val file = outputDir.get().file(name).asFile
            file.isFile && file.length() > 0L
        }
    }
    doLast {
        val directory = outputDir.get().asFile.apply { mkdirs() }
        openWakeWordModelUrls.forEach { (name, url) ->
            val destination = File(directory, name)
            if (!destination.isFile || destination.length() == 0L) {
                logger.lifecycle("Downloading openWakeWord model: $name")
                URL(url).openStream().use { input ->
                    destination.outputStream().use { output -> input.copyTo(output) }
                }
                check(destination.length() > 0L) { "Downloaded model is empty: $name" }
            }
        }
    }
}

android {
    namespace = "com.example.voiceassistant"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.voiceassistant"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SUPABASE_URL", localPropertyAsBuildConfig("SUPABASE_URL"))
        buildConfigField("String", "SUPABASE_ANON_KEY", localPropertyAsBuildConfig("SUPABASE_ANON_KEY"))
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    sourceSets.getByName("main").assets.srcDir(openWakeWordAssetsDir)

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(downloadOpenWakeWordModels)
}

tasks.configureEach {
    if (name.startsWith("merge") && name.endsWith("Assets")) {
        dependsOn(downloadOpenWakeWordModels)
    }
}

dependencies {
    implementation("xyz.rementia:openwakeword:0.1.5")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.compose.ui.tooling)
}
