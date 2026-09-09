package com.cjstudio.caronas

import android.net.Uri

interface IUsuarioRepository {
    // Cria a conta (Auth + doc Firestore) e devolve o uid criado. Em
    // qualquer falha no meio do caminho, desfaz o que já tiver sido criado.
    suspend fun cadastrar(usuario: Usuario, senha: String): Result<String>

    suspend fun login(email: String, senha: String): Result<Usuario>

    suspend fun buscarUsuarioLogado(): Result<Usuario>

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
}
