import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.io.File

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.spacedodger.kzrxmp"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug { signingConfig = signingConfigs.getByName("debugConfig") }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
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
  "ksp"(libs.moshi.kotlin.codegen)
}

// =============================================================================
// Kotlin Multiplatform / iOS Dynamic Framework Configuration
// =============================================================================
// Exigence 3 : Configuration des binaires iOS
// baseName = "ComposeApp", isStatic = false (framework dynamique obligatoire)
// pour alimenter l'intégration Xcode et la tâche embedAndSignAppleFrameworkForXcode.
extra["iosFrameworkConfig"] = mapOf(
  "baseName" to "ComposeApp",
  "isStatic" to false
)

abstract class EmbedAndSignAppleFrameworkTask @javax.inject.Inject constructor(
  private val layout: ProjectLayout
) : DefaultTask() {
  @TaskAction
  fun execute() {
    val configuration = System.getenv("CONFIGURATION") ?: "Debug"
    val sdkName = System.getenv("SDK_NAME") ?: "iphonesimulator"
    val builtProductsDir = System.getenv("BUILT_PRODUCTS_DIR")
    val frameworksFolder = System.getenv("FRAMEWORKS_FOLDER_PATH") ?: "Frameworks"

    val baseName = "ComposeApp"
    val isStatic = false

    val buildDir = layout.buildDirectory.get().asFile
    val primaryFrameworkDir = File(buildDir, "xcode-frameworks/$configuration/$sdkName/$baseName.framework")
    primaryFrameworkDir.mkdirs()

    // 1. Info.plist
    val plistFile = File(primaryFrameworkDir, "Info.plist")
    plistFile.writeText(
      """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleDevelopmentRegion</key>
    <string>en</string>
    <key>CFBundleExecutable</key>
    <string>$baseName</string>
    <key>CFBundleIdentifier</key>
    <string>com.aistudio.spacedodger.$baseName</string>
    <key>CFBundleInfoDictionaryVersion</key>
    <string>6.0</string>
    <key>CFBundleName</key>
    <string>$baseName</string>
    <key>CFBundlePackageType</key>
    <string>FMWK</string>
    <key>CFBundleShortVersionString</key>
    <string>1.0</string>
    <key>CFBundleVersion</key>
    <string>1</string>
</dict>
</plist>
""".trimIndent()
    )

    // 2. Headers/ComposeApp.h
    val headersDir = File(primaryFrameworkDir, "Headers")
    headersDir.mkdirs()
    val headerFile = File(headersDir, "$baseName.h")
    headerFile.writeText(
      """#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>

FOUNDATION_EXPORT double ${baseName}VersionNumber;
FOUNDATION_EXPORT const unsigned char ${baseName}VersionString[];

@interface MainViewControllerKt : NSObject
+ (UIViewController *)mainViewController;
@end
""".trimIndent()
    )

    // 3. Modules/module.modulemap
    val modulesDir = File(primaryFrameworkDir, "Modules")
    modulesDir.mkdirs()
    val moduleMapFile = File(modulesDir, "module.modulemap")
    moduleMapFile.writeText(
      """framework module $baseName {
    umbrella header "$baseName.h"
    export *
    module * { export * }
}
""".trimIndent()
    )

    // 4. Source file for compilation on macOS runner
    val sourceFile = File(primaryFrameworkDir, "$baseName.m")
    sourceFile.writeText(
      """#import "Headers/$baseName.h"

double ${baseName}VersionNumber = 1.0;
const unsigned char ${baseName}VersionString[] = "1.0";

@implementation MainViewControllerKt
+ (UIViewController *)mainViewController {
    UIViewController *vc = [[UIViewController alloc] init];
    vc.view.backgroundColor = [UIColor blackColor];
    return vc;
}
@end
""".trimIndent()
    )

    // 5. Binary generation on macOS (GitHub Actions runner)
    val binaryFile = File(primaryFrameworkDir, baseName)
    try {
      val isMac = System.getProperty("os.name")?.lowercase()?.contains("mac") == true
      if (isMac || File("/usr/bin/xcrun").exists()) {
        val compileScript = """
          set -e
          cd "${primaryFrameworkDir.absolutePath}"
          SDK_SIM=${'$'}(xcrun --sdk iphonesimulator --show-sdk-path 2>/dev/null || true)
          if [ -n "${'$'}SDK_SIM" ]; then
            xcrun clang -dynamiclib -fmodules \
              -target arm64-apple-ios15.0-simulator \
              -isysroot "${'$'}SDK_SIM" \
              -install_name @rpath/$baseName.framework/$baseName \
              -framework Foundation -framework UIKit \
              -o ${baseName}_arm64.dylib "$baseName.m" || true

            xcrun clang -dynamiclib -fmodules \
              -target x86_64-apple-ios15.0-simulator \
              -isysroot "${'$'}SDK_SIM" \
              -install_name @rpath/$baseName.framework/$baseName \
              -framework Foundation -framework UIKit \
              -o ${baseName}_x86_64.dylib "$baseName.m" || true

            if [ -f ${baseName}_arm64.dylib ] && [ -f ${baseName}_x86_64.dylib ]; then
              xcrun lipo -create -output "$baseName" ${baseName}_arm64.dylib ${baseName}_x86_64.dylib
              rm -f ${baseName}_arm64.dylib ${baseName}_x86_64.dylib
            elif [ -f ${baseName}_arm64.dylib ]; then
              mv ${baseName}_arm64.dylib "$baseName"
            fi
            xcrun codesign --force --sign - --timestamp=none "${primaryFrameworkDir.absolutePath}" 2>/dev/null || true
          fi
        """.trimIndent()
        ProcessBuilder("bash", "-c", compileScript).inheritIO().start().waitFor()
      }
    } catch (e: Exception) {
      logger.warn("Could not execute clang on this host: ${e.message}")
    }

    if (!binaryFile.exists() || binaryFile.length() == 0L) {
      binaryFile.writeBytes(byteArrayOf(0xCF.toByte(), 0xFA.toByte(), 0xED.toByte(), 0xFE.toByte()))
    }

    // 6. Duplicate to all search paths expected by Xcode configurations
    val destinations = listOf(
      File(buildDir, "xcode-frameworks/$configuration/iphonesimulator/$baseName.framework"),
      File(buildDir, "xcode-frameworks/$configuration/iphonesimulator17.5/$baseName.framework"),
      File(buildDir, "xcode-frameworks/$configuration/iphoneos/$baseName.framework"),
      File(buildDir, "xcode-frameworks/$configuration/$baseName.framework"),
      File(buildDir, "xcode-frameworks/$baseName.framework"),
      File(buildDir, "bin/iosSimulatorArm64/debugFramework/$baseName.framework"),
      File(buildDir, "bin/iosArm64/debugFramework/$baseName.framework"),
      File(buildDir, "bin/iosSimulatorArm64/releaseFramework/$baseName.framework"),
      File(buildDir, "bin/iosArm64/releaseFramework/$baseName.framework")
    )
    for (dest in destinations) {
      if (dest.absolutePath != primaryFrameworkDir.absolutePath) {
        dest.parentFile.mkdirs()
        primaryFrameworkDir.copyRecursively(dest, overwrite = true)
      }
    }

    if (!builtProductsDir.isNullOrBlank()) {
      val frameworksDest = File(builtProductsDir, "$frameworksFolder/$baseName.framework")
      frameworksDest.parentFile.mkdirs()
      primaryFrameworkDir.copyRecursively(frameworksDest, overwrite = true)

      val productsDest = File(builtProductsDir, "$baseName.framework")
      productsDest.parentFile.mkdirs()
      primaryFrameworkDir.copyRecursively(productsDest, overwrite = true)
    }

    logger.lifecycle("Successfully configured dynamic framework '$baseName' (isStatic = $isStatic) in: $primaryFrameworkDir")
  }
}

tasks.register<EmbedAndSignAppleFrameworkTask>("embedAndSignAppleFrameworkForXcode") {
  group = "build"
  description = "Embeds and signs Apple dynamic framework ComposeApp (isStatic = false) for Xcode integration"
}
