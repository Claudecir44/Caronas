package com.cjstudio.caronas

import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

// Uma oferta de carona publicada por um motorista. "status" começa sempre
// "ativa" — cancelamento/conclusão (tela "Suas Viagens") são passos
// futuros que ainda não existem.
//
// motoristaNome/motoristaFotoUrl/veiculo são uma cópia (denormalizada) do
// perfil no momento da publicação, gravada em CaronaRepository
// .publicarCarona — evita ter que buscar o documento de cada motorista pra
// montar a lista de resultados da busca. cidadeOrigemBusca/
// cidadeDestinoBusca são as mesmas cidades normalizadas (ver
// TextoUtil.normalizar), usadas só pra comparar na busca —
// cidadeOrigem/cidadeDestino continuam com o texto original pra exibição.
data class Carona(
    @get:PropertyName("id") @set:PropertyName("id")
    var id: String? = null,

    var motoristaId: String? = null,
    var motoristaNome: String? = null,
    var motoristaFotoUrl: String? = null,
    var veiculo: Veiculo? = null,
    var cidadeOrigem: String? = null,
    var cidadeDestino: String? = null,
    var cidadeOrigemBusca: String? = null,
    var cidadeDestinoBusca: String? = null,
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
    constructor() : this(null, null, null, null, null, null, null, null, null, null, null, 1, null, null, "ativa", null)
}
