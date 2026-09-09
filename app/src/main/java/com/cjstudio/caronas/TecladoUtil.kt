package com.cjstudio.caronas

import android.app.Activity
import androidx.core.view.WindowCompat

// Com targetSdk 36 (Android 15+), o app passa a desenhar em modo
// "edge-to-edge" por padrão — isso quebra o android:windowSoftInputMode=
// "adjustResize" declarado no manifest (o sistema não encolhe mais a tela
// sozinho quando o teclado abre, então os últimos campos de formulários
// dentro de ScrollView ficam escondidos atrás do teclado). Chamar isto no
// onCreate, antes do setContentView, restaura o comportamento clássico de
// redimensionamento automático nas telas com formulário.
fun Activity.restaurarRedimensionamentoComTeclado() {
    WindowCompat.setDecorFitsSystemWindows(window, true)
}
