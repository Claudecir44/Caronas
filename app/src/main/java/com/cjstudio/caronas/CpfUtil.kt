package com.cjstudio.caronas

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText

// CPF do MOTORISTA (passageiro não tem CPF): obrigatório no cadastro de
// motorista e vinculado à conta no servidor (ver registrarMotorista em
// functions/index.js e VinculoMotorista.kt). A validação dos dígitos aqui é
// só pra avisar rápido; a validação de verdade (e a unicidade) é do servidor.
object CpfUtil {

    fun somenteDigitos(valor: String?): String = valor.orEmpty().filter { it.isDigit() }

    // Confere os dois dígitos verificadores (e recusa 111.111.111-11 e afins).
    fun valido(cpf: String?): Boolean {
        val d = somenteDigitos(cpf)
        if (d.length != 11 || d.all { it == d[0] }) return false
        for (tamanho in intArrayOf(9, 10)) {
            var soma = 0
            for (i in 0 until tamanho) soma += (d[i] - '0') * (tamanho + 1 - i)
            val digito = ((soma * 10) % 11) % 10
            if (digito != d[tamanho] - '0') return false
        }
        return true
    }

    // 12345678909 -> 123.456.789-09 (parcial enquanto digita).
    fun formatar(cpf: String?): String {
        val d = somenteDigitos(cpf).take(11)
        val sb = StringBuilder()
        d.forEachIndexed { i, c ->
            if (i == 3 || i == 6) sb.append('.')
            if (i == 9) sb.append('-')
            sb.append(c)
        }
        return sb.toString()
    }

    // Máscara 000.000.000-00 no campo enquanto a pessoa digita.
    fun aplicarMascara(campo: EditText) {
        campo.addTextChangedListener(object : TextWatcher {
            private var atualizando = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (atualizando) return
                val formatado = formatar(s?.toString())
                if (formatado == s?.toString()) return
                atualizando = true
                campo.setText(formatado)
                campo.setSelection(formatado.length)
                atualizando = false
            }
        })
    }
}
