package com.cjstudio.caronas

// Um ponto no trajeto de uma Carona — origem (ordem 0), qualquer parada
// intermediária, ou destino (última ordem). "endereco" é opcional: o
// motorista pode informar só a cidade (como antes) ou também o endereço
// exato de onde vai sair/chegar naquela cidade (ex.: "Rodoviária, Portão
// 3"). Serializable pelo mesmo motivo de Veiculo.kt: Carona/Solicitacao
// (que carregam List<ParadaRota>) viajam inteiras numa Intent — sem isso
// aqui, o app fechava ao abrir o chat de qualquer viagem com paradas
// cadastradas (mesmo bug já corrigido em Veiculo.kt nesta sessão).
data class ParadaRota(
    var cidade: String? = null,
    var cidadeBusca: String? = null,
    var endereco: String? = null,
    var ordem: Int = 0
) : java.io.Serializable {
    // Construtor vazio necessário para o Firestore.
    constructor() : this(null, null, null, 0)
}
