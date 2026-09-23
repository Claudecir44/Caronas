import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

// Credenciais de assinatura de release lidas de keystore.properties (fora do
// controle de versão) — nunca hardcoded aqui, mesmo padrão do Match. Se o
// arquivo não existir (ex.: checkout novo sem as credenciais), a build de
// release cai para a chave de debug em vez de falhar.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
val temKeystoreDeRelease = keystorePropertiesFile.exists()
if (temKeystoreDeRelease) {
    // Leitura explícita em UTF-8 — o carregamento padrão de .properties do
    // Java assume ISO-8859-1, o que corrompe senha com acento/caractere
    // especial mesmo estando digitada certa no arquivo.
    keystoreProperties.load(keystorePropertiesFile.inputStream().reader(Charsets.UTF_8))
}

// google-services precisa de app/google-services.json pra sequer configurar
// (senão o build inteiro falha na fase de configuração, mesmo pra tarefas
// que não têm nada a ver com Firebase). O projeto Firebase do Caronas já
// existe (caronas-6b0c4) e o arquivo já está commitado — esse "if" só
// continua aqui como salvaguarda pra nunca quebrar o build inteiro caso
// esse arquivo suma de um checkout novo por engano.
val temGoogleServices = file("google-services.json").exists()
if (temGoogleServices) {
    apply(plugin = "com.google.gms.google-services")
    // Crashlytics depende do google-services (mesmo arquivo de configuração).
    apply(plugin = "com.google.firebase.crashlytics")
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
        versionCode = 3
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

    signingConfigs {
        if (temKeystoreDeRelease) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
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
            signingConfig = if (temKeystoreDeRelease) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
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
    implementation(libs.firebase.functions)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.crashlytics)
    // Badge numérico no ícone do launcher (ver AppIconBadgeUtil.kt) — a
    // API pública do Android só faz o CANAL de notificação pedir um badge
    // (setShowBadge, já configurado), mas quem desenha o número em cima do
    // ícone é o launcher de cada fabricante, cada um com sua própria API
    // proprietária (MIUI, Samsung, Sony, etc.). ShortcutBadger abstrai
    // isso — detecta o launcher e manda o broadcast/intent certo.
    implementation(libs.shortcutbadger)

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
