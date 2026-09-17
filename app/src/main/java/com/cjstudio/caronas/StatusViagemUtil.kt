package com.cjstudio.caronas

// "Concluída" nunca é um status gravado no Firestore (Carona.status só tem
// "ativa"/"cancelada", Solicitacao.status só tem "solicitada"/"confirmada"/
// "cancelada") — é sempre INFERIDO comparando a data/hora de partida com
// agora, exibido só nas telas (ver MinhaOfertaAdapter, MinhaViagemAdapter,
// ViagemAdminAdapter, ViagemPassageiroAdminAdapter, e o equivalente em
// public/index.html). Trocar o campo de verdade no Firestore não foi
// escolhido de propósito: outras regras (ex. firestore.rules exigindo
// status == 'confirmada' pra poder avaliar, ver /avaliacoes) dependem do
// valor original continuar do jeito que está.
//
// Tolerância de 10 minutos depois do horário de partida (pedido explícito
// do usuário — "após 10 minutos do horário de partida") antes de virar
// "Concluída", em vez de instantâneo no exato horário marcado.
object StatusViagemUtil {
    private const val TOLERANCIA_MS = 10 * 60 * 1000L

    fun jaConcluida(dataHoraPartida: Long?): Boolean {
        if (dataHoraPartida == null) return false
        return System.currentTimeMillis() > dataHoraPartida + TOLERANCIA_MS
    }
}
