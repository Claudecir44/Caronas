package com.cjstudio.caronas

// Só existe (não-nulo) no documento do usuário quando usuario.motorista == true.
// Serializable pelo mesmo motivo de Solicitacao.kt: Solicitacao.veiculo só é
// preenchido quando o motorista confirma (ver SolicitacaoRepository), e
// Solicitacao inteira viaja numa Intent (ver TelaCaronasActivity.abrirChatViagem/
// TelaCaronasActivity -> ChatCaronaActivity). Sem esta interface aqui,
// tentar serializar uma Solicitacao com veiculo != null (ou seja, qualquer
// viagem já confirmada) lançava NotSerializableException ao chamar
// startActivity — o app inteiro fechava ao tocar em "Chat" numa viagem
// confirmada, mesmo com Solicitacao já sendo Serializable (o Kotlin não
// verifica em tempo de compilação se os TIPOS dos campos também são).
data class Veiculo(
    var modelo: String? = null,
    var marca: String? = null,
    var cor: String? = null,
    var placa: String? = null
) : java.io.Serializable {
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
