package com.cjstudio.caronas

import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

// Uma oferta de carona publicada por um motorista. "status" começa sempre
// "ativa" — cancelamento/conclusão (tela "Suas Viagens") e busca/match com
// passageiro (tela "Procurar") são passos futuros que ainda não existem.
data class Carona(
    @get:PropertyName("id") @set:PropertyName("id")
    var id: String? = null,

    var motoristaId: String? = null,
    var cidadeOrigem: String? = null,
    var cidadeDestino: String? = null,
    var distanciaKm: Double? = null,
    var dataHoraPartida: Long? = null,
    var vagas: Int = 1,
    var valorPorVaga: Double? = null,
    var valorSugerido: Double? = null,
    var status: String = "ativa",

    @ServerTimestamp
    var criadoEm: Date? = null
) {
    // Construtor vazio necessário para o Firestore (mesmo padrão de Usuario.kt).
    constructor() : this(null, null, null, null, null, null, 1, null, null, "ativa", null)
}
