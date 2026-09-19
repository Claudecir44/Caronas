package com.cjstudio.caronas

import android.graphics.Rect
import android.view.View
import android.widget.ScrollView
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
// Só o padding não bastava: ele abre espaço embaixo, mas não move o scroll
// sozinho, então o campo em foco (geralmente o último do formulário) podia
// continuar atrás do teclado até o usuário rolar manualmente. Por isso,
// junto com o padding, rola até o campo focado ficar visível acima do
// teclado.
//
// O espaço das barras do sistema (status e navegação) NÃO é tratado aqui: é
// reservado uma vez, para toda Activity, no container raiz do conteúdo (ver
// CaronasApplication). Como o teclado é medido a partir da borda da tela e
// inclui a barra de navegação, aqui entra só o que ele ocupa ALÉM da barra —
// senão a altura da barra seria contada duas vezes.
//
// Chamar depois do setContentView, na raiz rolável ou na raiz da tela.
fun View.ajustarPaddingParaTeclado() {
    val paddingInferiorOriginal = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        val teclado = windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        val barrasSistema = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
        val paddingTeclado = maxOf(teclado - barrasSistema, 0)
        view.setPadding(
            view.paddingLeft,
            view.paddingTop,
            view.paddingRight,
            paddingInferiorOriginal + paddingTeclado
        )

        if (paddingTeclado > 0 && view is ScrollView) {
            val campoFocado = view.findFocus()
            if (campoFocado != null) {
                view.post {
                    val retangulo = Rect()
                    campoFocado.getDrawingRect(retangulo)
                    view.offsetDescendantRectToMyCoords(campoFocado, retangulo)
                    view.smoothScrollTo(0, retangulo.bottom - view.height + paddingTeclado)
                }
            }
        }

        windowInsets
    }
}
