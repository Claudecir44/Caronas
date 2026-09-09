plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

// google-services precisa de app/google-services.json pra sequer configurar
// (senão o build inteiro falha na fase de configuração, mesmo pra tarefas
// que não têm nada a ver com Firebase). O projeto Firebase do Caronas ainda
// não existe (bloqueado por cota de projetos GCP da conta) — aplica o
// plugin só quando o arquivo já estiver presente, pra o resto do app
// continuar compilando normalmente enquanto isso não é resolvido.
val temGoogleServices = file("google-services.json").exists()
if (temGoogleServices) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.cjstudio.caronas"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.cjstudio.caronas"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    packaging {
        resources {
            excludes += "/META-INF/NOTICE.md"
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/INDEX.LIST"
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
        }
        release {
            optimization {
                enable = false
            }
        }
    }

    flavorDimensions += listOf("type")

    productFlavors {
        create("usuario") {
            applicationId = "com.cjstudio.caronas.usuario"
            versionName = "1.0-usuario"
            resValue("string", "app_name", "Caronas")
            buildConfigField("String", "TIPO", "\"usuario\"")
        }
        create("admin") {
            applicationId = "com.cjstudio.caronas.admin"
            versionName = "1.0-admin"
            resValue("string", "app_name", "Admin Caronas")
            buildConfigField("String", "TIPO", "\"admin\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        resValues = true
    }
}

configurations.all {
    resolutionStrategy {
        // Mesmo ajuste do Match (ver app/build.gradle.kts de lá): o compilador
        // Kotlin embutido no AGP 9 é fixo numa versão; algumas libs mais novas
        // puxam um kotlin-stdlib mais recente do que esse compilador entende.
        force("org.jetbrains.kotlin:kotlin-stdlib:2.2.10")
    }
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.storage)

    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.core)
    implementation(libs.androidx.cardview)

    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    implementation(libs.lifecycle.runtime.ktx)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.datastore.preferences)

    implementation(libs.coil)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
