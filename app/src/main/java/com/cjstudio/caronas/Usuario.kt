package com.cjstudio.caronas

import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

// Mesmo usuário serve pra motorista e passageiro — "motorista" é só um
// papel marcado no cadastro, não um tipo de conta separado (ver
// CadastroCaronasActivity). "id" nunca é lido do Firestore (é o próprio
// nome do documento, usuarios/{uid}) — sempre setado manualmente depois
// da leitura, igual ao padrão do Usuario.kt do Match.
data class Usuario(
    @get:PropertyName("id") @set:PropertyName("id")
    var id: String? = null,

    var nomeCompleto: String? = null,
    var email: String? = null,
    var telefone: String? = null,
    var fotoUrl: String? = null,
    var motorista: Boolean = false,
    var veiculo: Veiculo? = null,

    // Quantas caronas esse usuário já ofereceu como motorista (contador
    // simples, incrementado a cada "oferecer carona" bem-sucedido — ver
    // CaronaRepository.publicarCarona). Só estatística: NÃO é mais o que
    // conta pras 10 grátis (ver caronasRealizadas).
    var caronasOferecidas: Int = 0,

    // Viagens que contam pras 10 grátis: só as que tiveram pelo menos 1
    // passageiro confirmado. Mantido só pelo servidor
    // (functions/index.js:recontarViagensRealizadas) — o cliente nunca
    // grava valor diferente (firestore.rules recusa). Da 11ª em diante
    // precisa de acessoMotoristaExpiraEm válido (ver AcessoMotoristaUtil/
    // firestore.rules: permiteOferecerCarona()).
    var caronasRealizadas: Int = 0,

    // Parte de caronasRealizadas que veio de uma conta excluída do mesmo
    // CPF (restaurarCreditoMotoristaPorCpf). Também só do servidor.
    var caronasRealizadasHerdadas: Int = 0,

    // Preenchido quando o motorista paga o acesso avulso (R$15,99 = 30
    // dias, sem renovação automática — mesmo modelo "pagamento único" já
    // usado no Premium do Match, ver concederAcessoMotorista em
    // functions/index.js). Data FIXA de expiração (nunca @ServerTimestamp —
    // isso reescreveria pra "agora" a cada save do documento, e não é isso
    // que queremos: é "agora + 30 dias", calculado uma vez no momento do
    // pagamento). Nulo = nunca pagou (só vale enquanto durarem as 10
    // gratuitas).
    var acessoMotoristaExpiraEm: Date? = null,

    @ServerTimestamp
    var criadoEm: Date? = null
) {
    // Construtor vazio necessário para o Firestore.
    constructor() : this(null, null, null, null, null, false, null, 0, 0, 0, null, null)
}
