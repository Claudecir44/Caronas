package com.cjstudio.caronas

import android.content.Context

// Sexo do usuário (Usuario.sexo) e a preferência do motorista sobre quais
// passageiros aceita numa carona (Carona.aceitaPassageiros). Os valores
// gravados no Firestore são sempre estes — firestore.rules confere os
// mesmos ("homem"/"mulher"/"ambos"). Carona antiga, sem o campo, vale como
// AMBOS; usuário antigo, sem sexo, precisa completar o cadastro (ver
// TelaCaronasActivity.exigirSexoSeFaltar) antes de oferecer ou buscar.
object SexoUtil {
    const val HOMEM = "homem"
    const val MULHER = "mulher"
    const val AMBOS = "ambos"

    fun rotuloSexo(context: Context, sexo: String?): String = when (sexo) {
        HOMEM -> context.getString(R.string.sexo_homem)
        MULHER -> context.getString(R.string.sexo_mulher)
        else -> context.getString(R.string.sexo_nao_informado)
    }

    // A carona aparece/aceita pedido desse passageiro? (mesma regra de
    // firestore.rules:aceitaPassageiro, que é a trava de verdade).
    fun caronaAceita(carona: Carona, sexoPassageiro: String?): Boolean {
        val aceita = carona.aceitaPassageiros ?: AMBOS
        return aceita == AMBOS || aceita == sexoPassageiro
    }

    // Filtro "Motorista: Homem/Mulher/Ambos" da busca do passageiro.
    fun motoristaCombina(carona: Carona, filtroMotorista: String): Boolean =
        filtroMotorista == AMBOS || carona.motoristaSexo == filtroMotorista

    // Texto curto pros cards ("Somente mulheres"), null quando aceita todos.
    fun rotuloRestricao(context: Context, aceitaPassageiros: String?): String? = when (aceitaPassageiros) {
        HOMEM -> context.getString(R.string.carona_somente_homens)
        MULHER -> context.getString(R.string.carona_somente_mulheres)
        else -> null
    }
}
