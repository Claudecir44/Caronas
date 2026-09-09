package com.cjstudio.caronas

import android.graphics.PorterDuff
import android.text.InputType
import android.view.MotionEvent
import android.widget.EditText
import androidx.core.content.ContextCompat

private const val AREA_EXTRA_TOQUE_DP = 16

// Ícone de "mostrar/ocultar senha" à direita do campo (compound drawable).
// Sempre começa oculto (asteriscos); tocar no ícone alterna a visibilidade
// sem mexer no texto digitado nem na posição do cursor. Mesmo padrão do
// PasswordToggleUtil.kt do Match.
fun EditText.habilitarToggleSenha(tintColor: Int? = null) {
    val tipoOculto = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    val tipoVisivel = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD

    inputType = tipoOculto
    aplicarIconeToggleSenha(oculto = true, tintColor = tintColor)

    setOnTouchListener { v, event ->
        if (event.action == MotionEvent.ACTION_UP) {
            val drawableEnd = compoundDrawables[2]
            if (drawableEnd != null) {
                val areaIcone = drawableEnd.bounds.width() + paddingEnd +
                    (AREA_EXTRA_TOQUE_DP * resources.displayMetrics.density).toInt()
                if (event.x >= (v.width - areaIcone)) {
                    val estaOculto = inputType == tipoOculto
                    inputType = if (estaOculto) tipoVisivel else tipoOculto
                    setSelection(text.length)
                    aplicarIconeToggleSenha(oculto = !estaOculto, tintColor = tintColor)
                    return@setOnTouchListener true
                }
            }
        }
        false
    }
}

private fun EditText.aplicarIconeToggleSenha(oculto: Boolean, tintColor: Int?) {
    val idIcone = if (oculto) R.drawable.ic_visibility else R.drawable.ic_visibility_off
    val icone = ContextCompat.getDrawable(context, idIcone)?.mutate()
    if (tintColor != null) {
        icone?.setColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
    }
    setCompoundDrawablesWithIntrinsicBounds(null, null, icone, null)
    compoundDrawablePadding = (8 * resources.displayMetrics.density).toInt()
}
