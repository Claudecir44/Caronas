package com.cjstudio.caronas

// Documento motoristasVinculo/{uid}: a identidade do MOTORISTA vinculada à
// conta — nome completo, CPF, telefone e e-mail, nenhum repetido entre
// motoristas (ver vincularIdentidadeMotorista em functions/index.js).
// Só a Cloud Function grava; o dono e o admin leem (firestore.rules).
// "cpf" só com dígitos (11) — use CpfUtil.formatar pra exibir. Conta apenas
// de passageiro não tem esse documento.
data class VinculoMotorista(
    var cpf: String? = null,
    var nomeCompleto: String? = null,
    var telefone: String? = null,
    var email: String? = null
) {
    // Construtor vazio necessário para o Firestore.
    constructor() : this(null, null, null, null)
}
