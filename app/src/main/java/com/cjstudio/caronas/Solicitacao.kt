package com.cjstudio.caronas

import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

// Pedido de vaga de um passageiro numa carona (criado ao tocar "Solicitar
// vaga" nos resultados da busca). Os dados da rota/data/valor são uma
// cópia (denormalizada) da Carona no momento do pedido — assim
// "Minhas Viagens" continua mostrando o histórico certo mesmo que a
// carona original seja editada ou excluída depois pelo motorista.
//
// "status": "solicitada" (aguardando o motorista confirmar) ->
// "confirmada" (motorista aceitou, ver MinhasOfertasActivity) ou
// "cancelada" (passageiro ou motorista cancelou). "concluída" não é um
// status próprio — é inferido no app (ver MinhaViagemAdapter) comparando
// dataHoraPartida com a data atual.
//
// passageiroNome/passageiroFotoUrl são uma cópia do perfil do passageiro
// no momento do pedido, no mesmo espírito de Carona.motoristaNome — evita
// ter que buscar o documento de cada passageiro pra montar a lista de
// "Solicitações Recebidas" do motorista. motoristaNome/motoristaFotoUrl/
// veiculo são a mesma ideia, só que do motorista, preenchidos só na hora
// que ele confirma (ver CaronaRepository... na verdade SolicitacaoRepository
// .confirmarSolicitacao) — o passageiro só precisa saber quem vai buscá-lo
// depois que a viagem é aceita, não antes.
// Serializable só pra poder passar o objeto inteiro numa Intent (ver
// ChatCaronaActivity) — não afeta a serialização do Firestore, que usa
// reflection própria (toObject/PropertyName), ignorando essa interface.
data class Solicitacao(
    @get:PropertyName("id") @set:PropertyName("id")
    var id: String? = null,

    var caronaId: String? = null,
    var passageiroId: String? = null,
    var passageiroNome: String? = null,
    var passageiroFotoUrl: String? = null,
    var motoristaId: String? = null,
    var motoristaNome: String? = null,
    var motoristaFotoUrl: String? = null,
    var veiculo: Veiculo? = null,
    var cidadeOrigem: String? = null,
    var cidadeDestino: String? = null,
    var dataHoraPartida: Long? = null,
    var valorPago: Double? = null,
    var status: String = "solicitada",

    @ServerTimestamp
    var criadoEm: Date? = null
) : java.io.Serializable {
    // Construtor vazio necessário para o Firestore (mesmo padrão de Usuario.kt).
    constructor() : this(null, null, null, null, null, null, null, null, null, null, null, null, null, "solicitada", null)
}
