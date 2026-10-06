package com.cjstudio.caronas

import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

// Campos "Valor de cada trecho" (Lajeado -> Canoas, Canoas -> Porto Alegre,
// ...) de uma carona com paradas — usados no Oferecer Carona e no editar
// oferta. Cada valor vai pra ParadaRota.valorTrechoAnterior do ponto onde o
// trecho termina; sem paradas o bloco some (só existe a viagem inteira).
object ValoresPorTrechoUtil {

    // (Re)monta uma linha por trecho entre cidades consecutivas. Mantém o
    // que já estava digitado num trecho que continua igual (mesmas duas
    // cidades) — reconstruir a cada letra digitada não pode apagar valores.
    fun montar(bloco: View, container: LinearLayout, cidades: List<String>, valoresIniciais: List<Double?> = emptyList()) {
        val anteriores = mutableMapOf<String, String>()
        for (i in 0 until container.childCount) {
            val linha = container.getChildAt(i)
            (linha.tag as? String)?.let { chave -> anteriores[chave] = linha.findViewById<EditText>(R.id.etValorTrecho).text.toString() }
        }
        container.removeAllViews()
        if (cidades.size < 3) {
            bloco.visibility = View.GONE
            return
        }
        bloco.visibility = View.VISIBLE
        val context = container.context
        cidades.zipWithNext().forEachIndexed { indice, (de, ate) ->
            val linha = LayoutInflater.from(context).inflate(R.layout.item_valor_trecho, container, false)
            val chave = "$de|$ate"
            linha.tag = chave
            linha.findViewById<TextView>(R.id.tvRotuloValorTrecho).text =
                context.getString(R.string.oferecer_parada_valor_rotulo, de.ifBlank { "?" }, ate.ifBlank { "?" })
            val texto = anteriores[chave] ?: valoresIniciais.getOrNull(indice)?.let { formatar(it) } ?: ""
            linha.findViewById<EditText>(R.id.etValorTrecho).setText(texto)
            container.addView(linha)
        }
    }

    fun preencher(container: LinearLayout, valores: List<Double>) {
        valores.forEachIndexed { indice, valor ->
            container.getChildAt(indice)?.findViewById<EditText>(R.id.etValorTrecho)?.setText(formatar(valor))
        }
    }

    // Valores digitados, um por trecho (null = em branco, calcula pela
    // distância). Devolve null e marca o erro se algum preenchido for inválido.
    fun ler(container: LinearLayout): List<Double?>? {
        val valores = mutableListOf<Double?>()
        for (i in 0 until container.childCount) {
            val et = container.getChildAt(i).findViewById<EditText>(R.id.etValorTrecho)
            val texto = et.text.toString().trim()
            if (texto.isEmpty()) {
                valores.add(null)
                continue
            }
            val valor = texto.replace(",", ".").toDoubleOrNull()
            if (valor == null || valor <= 0) {
                et.error = container.context.getString(R.string.oferecer_erro_valor_parada)
                et.requestFocus()
                return null
            }
            valores.add(valor)
        }
        return valores
    }

    // Grava cada valor no ponto onde o trecho termina (rota[i + 1]).
    fun aplicarNaRota(rota: List<ParadaRota>, valores: List<Double?>): List<ParadaRota> =
        if (rota.size < 3) rota.map { it.copy(valorTrechoAnterior = null) }
        else rota.mapIndexed { i, parada -> if (i == 0) parada.copy(valorTrechoAnterior = null) else parada.copy(valorTrechoAnterior = valores.getOrNull(i - 1)) }

    private fun formatar(valor: Double) = String.format(Locale("pt", "BR"), "%.2f", valor)
}
