package com.cjstudio.caronas

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatAdminCaronaRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) : IChatAdminCaronaRepository {

    private fun colecaoConversas() = db.collection("conversasAdmin")
    private fun colecaoMensagens(conversaId: String) = colecaoConversas().document(conversaId).collection("mensagens")

    // Id determinístico (par ordenado) em vez de gerado por .add() + busca —
    // mesmo espírito de ChatCaronaRepository usar o solicitacaoId como id do
    // documento: um get() direto por id é sempre validável pelas regras do
    // Firestore, diferente de uma consulta (ver comentário lá). "Quem é
    // admin1/admin2" não tem significado nenhum além de decidir esse id, os
    // dois lados são simétricos.
    private fun idConversaEntre(a: String, b: String) = listOf(a, b).sorted().joinToString("_")

    private suspend fun nomeCompletoAdmin(uid: String): String {
        val doc = db.collection("admins").document(uid).get().await()
        return listOfNotNull(doc.getString("nome"), doc.getString("sobrenome"))
            .joinToString(" ").ifBlank { doc.getString("email").orEmpty() }
    }

    override suspend fun buscarOuCriarConversa(
        outroAdminId: String,
        outroAdminNome: String?,
        outroAdminFoto: String?
    ): Result<ConversaAdmin> {
        return try {
            val uid = auth.currentUser?.uid ?: throw IllegalStateException("Não há sessão ativa.")
            val conversaId = idConversaEntre(uid, outroAdminId)
            val docRef = colecaoConversas().document(conversaId)
            val existente = docRef.get().await()
            if (existente.exists()) {
                return Result.success(existente.toObject(ConversaAdmin::class.java)!!.apply { id = existente.id })
            }

            val meuNomeCompleto = nomeCompletoAdmin(uid)
            val meuFoto = db.collection("admins").document(uid).get().await().getString("fotoUrl")

            val euSouAdmin1 = listOf(uid, outroAdminId).sorted().first() == uid
            val novaConversa = if (euSouAdmin1) {
                ConversaAdmin(
                    participantes = listOf(uid, outroAdminId),
                    admin1Id = uid, admin1Nome = meuNomeCompleto, admin1Foto = meuFoto,
                    admin2Id = outroAdminId, admin2Nome = outroAdminNome, admin2Foto = outroAdminFoto
                )
            } else {
                ConversaAdmin(
                    participantes = listOf(uid, outroAdminId),
                    admin1Id = outroAdminId, admin1Nome = outroAdminNome, admin1Foto = outroAdminFoto,
                    admin2Id = uid, admin2Nome = meuNomeCompleto, admin2Foto = meuFoto
                )
            }
            docRef.set(novaConversa).await()
            Result.success(novaConversa.apply { id = docRef.id })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun escutarMinhasConversas(): Flow<List<ConversaAdmin>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val registration = colecaoConversas()
            .whereArrayContains("participantes", uid)
            .orderBy("ultimoTimestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val conversas = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(ConversaAdmin::class.java)?.apply { id = doc.id }
                } ?: emptyList()
                trySend(conversas)
            }
        awaitClose { registration.remove() }
    }

    // Decifra o conteúdo aqui (ver ChatAdminCryptoUtil) — quem consome esse
    // Flow (Activity/adapter) nunca lida com texto cifrado.
    override fun escutarMensagens(conversaId: String): Flow<List<MensagemChatAdmin>> = callbackFlow {
        val registration = colecaoMensagens(conversaId)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val mensagens = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(MensagemChatAdmin::class.java)?.apply {
                        id = doc.id
                        this.conversaId = conversaId
                        conteudo = ChatAdminCryptoUtil.descriptografar(conteudo)
                    }
                } ?: emptyList()
                trySend(mensagens)
            }
        awaitClose { registration.remove() }
    }

    override suspend fun enviarMensagem(conversa: ConversaAdmin, texto: String): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: throw IllegalStateException("Não há sessão ativa.")
            val conversaId = conversa.id ?: throw IllegalStateException("Conversa sem id.")
            val destinatarioId = conversa.idOutroAdmin(uid) ?: throw IllegalStateException("Conversa sem outro participante.")

            val mensagem = MensagemChatAdmin(
                remetenteId = uid,
                remetenteNome = nomeCompletoAdmin(uid),
                destinatarioId = destinatarioId,
                destinatarioNome = conversa.nomeOutroAdmin(uid),
                conteudo = ChatAdminCryptoUtil.criptografar(texto)
            )
            colecaoMensagens(conversaId).add(mensagem).await()

            // Soma 1 na contagem de não lidas de quem vai RECEBER a
            // mensagem — "ultimaMensagem" fica em texto puro (mesmo padrão
            // de ConversaCarona/ChatCaronaRepository), só o conteúdo dentro
            // da subcoleção de mensagens é cifrado.
            val souAdmin1 = conversa.admin1Id == uid
            val campoNaoLidas = if (souAdmin1) "naoLidas2" else "naoLidas1"
            colecaoConversas().document(conversaId).update(
                mapOf(
                    "ultimaMensagem" to texto,
                    "ultimoTimestamp" to FieldValue.serverTimestamp(),
                    "ultimoRemetenteId" to uid,
                    campoNaoLidas to FieldValue.increment(1)
                )
            ).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun marcarConversaComoLida(conversa: ConversaAdmin): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: throw IllegalStateException("Não há sessão ativa.")
            val conversaId = conversa.id ?: throw IllegalStateException("Conversa sem id.")
            val campo = if (conversa.admin1Id == uid) "naoLidas1" else "naoLidas2"
            colecaoConversas().document(conversaId).update(campo, 0).await()

            val naoLidas = colecaoMensagens(conversaId)
                .whereEqualTo("destinatarioId", uid)
                .whereEqualTo("lida", false)
                .get().await()
            if (!naoLidas.isEmpty) {
                val lote = db.batch()
                naoLidas.documents.forEach { doc -> lote.update(doc.reference, "lida", true) }
                lote.commit().await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun apagarMensagemParaMim(mensagem: MensagemChatAdmin): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: throw IllegalStateException("Não há sessão ativa.")
            val conversaId = mensagem.conversaId ?: throw IllegalStateException("Mensagem sem conversa.")
            val mensagemId = mensagem.id ?: throw IllegalStateException("Mensagem sem id.")
            val campo = if (mensagem.remetenteId == uid) "deletadoParaRemetente" else "deletadoParaDestinatario"
            colecaoMensagens(conversaId).document(mensagemId).update(campo, true).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun apagarMensagemParaTodos(mensagem: MensagemChatAdmin): Result<Unit> {
        return try {
            val conversaId = mensagem.conversaId ?: throw IllegalStateException("Mensagem sem conversa.")
            val mensagemId = mensagem.id ?: throw IllegalStateException("Mensagem sem id.")
            colecaoMensagens(conversaId).document(mensagemId).update(
                mapOf(
                    "deletadoParaTodos" to true,
                    "conteudo" to ChatAdminCryptoUtil.criptografar("Mensagem apagada"),
                    "tipo" to "deletada"
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun apagarConversa(conversa: ConversaAdmin): Result<Unit> {
        return try {
            val conversaId = conversa.id ?: throw IllegalStateException("Conversa sem id.")
            val mensagens = colecaoMensagens(conversaId).get().await()
            // Lote tem limite de 500 operações — divide em blocos pra uma
            // conversa muito longa não estourar esse limite.
            mensagens.documents.chunked(400).forEach { bloco ->
                val lote = db.batch()
                bloco.forEach { doc -> lote.delete(doc.reference) }
                lote.commit().await()
            }
            colecaoConversas().document(conversaId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun escutarTotalNaoLidas(): Flow<Int> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(0)
            close()
            return@callbackFlow
        }
        val registration = colecaoConversas()
            .whereArrayContains("participantes", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val total = snapshot?.documents?.sumOf { doc ->
                    doc.toObject(ConversaAdmin::class.java)?.naoLidasParaMim(uid) ?: 0
                } ?: 0
                trySend(total)
            }
        awaitClose { registration.remove() }
    }
}
