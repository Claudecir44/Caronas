package com.cjstudio.caronas

import android.net.Uri
import kotlinx.coroutines.flow.Flow

interface IUsuarioRepository {
    // Cria a conta (Auth + doc Firestore) e devolve o uid criado. Em
    // qualquer falha no meio do caminho, desfaz o que já tiver sido criado.
    suspend fun cadastrar(usuario: Usuario, senha: String): Result<String>

    suspend fun login(email: String, senha: String): Result<Usuario>

    suspend fun buscarUsuarioLogado(): Result<Usuario>

    // Perfil de QUALQUER usuário (não só o logado) — usado pela tela de
    // perfil público (PerfilPublicoActivity), aberta ao tocar no nome/foto
    // de alguém na busca, em Minhas Viagens ou em Solicitações Recebidas.
    // firestore.rules já libera leitura de "usuarios/{id}" pra qualquer
    // autenticado, então isso não precisa de regra nova.
    suspend fun buscarUsuarioPorId(uid: String): Result<Usuario>

    // Grava com merge — nunca sobrescreve o doc inteiro.
    suspend fun atualizarPerfil(usuario: Usuario): Result<Unit>

    suspend fun uploadFotoPerfil(uid: String, uri: Uri): Result<String>

    // Atualização parcial (só o campo "motorista") usada pela escolha de
    // papel na tela de login — não precisa do Usuario inteiro carregado.
    suspend fun atualizarPapelMotorista(uid: String, motorista: Boolean): Result<Unit>

    // Reautentica com a senha atual antes de apagar foto + doc + conta.
    suspend fun excluirContaPropria(senhaAtual: String): Result<Unit>

    // Usado só pra desfazer um cadastro que falhou no meio (ex.: upload de
    // foto deu erro) — apaga a conta recém-criada sem exigir reautenticação
    // (o usuário acabou de logar nela agora mesmo).
    suspend fun excluirContaRecemCriada(uid: String): Result<Unit>

    suspend fun logout()

    suspend fun usuarioLogadoId(): String?

    // Envia o e-mail de redefinição de senha do próprio Firebase Auth —
    // usado pela caixa "Suporte" da tela de login (opção "Esqueci minha
    // senha"). Nunca revela se o e-mail existe ou não (mesmo comportamento
    // padrão do Firebase).
    suspend fun enviarRedefinicaoSenha(email: String): Result<Unit>

    // Reenvia o e-mail de verificação de cadastro via Cloud Function
    // "reenviarVerificacaoEmail" (ver functions/index.js) — usado pela
    // caixa "Suporte" da tela de login (opção "Reenviar e-mail de validação").
    suspend fun reenviarEmailVerificacao(email: String): Result<Unit>

    // Envia uma reclamação, sugestão ou denúncia (ver Manifestacao.kt,
    // EnviarManifestacaoActivity) — grava direto no Firestore (sem Cloud
    // Function, mesmo espírito de "criar é livre, só responder/excluir é
    // sensível"), gated por firestore.rules pra exigir usuarioId == uid de
    // quem está enviando.
    suspend fun enviarManifestacao(tipo: String, nomeCompleto: String, email: String, telefone: String, mensagem: String): Result<Unit>

    // Cria a preferência de pagamento (Mercado Pago) do acesso avulso do
    // motorista (R$15,99 = 30 dias, ver AssinaturaMotoristaActivity/
    // functions/index.js:createPaymentPreferenceMotorista) e devolve a URL
    // de checkout (initPoint) pra abrir no navegador.
    suspend fun iniciarPagamentoAcessoMotorista(): Result<String>

    // Histórico de pagamentos do PRÓPRIO motorista logado (coleção
    // pagamentosMotorista, gravada só pela Cloud Function), do mais recente
    // pro mais antigo — alimenta a lista "Meus pagamentos" da tela de acesso pago.
    suspend fun buscarMeusPagamentosMotorista(): Result<List<PagamentoMotorista>>

    // Sessão atual (Firebase Auth) — as telas nunca falam com o FirebaseAuth
    // direto, sempre por aqui.
    fun uidLogado(): String?
    fun estaLogado(): Boolean

    // Validade do acesso pago do motorista em tempo real (millis; não emite
    // enquanto o campo não existir) — a tela de pagamento escuta isso pra saber
    // que o webhook confirmou a compra (ver AssinaturaMotoristaActivity).
    fun escutarAcessoMotorista(): Flow<Long>
}
