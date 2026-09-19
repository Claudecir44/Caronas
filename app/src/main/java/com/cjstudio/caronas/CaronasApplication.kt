package com.cjstudio.caronas

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dagger.hilt.android.HiltAndroidApp

// Ponto de entrada do Hilt. Fase 1: só isso — App Check, heartbeat de
// presença e proteção contra captura de tela (padrões usados no Match)
// ficam pra quando essas necessidades aparecerem de verdade aqui.
//
// Edge-to-edge (Android 15+): com targetSdk 35+ o Android força TODA Activity a
// desenhar por baixo da barra de status e da barra de navegação (e do recorte
// da câmera, sobretudo em paisagem), sem opção de desligar pelo tema. Nenhuma
// tela tratava isso — títulos, botões e o ⚙️ ficavam sob as barras do sistema.
// Aqui o espaço é reservado UMA vez, no container raiz do conteúdo de toda
// Activity (mesmo padrão do Match), devolvendo a tela ao comportamento de antes
// do Android 15: a faixa das barras mostra o fundo da janela (escuro, com ícones
// claros) e o layout da tela ocupa só o que sobra. Em Android antigo, sem
// edge-to-edge forçado, os insets chegam zerados e nada muda.
//
// O teclado é tratado à parte, por tela, em View.ajustarPaddingParaTeclado
// (TecladoUtil.kt) — que soma só o que o teclado ocupa ALÉM das barras, porque
// o das barras já está aqui.
@HiltAndroidApp
class CaronasApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                val conteudoRaiz = activity.findViewById<View>(android.R.id.content) ?: return
                ViewCompat.setOnApplyWindowInsetsListener(conteudoRaiz) { view, insets ->
                    val reservado = insets.getInsets(
                        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                    )
                    view.setPadding(reservado.left, reservado.top, reservado.right, reservado.bottom)
                    // Devolve os insets inteiros (sem consumir): o ajuste do teclado
                    // nas telas de formulário ainda precisa enxergá-los.
                    insets
                }
            }

            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
