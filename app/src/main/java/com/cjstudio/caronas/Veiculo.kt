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

    // Um Veiculo() "vazio" (todos os campos em branco) já apareceu salvo em
    // documentos de usuário — não-nulo, então passava batido em qualquer
    // checagem "!= null" (checkbox de Meu Perfil, gate de login como
    // motorista), mas sem placa/modelo pra mostrar em lugar nenhum. Sempre
    // checar isso em vez de só != null.
    fun estaPreenchido(): Boolean =
        !modelo.isNullOrBlank() && !marca.isNullOrBlank() && !cor.isNullOrBlank() && !placa.isNullOrBlank()
}
