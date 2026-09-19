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

// Liga um EditText (cidade OU endereço — o "tipo" decide o que o servidor sugere,
// ver IAutocompleteRepository) a uma lista de sugestões que aparece embaixo
// dele enquanto a pessoa digita, no estilo BlaBlaCar/Uber. Reaproveitado em
// toda tela com campo de cidade/endereço: OferecerCaronaActivity (origem,
// destino, e cada parada adicionada dinamicamente) e o diálogo de busca em
// TelaCaronasActivity.
object AutocompleteEnderecoUtil {

    private const val ATRASO_DEBOUNCE_MS = 400L
    private const val MINIMO_CARACTERES = 3

    // campoCidade: só pro ENDERECO — o campo de cidade ao lado, cujo texto entra na
    // busca pra sugerir ruas da cidade certa. Cidade: a sugestão aparece como
    // "Viamão - RS", mas ao tocar só "Viamão" vai pro campo (a busca de caronas
    // compara o nome da cidade).
    fun ligar(
        activity: AppCompatActivity,
        editText: EditText,
        repositorio: IAutocompleteRepository,
        tipo: TipoAutocomplete,
        campoCidade: EditText? = null
    ) {
        val popup = ListPopupWindow(editText.context)
        popup.anchorView = editText

        var job: Job? = null
        var ignorarProximaBusca = false
        var sugestoesAtuais: List<SugestaoEndereco> = emptyList()

        popup.setOnItemClickListener { _, _, posicao, _ ->
            val valor = sugestoesAtuais.getOrNull(posicao)?.valor ?: return@setOnItemClickListener
            ignorarProximaBusca = true
            editText.setText(valor)
            editText.setSelection(valor.length)
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
                    val sugestoes = repositorio.autocompletar(consulta, tipo, campoCidade?.text?.toString())
                    // O texto pode ter mudado de novo enquanto a chamada
                    // estava em andamento (ou o campo pode ter perdido o
                    // foco) — não mostra popup desatualizado nesse caso.
                    if (sugestoes.isEmpty() || !editText.isFocused || editText.text.toString().trim() != consulta) {
                        popup.dismiss()
                        return@launch
                    }
                    sugestoesAtuais = sugestoes
                    popup.setAdapter(ArrayAdapter(editText.context, android.R.layout.simple_list_item_1, sugestoes.map { it.texto }))
                    popup.show()
                }
            }
        })

        editText.setOnFocusChangeListener { _, temFoco -> if (!temFoco) popup.dismiss() }
    }
}
