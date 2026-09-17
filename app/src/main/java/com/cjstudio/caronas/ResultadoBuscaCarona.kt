package com.cjstudio.caronas

// Uma Carona encontrada numa busca, já resolvida pro TRECHO que o
// passageiro procurou — não a rota inteira do motorista. Ver
// TelaCaronasActivity.buscarCaronas: o preço e a rota mostrados ao
// passageiro (CaronaResultadoAdapter) são sempre origem->destino
// BUSCADOS, nunca a rota completa nem o valor cobrado de quem for do
// início ao fim — uma parada em outro trecho, pra pegar outro passageiro,
// não pode mudar quanto ESTE passageiro paga nem por onde ele vê que vai
// passar.
data class ResultadoBuscaCarona(
    val carona: Carona,
    val indiceOrigem: Int,
    val indiceDestino: Int,
    val cidadeEmbarque: String,
    val cidadeDesembarque: String,
    val valorTrecho: Double,
    // Vagas ainda livres pro trecho buscado (ver
    // SolicitacaoRepository.vagasDisponiveis) — nunca a capacidade total do
    // carro (carona.vagas), que não desconta quem já ocupou esse trecho.
    val vagasDisponiveis: Int
)
