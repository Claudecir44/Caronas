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
// "confirmada" (motorista aceitou, ver TelaCaronasActivity.confirmarSolicitacao) ou
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
    // Cidade/endereço de embarque e desembarque ESCOLHIDOS pelo passageiro —
    // se a Carona não tem paradas (rota simples, o caso comum), são sempre
    // a origem/destino inteiros da carona, exatamente como antes. Se a
    // Carona tem paradas intermediárias, podem ser qualquer par de paradas
    // da rota (ver TelaCaronasActivity, escolha de trecho antes de
    // solicitar). indiceOrigemNaRota/indiceDestinoNaRota guardam a posição
    // desse trecho dentro de Carona.paradas — é o que permite calcular
    // quantas vagas do carro aquele trecho específico ocupa (ver
    // SolicitacaoRepository.vagasDisponiveis).
    var cidadeOrigem: String? = null,
    var cidadeDestino: String? = null,
    var enderecoOrigem: String? = null,
    var enderecoDestino: String? = null,
    var indiceOrigemNaRota: Int = 0,
    var indiceDestinoNaRota: Int = 1,
    var dataHoraPartida: Long? = null,
    var valorPago: Double? = null,
    var status: String = "solicitada",

    // Preenchido só quando status vira "cancelada" — "passageiro" ou
    // "motorista", quem tomou a ação (ver SolicitacaoRepository
    // .cancelarSolicitacao/cancelarComoMotorista). A Cloud Function
    // notificarViagemCancelada usa esse campo pra saber quem AVISAR (o
    // participante que NÃO cancelou) — sem ele não dava pra saber, o
    // status sozinho não diz quem tomou a ação.
    var canceladoPor: String? = null,

    // Vira true quando o passageiro abre "Minhas Viagens" com essa
    // solicitação já confirmada — alimenta o badge de notificação em cima
    // do botão "Minhas Viagens" (ver SolicitacaoRepository
    // .marcarConfirmacoesComoVistas/TelaCaronasActivity.carregarMinhasViagens).
    // Diferente do contador de mensagens não lidas do chat (que zera um
    // número), aqui é um flag por solicitação — o "status" nunca muda de
    // volta, então precisa de um campo à parte pra saber "já vi essa
    // confirmação" (sem isso o badge nunca zeraria).
    var confirmacaoVista: Boolean = false,

    // Mesma ideia de confirmacaoVista, só que pro cancelamento — sempre
    // false quando canceladoPor é gravado (ver SolicitacaoRepository
    // .cancelarInterno), marcado true quando o OUTRO participante (quem não
    // cancelou) abre a própria lista (marcarCancelamentosComoVistos, pro
    // motorista, ou marcarConfirmacoesComoVistas, que passou a cobrir
    // cancelamento também, pro passageiro). Alimenta o badge combinado de
    // "Minhas Ofertas"/"Minhas Viagens" e, por tabela, o badge do ícone do
    // app na tela inicial (ver AppIconBadgeUtil.kt).
    var canceladoVisto: Boolean = false,

    @ServerTimestamp
    var criadoEm: Date? = null
) : java.io.Serializable {
    // Construtor vazio necessário para o Firestore (mesmo padrão de Usuario.kt).
    constructor() : this(
        null, null, null, null, null, null, null, null, null,
        null, null, null, null, 0, 1, null, null, "solicitada", null, false, false, null
    )
}
