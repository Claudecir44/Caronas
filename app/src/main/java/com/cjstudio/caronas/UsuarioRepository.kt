package com.cjstudio.caronas

import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsuarioRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val prefs: DataStore<Preferences>,
    private val functions: FirebaseFunctions
) : IUsuarioRepository {

    private fun colecaoUsuarios() = db.collection("usuarios")

    override suspend fun cadastrar(usuario: Usuario, senha: String): Result<String> {
        var uidCriado: String? = null
        return try {
            val authResult = auth.createUserWithEmailAndPassword(usuario.email!!, senha).await()
            val uid = authResult.user?.uid ?: throw IllegalStateException("Falha ao criar usuário.")
            uidCriado = uid

            usuario.id = uid
            colecaoUsuarios().document(uid).set(usuario).await()

            // Best-effort — mesmo padrão do Match: se o envio falhar (sem
            // rede no momento, por ex.), o cadastro em si já está feito e
            // não deveria ser desfeito por causa disso. O bloqueio de
            // verdade acontece no login (isEmailVerified abaixo), que
            // também reenvia o e-mail a cada tentativa.
            try {
                authResult.user?.sendEmailVerification()?.await()
            } catch (_: Exception) {
                // Ignorado de propósito — ver comentário acima.
            }

            prefs.edit { it[KEY_USUARIO_ID] = uid }
            Result.success(uid)
        } catch (e: Exception) {
            // Desfaz o que já foi criado, pra não deixar conta órfã sem doc
            // (ou doc sem conta) — mesmo espírito do rollback do Match, só
            // que direto pelo client (fase 1 não tem Cloud Function ainda).
            uidCriado?.let { excluirContaRecemCriada(it) }
            Result.failure(e)
        }
    }

    override suspend fun login(email: String, senha: String): Result<Usuario> {
        return try {
            val authResult = auth.signInWithEmailAndPassword(email, senha).await()
            val firebaseUser = authResult.user ?: throw IllegalStateException("Falha ao entrar.")

            // reload() força buscar o perfil de novo no servidor antes de
            // checar isEmailVerified — sem isso, o valor no FirebaseUser
            // devolvido pelo signIn pode vir desatualizado (falso) mesmo
            // que o usuário já tenha clicado no link de verificação minutos
            // atrás, porque essa mudança aconteceu FORA do app (no
            // navegador) e o token do signIn nem sempre reflete ela na
            // hora. Era exatamente esse o bug: validar o e-mail e o login
            // continuar pedindo validação de novo.
            firebaseUser.reload().await()

            // Mesmo padrão do Match (UsuarioRepository.loginUsuario): tenta
            // reenviar o e-mail de verificação quando a conta ainda não foi
            // confirmada, caso o primeiro tenha caído no spam ou não tenha
            // chegado — e não deixa entrar sem confirmar. Com cooldown (ver
            // reenviarVerificacaoComCooldown/VerificacaoEmailUtil.kt) — sem
            // isso, login repetido em sequência rápida estourava o limite
            // de envio do próprio Firebase.
            if (!firebaseUser.isEmailVerified) {
                val mensagem = reenviarVerificacaoComCooldown(firebaseUser, prefs)
                auth.signOut()
                throw IllegalStateException(mensagem)
            }

            val uid = firebaseUser.uid
            prefs.edit { it[KEY_USUARIO_ID] = uid }
            buscarUsuarioLogado()
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun buscarUsuarioLogado(): Result<Usuario> {
        return try {
            val uid = auth.currentUser?.uid ?: throw IllegalStateException("Não há sessão ativa.")
            val doc = colecaoUsuarios().document(uid).get().await()
            val usuario = doc.toObject(Usuario::class.java) ?: throw IllegalStateException("Cadastro não encontrado.")
            usuario.id = doc.id
            Result.success(usuario)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun buscarUsuarioPorId(uid: String): Result<Usuario> {
        return try {
            val doc = colecaoUsuarios().document(uid).get().await()
            val usuario = doc.toObject(Usuario::class.java) ?: throw IllegalStateException("Usuário não encontrado.")
            usuario.id = doc.id
            Result.success(usuario)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun atualizarPerfil(usuario: Usuario): Result<Unit> {
        return try {
            val uid = usuario.id ?: throw IllegalStateException("Usuário sem id.")
            colecaoUsuarios().document(uid).set(usuario, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun atualizarPapelMotorista(uid: String, motorista: Boolean): Result<Unit> {
        return try {
            colecaoUsuarios().document(uid).update("motorista", motorista).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun uploadFotoPerfil(uid: String, uri: Uri): Result<String> {
        return try {
            val ref = storage.reference.child("fotos_perfil/$uid/perfil_${UUID.randomUUID()}.jpg")
            ref.putFile(uri).await()
            val url = ref.downloadUrl.await().toString()
            colecaoUsuarios().document(uid).update("fotoUrl", url).await()
            Result.success(url)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun excluirContaPropria(senhaAtual: String): Result<Unit> {
        return try {
            val user = auth.currentUser ?: throw IllegalStateException("Não há sessão ativa.")
            val email = user.email ?: throw IllegalStateException("Conta sem e-mail.")
            val credential = EmailAuthProvider.getCredential(email, senhaAtual)
            user.reauthenticate(credential).await()

            val uid = user.uid
            // Apaga a pasta de fotos de perfil (best-effort — se não existir
            // nenhum arquivo, o list() só devolve vazio, sem erro).
            try {
                val pasta = storage.reference.child("fotos_perfil/$uid")
                pasta.listAll().await().items.forEach { it.delete().await() }
            } catch (_: Exception) {
                // Ignora — a exclusão da conta não deve travar por causa da foto.
            }

            colecaoUsuarios().document(uid).delete().await()
            user.delete().await()
            logout()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun excluirContaRecemCriada(uid: String): Result<Unit> {
        return try {
            colecaoUsuarios().document(uid).delete().await()
            auth.currentUser?.takeIf { it.uid == uid }?.delete()?.await()
            prefs.edit { it.remove(KEY_USUARIO_ID) }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun logout() {
        auth.signOut()
        prefs.edit { it.remove(KEY_USUARIO_ID) }
    }

    override suspend fun usuarioLogadoId(): String? {
        return auth.currentUser?.uid
    }

    override suspend fun enviarRedefinicaoSenha(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun reenviarEmailVerificacao(email: String): Result<Unit> {
        return try {
            functions.getHttpsCallable("reenviarVerificacaoEmail")
                .call(mapOf("email" to email))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun enviarManifestacao(tipo: String, nomeCompleto: String, email: String, telefone: String, mensagem: String): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: throw IllegalStateException("Não há sessão ativa.")
            val manifestacao = Manifestacao(
                tipo = tipo,
                nomeCompleto = nomeCompleto,
                email = email,
                telefone = telefone,
                mensagem = mensagem,
                usuarioId = uid
            )
            db.collection("manifestacoes").add(manifestacao).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun iniciarPagamentoAcessoMotorista(): Result<String> {
        return try {
            val resultado = functions.getHttpsCallable("createPaymentPreferenceMotorista").call().await()
            @Suppress("UNCHECKED_CAST")
            val dados = resultado.data as? Map<String, Any?>
            val initPoint = dados?.get("initPoint") as? String
                ?: throw IllegalStateException("Resposta inválida do servidor.")
            Result.success(initPoint)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Ordena aqui, no cliente, em vez de orderBy no Firestore: whereEqualTo +
    // orderBy em outro campo exigiria um índice composto novo, e um motorista
    // tem no máximo alguns documentos (um por mês pago).
    override suspend fun buscarMeusPagamentosMotorista(): Result<List<PagamentoMotorista>> {
        return try {
            val uid = auth.currentUser?.uid ?: throw IllegalStateException("Usuário não autenticado.")
            val snapshot = db.collection("pagamentosMotorista").whereEqualTo("usuarioId", uid).get().await()
            val pagamentos = snapshot.documents.mapNotNull { doc ->
                doc.toObject(PagamentoMotorista::class.java)?.also { it.id = doc.id }
            }.sortedByDescending { it.dataCompra ?: 0L }
            Result.success(pagamentos)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
