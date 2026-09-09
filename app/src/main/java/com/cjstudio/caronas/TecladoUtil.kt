package com.cjstudio.caronas

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

// Com targetSdk 36 (Android 15+), o app desenha em modo "edge-to-edge" por
// padrão. Em versões recentes (confirmado no Android 16), nem
// WindowCompat.setDecorFitsSystemWindows(window, true) restaura de forma
// confiável o redimensionamento automático de windowSoftInputMode=
// "adjustResize" — os últimos campos de formulários dentro de ScrollView
// continuam escondidos atrás do teclado. A solução robusta é escutar o
// inset real do teclado (IME) e aplicar como padding inferior na view
// rolável, empurrando o conteúdo pra cima na medida exata.
//
// Chamar depois do setContentView, na raiz rolável da tela (ScrollView).
fun View.ajustarPaddingParaTeclado() {
    val paddingInferiorOriginal = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        val teclado = windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        val barrasSistema = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
        view.setPadding(
            view.paddingLeft,
            view.paddingTop,
            view.paddingRight,
            paddingInferiorOriginal + maxOf(teclado - barrasSistema, 0)
        )
        windowInsets
    }
}
