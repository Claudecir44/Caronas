package com.cjstudio.caronas

import android.net.Uri
import kotlinx.coroutines.flow.Flow

// Falhas de negócio do login de admin — a tela troca cada uma pela mensagem
// certa (as outras caem no erro genérico).
class EmailNaoVerificadoException(mensagem: String) : Exception(mensagem)
class AcessoAdminRestritoException : Exception("Acesso restrito a administradores.")

interface IAdminRepository {
    // Checa se o uid tem entrada na coleção "admins" (ver firestore.rules,
    // função ehAdmin()) — chamado logo após o login na flavor admin, pra
    // barrar qualquer conta de usuário comum que tente entrar ali.
    suspend fun souAdmin(uid: String): Result<Boolean>

    // Todos os usuários cadastrados, sem paginação — volume atual do
    // Caronas não justifica cursor de página (ver plano). Quem chama
    // separa em Motoristas/Passageiros filtrando client-side por
    // usuario.veiculo (ver Usuario.kt: "motorista" é só o papel da última
    // sessão de login, não um tipo de conta permanente).
    suspend fun listarTodosUsuarios(): Result<List<Usuario>>

    // Todas as caronas oferecidas, sem paginação, mais recentes primeiro.
    suspend fun listarTodasCaronas(): Result<List<Carona>>

    // Todas as solicitações (viagens do lado do passageiro), sem
    // paginação — ver AdministracaoCaronasActivity.mostrarViagensPassageiro.
    // Precisa do bypass ehAdmin() em firestore.rules (solicitacoes
    // normalmente só é legível pelos dois participantes).
    suspend fun listarTodasSolicitacoes(): Result<List<Solicitacao>>

    // Usuários cujo nome completo, telefone, e-mail ou CPF (motorista; texto
    // com 11 dígitos, com ou sem máscara) contém o texto
    // buscado (sem diferenciar maiúsculas/acentos no nome) — busca
    // client-side em cima de listarTodosUsuarios (mesmo espírito "sem
    // paginação" do resto do painel, adequado ao volume atual do Caronas).
    // Usado pela seção "Mensagens" do painel admin, pra achar de quem ver
    // as conversas.
    suspend fun buscarUsuariosPorTexto(texto: String): Result<List<Usuario>>

    // Vínculo de identidade (com o CPF) de um motorista — sucesso com null =
    // sem vínculo (passageiro, ou motorista antigo sem CPF informado).
    suspend fun buscarVinculoMotorista(uid: String): Result<VinculoMotorista?>

    // Todas as mensagens de TODAS as conversas de um usuário (como
    // motorista ou como passageiro), mais recentes primeiro, cada uma já
    // acompanhada do resumo da conversa a que pertence (rota, quem é o
    // outro participante) — ver MensagemAdminInfo. Precisa do bypass
    // ehAdmin() em firestore.rules (conversas/mensagens normalmente só são
    // legíveis pelos dois participantes).
    suspend fun listarMensagensDoUsuario(usuarioId: String): Result<List<MensagemAdminInfo>>

    // Cria uma conta de admin nova (Auth + admins/{uid}) via Cloud Function
    // "cadastrarAdmin" — nunca um "create" direto do cliente em
    // admins/{uid} (ver firestore.rules). Exige a senha do administrador
    // master (fixa, só existe em functions/.env) e, quando já existe pelo
    // menos um admin, também a permissão "administradores" de quem está
    // chamando (bootstrap do primeiro admin de todos continua liberado só
    // pela senha master, sem sessão). Devolve o uid criado (o chamador
    // ainda precisa logar com email/senha pra poder subir a foto — ver
    // CadastroAdminCaronasActivity).
    suspend fun cadastrarAdmin(
        nome: String,
        sobrenome: String,
        email: String,
        telefone: String,
        cpf: String,
        senha: String,
        role: String,
        permissoes: Map<String, Boolean>,
        senhaAutorizacao: String
    ): Result<String>

    // Edita nome/sobrenome/telefone/cpf de um admin JÁ existente (achado
    // via buscarAdminPorCpf) — via Cloud Function "atualizarAdmin", mesma
    // trava dupla (permissão "administradores" + senha do master). Não mexe
    // em e-mail, senha de login nem foto.
    suspend fun atualizarAdmin(
        uid: String,
        nome: String,
        sobrenome: String,
        telefone: String,
        cpf: String,
        senhaAutorizacao: String
    ): Result<Unit>

    // Edita só papel (admin/colaborador) + permissões de um admin já
    // existente — via Cloud Function "atualizarPermissoesAdmin", separada
    // dos dados básicos (mesmo padrão do Match), mesma trava dupla.
    suspend fun atualizarPermissoesAdmin(
        uid: String,
        role: String,
        permissoes: Map<String, Boolean>,
        senhaAutorizacao: String
    ): Result<Unit>

    // Registro de ações administrativas sensíveis (ver LogAdministracao.kt) —
    // usado pela seção "Administração" de RelatoriosCaronasActivity.
    suspend fun listarLogsAdministracao(): Result<List<LogAdministracao>>

    // Remove só a entrada admins/{uid} (revoga o acesso ao painel) — não
    // apaga a conta Firebase Auth nem o cadastro de usuário comum. Via
    // Cloud Function "excluirAdmin", mesma trava de senha do master.
    suspend fun excluirAdmin(uid: String, senhaAutorizacao: String): Result<Unit>

    // Remove um usuário comum (motorista ou passageiro) POR COMPLETO — via
    // Cloud Function "excluirUsuario" (mesma trava de senha do master das
    // outras ações destrutivas do painel). Apaga o documento de perfil, a
    // conta de autenticação (libera o e-mail pra um cadastro novo) e todo
    // dado que referencia esse uid em qualquer coleção (solicitações,
    // ocupação de vaga, caronas oferecidas, conversas/mensagens,
    // avaliações, fotos no Storage) — nada fica órfão pra travar um
    // recadastro. Usado pelo toque-e-segure na lista de Passageiros (ver
    // AdministracaoCaronasActivity) e pelo botão equivalente no painel web.
    suspend fun excluirUsuario(uid: String, senhaAutorizacao: String): Result<Unit>

    suspend fun buscarAdminLogado(uid: String): Result<Admin>

    // Todos os admins cadastrados, sem paginação — mesmo espírito "sem
    // paginação" de listarTodosUsuarios/listarTodasCaronas. Usado só pelo
    // painel de Relatórios (ver RelatoriosCaronasActivity) pra contar o
    // total de administradores.
    suspend fun listarTodosAdmins(): Result<List<Admin>>

    // Busca por CPF (tela de "Editar Perfil" do admin master) — leitura
    // direta no Firestore, já liberada pra qualquer admin (ver
    // firestore.rules: admins/{adminId} allow read: if ehAdmin()); null se
    // não achar ninguém com esse CPF.
    suspend fun buscarAdminPorCpf(cpf: String): Result<Admin?>

    // Só o próprio admin pode chamar isso pro próprio uid (ver
    // firestore.rules: admins/{adminId} allow update só no campo fotoUrl,
    // só pelo dono). Sobe a foto pro Storage e grava a URL.
    suspend fun atualizarFotoAdmin(uid: String, uri: Uri): Result<String>

    // Troca a foto de OUTRO admin/colaborador (uid diferente do logado) —
    // storage.rules não libera escrita direto em fotos_perfil/{uid} de
    // outra pessoa, então isso sobe a foto numa pasta que o chamador PODE
    // escrever e delega o resto (copiar pro destino, apagar a origem,
    // gravar fotoUrl) pra Cloud Function atualizarFotoAdminAutorizado, via
    // Admin SDK. Mesma trava de senha master das outras edições de admin.
    suspend fun atualizarFotoAdminDeOutro(uid: String, uri: Uri, senhaAutorizacao: String): Result<String>

    // "Ver Sugestões e Reclamações" — reclamações/sugestões/denúncias ainda
    // NÃO arquivadas, mais recentes primeiro (ver Manifestacao.kt). Leitura
    // direta, liberada pra qualquer admin (firestore.rules: allow read: if
    // ehAdmin()), mesmo espírito de listarTodosUsuarios/listarTodasCaronas.
    suspend fun listarManifestacoes(): Result<List<Manifestacao>>

    // Só as arquivadas (botão "Arquivados" no fim da lista acima).
    suspend fun listarManifestacoesArquivadas(): Result<List<Manifestacao>>

    // Envia a resposta por e-mail pro autor (Cloud Function
    // "responderManifestacao" — precisa do servidor pra mandar o e-mail e
    // pra gravar o CPF de quem respondeu sem confiar no que o cliente
    // manda) e marca status="respondido" + data/hora + CPF do admin.
    suspend fun responderManifestacao(manifestacaoId: String, resposta: String): Result<Unit>

    // Só pode arquivar depois de já respondida (checado aqui E de novo em
    // firestore.rules, defesa em profundidade) — update direto do cliente,
    // não precisa de Cloud Function (não é uma ação "perigosa" como
    // excluir, só organiza a lista).
    suspend fun arquivarManifestacao(manifestacaoId: String): Result<Unit>

    // Exclusão definitiva — via Cloud Function "admExcluirManifestacao",
    // mesma trava de senha do administrador master das outras ações
    // destrutivas do painel (excluirAdmin/excluirUsuario).
    suspend fun excluirManifestacao(manifestacaoId: String, senhaAutorizacao: String): Result<Unit>

    // Edita nome completo/telefone/veículo de um motorista ou passageiro —
    // via Cloud Function "admAtualizarUsuario" (Admin SDK, o cliente não
    // tem permissão de escrever no cadastro de outra pessoa — ver
    // firestore.rules), mesma trava de senha do administrador master das
    // outras ações do painel. "veiculo" nulo quando é edição de um
    // passageiro (sem veículo pra editar).
    suspend fun atualizarUsuario(uid: String, nomeCompleto: String, telefone: String, veiculo: Veiculo?, senhaAutorizacao: String): Result<Unit>

    // Badge do botão "Sugestões" no dashboard (ver
    // AdministracaoCaronasActivity) — total de manifestações ainda não
    // respondidas e não arquivadas, em tempo real (mesmo padrão de
    // ISolicitacaoRepository.escutarContagemPendentes).
    fun escutarContagemManifestacoesPendentes(): Flow<Int>

    // Histórico completo de cobranças dos R$15,99/30 dias do motorista
    // (ver FinanceiroCaronasActivity), sem paginação — mesmo espírito "sem
    // paginação" do resto do painel. Leitura direta, liberada pra qualquer
    // admin (firestore.rules: pagamentosMotorista allow read: if ehAdmin()).
    suspend fun listarPagamentosMotorista(): Result<List<PagamentoMotorista>>

    // Sessão atual (Firebase Auth) — as telas nunca falam com o FirebaseAuth direto.
    fun uidLogado(): String?

    // Login completo da flavor admin: Auth + e-mail verificado (reenvia com
    // cooldown) + entrada em admins/{uid}; guarda o uid no DataStore. Devolve o
    // uid. Falhas: EmailNaoVerificadoException, AcessoAdminRestritoException ou
    // qualquer outra (rede, senha errada).
    suspend fun loginAdmin(email: String, senha: String): Result<String>

    // Depois de criar um admin (Cloud Function cadastrarAdmin): entra com a senha
    // recém-criada pra poder subir a foto (storage.rules exige o próprio uid) e
    // mandar o e-mail de verificação, e sai em seguida.
    suspend fun finalizarCadastroAdmin(uid: String, email: String, senha: String, foto: Uri?): Result<Unit>
}
