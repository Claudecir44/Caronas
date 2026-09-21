package com.cjstudio.caronas

// Estado do chat entre motorista e passageiro de UMA solicitação de vaga.
//
// Regra (a mesma de firestore.rules:chatAberto, que é a trava de verdade —
// isto aqui só decide o que a tela mostra):
//  - ABERTO: da hora em que o passageiro solicita a vaga até 6 horas depois
//    da chegada prevista da viagem. Os dois podem mandar mensagem.
//  - CANCELADO: motorista OU passageiro cancelou a solicitação — fecha de
//    novo, pra sempre.
//  - ENCERRADO: passaram as 6 horas após a chegada prevista (ou a
//    solicitação já não existe).
// Fechado (CANCELADO/ENCERRADO) = as mensagens continuam visíveis pros dois,
// só não dá mais pra enviar nem apagar nada.
enum class StatusChat { ABERTO, CANCELADO, ENCERRADO }

// Lançada quando se tenta abrir/criar uma conversa cuja solicitação já não
// está com o chat aberto (ver ChatCaronaRepository.buscarOuCriarConversa).
class ChatIndisponivelException(val status: StatusChat) : IllegalStateException("Chat indisponível: $status")

object ChatUtil {
    const val JANELA_APOS_CHEGADA_MS = 6L * 60 * 60 * 1000

    // "solicitacao" null = documento apagado (ex.: "excluir viagem" do
    // histórico) — sem solicitação não há como validar o chat, então fecha.
    fun status(solicitacao: Solicitacao?, agora: Long = System.currentTimeMillis()): StatusChat {
        if (solicitacao == null) return StatusChat.ENCERRADO
        if (solicitacao.status == "cancelada") return StatusChat.CANCELADO
        // Solicitações antigas não têm chegadaPrevistaEm — cai pra hora de
        // partida (igual à regra do servidor).
        val chegada = solicitacao.chegadaPrevistaEm ?: solicitacao.dataHoraPartida ?: 0L
        return if (agora <= chegada + JANELA_APOS_CHEGADA_MS) StatusChat.ABERTO else StatusChat.ENCERRADO
    }
}
