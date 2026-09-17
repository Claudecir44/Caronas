package com.cjstudio.caronas

import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListPopupWindow
import androidx.lifecycle.lifecycleScope
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Liga um EditText (cidade OU endereço, mesma função pros dois — ver
// IAutocompleteRepository) a uma lista de sugestões que aparece embaixo
// dele enquanto a pessoa digita, no estilo BlaBlaCar/Uber. Reaproveitado em
// toda tela com campo de cidade/endereço: OferecerCaronaActivity (origem,
// destino, e cada parada adicionada dinamicamente) e o diálogo de busca em
// TelaCaronasActivity.
object AutocompleteEnderecoUtil {

    private const val ATRASO_DEBOUNCE_MS = 400L
    private const val MINIMO_CARACTERES = 3

    fun ligar(activity: AppCompatActivity, editText: EditText, repositorio: IAutocompleteRepository) {
        val popup = ListPopupWindow(editText.context)
        popup.anchorView = editText

        var job: Job? = null
        var ignorarProximaBusca = false

        popup.setOnItemClickListener { _, _, posicao, _ ->
            val adapter = popup.listView?.adapter as? ArrayAdapter<*>
            val texto = adapter?.getItem(posicao) as? String ?: return@setOnItemClickListener
            ignorarProximaBusca = true
            editText.setText(texto)
            editText.setSelection(texto.length)
            popup.dismiss()
        }

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                job?.cancel()
                if (ignorarProximaBusca) {
                    ignorarProximaBusca = false
                    popup.dismiss()
                    return
                }
                val consulta = s?.toString()?.trim().orEmpty()
                if (consulta.length < MINIMO_CARACTERES) {
                    popup.dismiss()
                    return
                }
                job = activity.lifecycleScope.launch {
                    delay(ATRASO_DEBOUNCE_MS)
                    val sugestoes = repositorio.autocompletar(consulta)
                    // O texto pode ter mudado de novo enquanto a chamada
                    // estava em andamento (ou o campo pode ter perdido o
                    // foco) — não mostra popup desatualizado nesse caso.
                    if (sugestoes.isEmpty() || !editText.isFocused || editText.text.toString().trim() != consulta) {
                        popup.dismiss()
                        return@launch
                    }
                    popup.setAdapter(ArrayAdapter(editText.context, android.R.layout.simple_list_item_1, sugestoes))
                    popup.show()
                }
            }
        })

        editText.setOnFocusChangeListener { _, temFoco -> if (!temFoco) popup.dismiss() }
    }
}
