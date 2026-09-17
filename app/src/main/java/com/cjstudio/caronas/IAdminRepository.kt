package com.cjstudio.caronas

import android.net.Uri

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

    // Usuários cujo nome completo, telefone ou e-mail contém o texto
    // buscado (sem diferenciar maiúsculas/acentos no nome) — busca
    // client-side em cima de listarTodosUsuarios (mesmo espírito "sem
    // paginação" do resto do painel, adequado ao volume atual do Caronas).
    // Usado pela seção "Mensagens" do painel admin, pra achar de quem ver
    // as conversas.
    suspend fun buscarUsuariosPorTexto(texto: String): Result<List<Usuario>>

    // Todas as mensagens de TODAS as conversas de um usuário (como
    // motorista ou como passageiro), mais recentes primeiro, cada uma já
    // acompanhada do resumo da conversa a que pertence (rota, quem é o
    // outro participante) — ver MensagemAdminInfo. Precisa do bypass
    // ehAdmin() em firestore.rules (conversas/mensagens normalmente só são
    // legíveis pelos dois participantes).
    suspend fun listarMensagensDoUsuario(usuarioId: String): Result<List<MensagemAdminInfo>>

    // Cria uma conta de admin nova (Auth + admins/{uid}) via Cloud Function
    // "cadastrarAdmin" — nunca um "create" direto do cliente em
    // admins/{uid} (ver firestore.rules). Exige só a senha do
    // administrador master (fixa, só existe em functions/.env). Devolve
    // o uid criado (o chamador ainda precisa logar com email/senha pra
    // poder subir a foto — ver CadastroAdminCaronasActivity).
    suspend fun cadastrarAdmin(
        nome: String,
        sobrenome: String,
        email: String,
        telefone: String,
        cpf: String,
        senha: String,
        senhaAutorizacao: String
    ): Result<String>

    // Edita nome/sobrenome/telefone/cpf de um admin JÁ existente (achado
    // via buscarAdminPorCpf) — via Cloud Function "atualizarAdmin", mesma
    // trava de senha do master do cadastro. Não mexe em e-mail, senha de
    // login nem foto.
    suspend fun atualizarAdmin(
        uid: String,
        nome: String,
        sobrenome: String,
        telefone: String,
        cpf: String,
        senhaAutorizacao: String
    ): Result<Unit>

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

    // Busca por CPF (tela de "Editar Perfil" do admin master) — leitura
    // direta no Firestore, já liberada pra qualquer admin (ver
    // firestore.rules: admins/{adminId} allow read: if ehAdmin()); null se
    // não achar ninguém com esse CPF.
    suspend fun buscarAdminPorCpf(cpf: String): Result<Admin?>

    // Só o próprio admin pode chamar isso pro próprio uid (ver
    // firestore.rules: admins/{adminId} allow update só no campo fotoUrl,
    // só pelo dono). Sobe a foto pro Storage e grava a URL.
    suspend fun atualizarFotoAdmin(uid: String, uri: Uri): Result<String>
}
