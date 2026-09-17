package com.cjstudio.caronas

import android.net.Uri
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdminRepository @Inject constructor(
    private val db: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val storage: FirebaseStorage
) : IAdminRepository {

    override suspend fun souAdmin(uid: String): Result<Boolean> {
        return try {
            val doc = db.collection("admins").document(uid).get().await()
            Result.success(doc.exists())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun listarTodosUsuarios(): Result<List<Usuario>> {
        return try {
            val snapshot = db.collection("usuarios")
                .orderBy("criadoEm", Query.Direction.DESCENDING)
                .get().await()
            val usuarios = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Usuario::class.java)?.also { it.id = doc.id }
            }
            Result.success(usuarios)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun listarTodasCaronas(): Result<List<Carona>> {
        return try {
            val snapshot = db.collection("caronas")
                .orderBy("dataHoraPartida", Query.Direction.DESCENDING)
                .get().await()
            val caronas = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Carona::class.java)?.also { it.id = doc.id }
            }
            Result.success(caronas)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun listarTodasSolicitacoes(): Result<List<Solicitacao>> {
        return try {
            val snapshot = db.collection("solicitacoes")
                .orderBy("dataHoraPartida", Query.Direction.DESCENDING)
                .get().await()
            val solicitacoes = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Solicitacao::class.java)?.also { it.id = doc.id }
            }
            Result.success(solicitacoes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun buscarUsuariosPorTexto(texto: String): Result<List<Usuario>> {
        return try {
            val termoBusca = texto.trim()
            if (termoBusca.isEmpty()) return Result.success(emptyList())
            val termoNormalizado = TextoUtil.normalizar(termoBusca)
            val usuarios = listarTodosUsuarios().getOrThrow()
            val resultado = usuarios.filter { usuario ->
                (usuario.nomeCompleto?.let { TextoUtil.normalizar(it).contains(termoNormalizado) } == true) ||
                    (usuario.telefone?.contains(termoBusca) == true) ||
                    (usuario.email?.contains(termoBusca, ignoreCase = true) == true)
            }
            Result.success(resultado)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun listarMensagensDoUsuario(usuarioId: String): Result<List<MensagemAdminInfo>> {
        return try {
            // Duas queries (campos diferentes) porque o usuário pode
            // aparecer como motorista numas conversas e passageiro em
            // outras — mesmo motivo estrutural de
            // ChatCaronaRepository.escutarMinhasConversas.
            val comoMotorista = db.collection("conversas").whereEqualTo("motoristaId", usuarioId).get().await()
            val comoPassageiro = db.collection("conversas").whereEqualTo("passageiroId", usuarioId).get().await()
            val conversas = (comoMotorista.documents + comoPassageiro.documents).mapNotNull { doc ->
                doc.toObject(ConversaCarona::class.java)?.also { it.id = doc.id }
            }

            val todasMensagens = mutableListOf<MensagemAdminInfo>()
            for (conversa in conversas) {
                val conversaId = conversa.id ?: continue
                val mensagens = db.collection("conversas").document(conversaId).collection("mensagens").get().await()
                mensagens.documents.forEach { doc ->
                    doc.toObject(MensagemCarona::class.java)?.also {
                        it.id = doc.id
                        it.conversaId = conversaId
                    }?.let { mensagem -> todasMensagens.add(MensagemAdminInfo(mensagem, conversa)) }
                }
            }

            Result.success(todasMensagens.sortedByDescending { it.mensagem.timestamp?.time ?: 0L })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun cadastrarAdmin(
        nome: String,
        sobrenome: String,
        email: String,
        telefone: String,
        cpf: String,
        senha: String,
        senhaAutorizacao: String
    ): Result<String> {
        return try {
            val resultado = functions.getHttpsCallable("cadastrarAdmin")
                .call(
                    mapOf(
                        "nome" to nome,
                        "sobrenome" to sobrenome,
                        "email" to email,
                        "telefone" to telefone,
                        "cpf" to cpf,
                        "senha" to senha,
                        "senhaAutorizacao" to senhaAutorizacao
                    )
                )
                .await()
            @Suppress("UNCHECKED_CAST")
            val dados = resultado.data as? Map<String, Any?>
            val uid = dados?.get("uid") as? String ?: throw IllegalStateException("Resposta inválida do servidor.")
            Result.success(uid)
        } catch (e: FirebaseFunctionsException) {
            Result.failure(Exception(e.message ?: "Erro ao cadastrar admin."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun atualizarAdmin(
        uid: String,
        nome: String,
        sobrenome: String,
        telefone: String,
        cpf: String,
        senhaAutorizacao: String
    ): Result<Unit> {
        return try {
            functions.getHttpsCallable("atualizarAdmin")
                .call(
                    mapOf(
                        "uid" to uid,
                        "nome" to nome,
                        "sobrenome" to sobrenome,
                        "telefone" to telefone,
                        "cpf" to cpf,
                        "senhaAutorizacao" to senhaAutorizacao
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: FirebaseFunctionsException) {
            Result.failure(Exception(e.message ?: "Erro ao salvar admin."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun excluirAdmin(uid: String, senhaAutorizacao: String): Result<Unit> {
        return try {
            functions.getHttpsCallable("excluirAdmin")
                .call(mapOf("uid" to uid, "senhaAutorizacao" to senhaAutorizacao))
                .await()
            Result.success(Unit)
        } catch (e: FirebaseFunctionsException) {
            Result.failure(Exception(e.message ?: "Erro ao excluir admin."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun excluirUsuario(uid: String, senhaAutorizacao: String): Result<Unit> {
        return try {
            functions.getHttpsCallable("excluirUsuario")
                .call(mapOf("uid" to uid, "senhaAutorizacao" to senhaAutorizacao))
                .await()
            Result.success(Unit)
        } catch (e: FirebaseFunctionsException) {
            Result.failure(Exception(e.message ?: "Erro ao remover usuário."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun buscarAdminPorCpf(cpf: String): Result<Admin?> {
        return try {
            val snapshot = db.collection("admins").whereEqualTo("cpf", cpf).limit(1).get().await()
            val doc = snapshot.documents.firstOrNull()
            val admin = doc?.toObject(Admin::class.java)?.also { it.id = doc.id }
            Result.success(admin)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun buscarAdminLogado(uid: String): Result<Admin> {
        return try {
            val doc = db.collection("admins").document(uid).get().await()
            val admin = doc.toObject(Admin::class.java) ?: throw IllegalStateException("Admin não encontrado.")
            admin.id = doc.id
            Result.success(admin)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun atualizarFotoAdmin(uid: String, uri: Uri): Result<String> {
        return try {
            val ref = storage.reference.child("fotos_perfil/$uid/perfil_${UUID.randomUUID()}.jpg")
            ref.putFile(uri).await()
            val url = ref.downloadUrl.await().toString()
            db.collection("admins").document(uid).update("fotoUrl", url).await()
            Result.success(url)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun atualizarUsuario(uid: String, nomeCompleto: String, telefone: String, veiculo: Veiculo?, senhaAutorizacao: String): Result<Unit> {
        return try {
            val dados = mutableMapOf<String, Any?>(
                "uid" to uid,
                "nomeCompleto" to nomeCompleto,
                "telefone" to telefone,
                "senhaAutorizacao" to senhaAutorizacao
            )
            if (veiculo != null) {
                dados["veiculo"] = mapOf(
                    "marca" to (veiculo.marca ?: ""),
                    "modelo" to (veiculo.modelo ?: ""),
                    "cor" to (veiculo.cor ?: ""),
                    "placa" to (veiculo.placa ?: "")
                )
            }
            functions.getHttpsCallable("admAtualizarUsuario").call(dados).await()
            Result.success(Unit)
        } catch (e: FirebaseFunctionsException) {
            Result.failure(Exception(e.message ?: "Erro ao salvar."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun colecaoManifestacoes() = db.collection("manifestacoes")

    override suspend fun listarManifestacoes(): Result<List<Manifestacao>> {
        return try {
            val snapshot = colecaoManifestacoes()
                .whereEqualTo("arquivado", false)
                .orderBy("criadoEm", Query.Direction.DESCENDING)
                .get().await()
            val itens = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Manifestacao::class.java)?.also { it.id = doc.id }
            }
            Result.success(itens)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun listarManifestacoesArquivadas(): Result<List<Manifestacao>> {
        return try {
            val snapshot = colecaoManifestacoes()
                .whereEqualTo("arquivado", true)
                .orderBy("arquivadoEm", Query.Direction.DESCENDING)
                .get().await()
            val itens = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Manifestacao::class.java)?.also { it.id = doc.id }
            }
            Result.success(itens)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun responderManifestacao(manifestacaoId: String, resposta: String): Result<Unit> {
        return try {
            functions.getHttpsCallable("responderManifestacao")
                .call(mapOf("manifestacaoId" to manifestacaoId, "resposta" to resposta))
                .await()
            Result.success(Unit)
        } catch (e: FirebaseFunctionsException) {
            Result.failure(Exception(e.message ?: "Erro ao enviar resposta."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun arquivarManifestacao(manifestacaoId: String): Result<Unit> {
        return try {
            val ref = colecaoManifestacoes().document(manifestacaoId)
            val status = ref.get().await().getString("status")
            if (status != Manifestacao.STATUS_RESPONDIDO) {
                throw IllegalStateException("Só é possível arquivar depois de responder.")
            }
            ref.update(
                mapOf(
                    "arquivado" to true,
                    "arquivadoEm" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun excluirManifestacao(manifestacaoId: String, senhaAutorizacao: String): Result<Unit> {
        return try {
            functions.getHttpsCallable("admExcluirManifestacao")
                .call(mapOf("manifestacaoId" to manifestacaoId, "senhaAutorizacao" to senhaAutorizacao))
                .await()
            Result.success(Unit)
        } catch (e: FirebaseFunctionsException) {
            Result.failure(Exception(e.message ?: "Erro ao excluir."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun escutarContagemManifestacoesPendentes(): Flow<Int> = callbackFlow {
        val registro = colecaoManifestacoes()
            .whereEqualTo("arquivado", false)
            .whereEqualTo("status", Manifestacao.STATUS_PENDENTE)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.size() ?: 0)
            }
        awaitClose { registro.remove() }
    }

    override suspend fun listarPagamentosMotorista(): Result<List<PagamentoMotorista>> {
        return try {
            val snapshot = db.collection("pagamentosMotorista").get().await()
            val pagamentos = snapshot.documents.mapNotNull { doc ->
                doc.toObject(PagamentoMotorista::class.java)?.also { it.id = doc.id }
            }
            Result.success(pagamentos)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
