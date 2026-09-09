package com.cjstudio.caronas

// Só existe (não-nulo) no documento do usuário quando usuario.motorista == true.
data class Veiculo(
    var modelo: String? = null,
    var marca: String? = null,
    var cor: String? = null,
    var placa: String? = null
) {
    // Construtor vazio necessário para o Firestore.
    constructor() : this(null, null, null, null)
}
