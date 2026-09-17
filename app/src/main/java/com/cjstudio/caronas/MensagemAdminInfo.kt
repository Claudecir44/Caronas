package com.cjstudio.caronas

// Uma mensagem + o resumo da conversa a que ela pertence (rota, quem é o
// motorista/passageiro) — usado só pela seção "Mensagens" do painel admin
// (ver AdminRepository.listarMensagensDoUsuario), onde uma mesma lista
// mistura mensagens de VÁRIAS conversas do usuário buscado, então cada
// item precisa carregar seu próprio contexto de conversa junto.
data class MensagemAdminInfo(
    val mensagem: MensagemCarona,
    val conversa: ConversaCarona
)
