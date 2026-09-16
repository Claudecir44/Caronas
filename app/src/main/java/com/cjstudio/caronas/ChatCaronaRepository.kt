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
class ChatCaronaRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) : IChatCaronaRepository {

    private fun colecaoConversas() = db.collection("conversas")

    // Mensagens vivem como subcoleção de conversas/{conversaId} (em vez de
    // uma coleção "mensagens" no nível raiz filtrada por conversaId) —
    // necessário pras regras do Firestore (ver firestore.rules): pra uma
    // consulta em lista, o Firestore só consegue validar a regra de leitura
    // usando um get() no documento pai quando o id desse pai vem do próprio
    // caminho da consulta. Com "mensagens" filtrado por um campo
    // (whereEqualTo("conversaId", ...)), o Firestore não consegue provar
    // que todo resultado bate com a regra (que checava remetenteId/
    // destinatarioId, campos diferentes do filtro) e nega tudo com
    // PERMISSION_DENIED, mesmo os documentos sendo do dono certo.
    private fun colecaoMensagens(conversaId: String) = colecaoConversas().document(conversaId).collection("mensagens")

    override suspend fun buscarOuCriarConversa(solicitacao: Solicitacao): Result<ConversaCarona> {
        return try {
            val solicitacaoId = solicitacao.id ?: throw IllegalStateException("Solicitação sem id.")

            // O id do documento da conversa é sempre o próprio id da
            // solicitação (relação 1:1) — em vez de um id gerado (.add())
            // achado depois por uma consulta (.whereEqualTo("solicitacaoId",
            // ...)). Achar por consulta exigia uma regra baseada num campo
            // diferente do filtro da consulta (motoristaId/passageiroId vs.
            // solicitacaoId), o mesmo problema estrutural que já tinha
            // quebrado "mensagens" — o Firestore não conseguia validar e
            // negava com PERMISSION_DENIED. Um get() direto por id não tem
            // esse problema: a regra é avaliada direto contra o documento.
            val docRef = colecaoConversas().document(solicitacaoId)
            val existente = docRef.get().await()

            if (existente.exists()) {
                val conversa = existente.toObject(ConversaCarona::class.java)!!.apply { id = existente.id }
                return Result.success(conversa)
            }

            val motoristaId = solicitacao.motoristaId ?: throw IllegalStateException("Solicitação sem motorista.")
            val passageiroId = solicitacao.passageiroId ?: throw IllegalStateException("Solicitação sem passageiro.")
            val perfilMotorista = db.collection("usuarios").document(motoristaId).get().await()

            val novaConversa = ConversaCarona(
                solicitacaoId = solicitacaoId,
                cidadeOrigem = solicitacao.cidadeOrigem,
                cidadeDestino = solicitacao.cidadeDestino,
                motoristaId = motoristaId,
                motoristaNome = perfilMotorista.getString("nomeCompleto"),
                motoristaFotoUrl = perfilMotorista.getString("fotoUrl"),
                passageiroId = passageiroId,
                passageiroNome = solicitacao.passageiroNome,
                passageiroFotoUrl = solicitacao.passageiroFotoUrl
            )
            docRef.set(novaConversa).await()
            Result.success(novaConversa.apply { id = docRef.id })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun escutarMensagens(conversaId: String): Flow<List<MensagemCarona>> = callbackFlow {
        val registration = colecaoMensagens(conversaId)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val mensagens = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(MensagemCarona::class.java)?.apply {
                        id = doc.id
                        this.conversaId = conversaId
                    }
                } ?: emptyList()
                trySend(mensagens)
            }
        awaitClose { registration.remove() }
    }

    override suspend fun enviarMensagem(conversa: ConversaCarona, texto: String): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: throw IllegalStateException("Não há sessão ativa.")
            val conversaId = conversa.id ?: throw IllegalStateException("Conversa sem id.")
            val souMotorista = conversa.motoristaId == uid
            val destinatarioId = if (souMotorista) conversa.passageiroId else conversa.motoristaId

            val mensagem = MensagemCarona(
                remetenteId = uid,
                destinatarioId = destinatarioId,
                conteudo = texto
            )
            colecaoMensagens(conversaId).add(mensagem).await()

            // Soma 1 na contagem de não lidas de quem vai RECEBER a mensagem.
            val campoNaoLidas = if (souMotorista) "naoLidasPassageiro" else "naoLidasMotorista"
            val atualizacao = mutableMapOf<String, Any?>(
                "ultimaMensagem" to texto,
                "ultimoTimestamp" to FieldValue.serverTimestamp(),
                "ultimoRemetenteId" to uid,
                campoNaoLidas to FieldValue.increment(1)
            )
            colecaoConversas().document(conversaId).update(atualizacao).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun escutarMinhasConversas(): Flow<List<ConversaCarona>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        var comoMotorista: List<ConversaCarona> = emptyList()
        var comoPassageiro: List<ConversaCarona> = emptyList()

        fun emitirCombinado() {
            val combinado = (comoMotorista + comoPassageiro).sortedByDescending { it.ultimoTimestamp?.time ?: 0L }
            trySend(combinado)
        }

        val reg1 = colecaoConversas()
            .whereEqualTo("motoristaId", uid)
            .orderBy("ultimoTimestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                comoMotorista = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(ConversaCarona::class.java)?.apply { id = doc.id }
                } ?: emptyList()
                emitirCombinado()
            }

        val reg2 = colecaoConversas()
            .whereEqualTo("passageiroId", uid)
            .orderBy("ultimoTimestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                comoPassageiro = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(ConversaCarona::class.java)?.apply { id = doc.id }
                } ?: emptyList()
                emitirCombinado()
            }

        awaitClose {
            reg1.remove()
            reg2.remove()
        }
    }

    override suspend fun marcarConversaComoLida(conversa: ConversaCarona): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: throw IllegalStateException("Não há sessão ativa.")
            val conversaId = conversa.id ?: throw IllegalStateException("Conversa sem id.")
            val campo = if (conversa.motoristaId == uid) "naoLidasMotorista" else "naoLidasPassageiro"
            colecaoConversas().document(conversaId).update(campo, 0).await()

            // Marca cada mensagem recebida ainda não lida como lida — é o
            // que alimenta o "Lida"/"Não lida" embaixo das mensagens
            // ENVIADAS de quem mandou (ver MensagemCaronaAdapter). Antes
            // disso o campo "lida" nunca era gravado (só o contador da
            // conversa era zerado), então ficava sempre false.
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

    override suspend fun apagarMensagemParaMim(mensagem: MensagemCarona): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: throw IllegalStateException("Não há sessão ativa.")
            val conversaId = mensagem.conversaId ?: throw IllegalStateException("Mensagem sem conversa.")
            val mensagemId = mensagem.id ?: throw IllegalStateException("Mensagem sem id.")
            val campo = if (mensagem.remetenteId == uid) "deletadaParaRemetente" else "deletadaParaDestinatario"
            colecaoMensagens(conversaId).document(mensagemId).update(campo, true).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun apagarMensagemParaTodos(mensagem: MensagemCarona): Result<Unit> {
        return try {
            val conversaId = mensagem.conversaId ?: throw IllegalStateException("Mensagem sem conversa.")
            val mensagemId = mensagem.id ?: throw IllegalStateException("Mensagem sem id.")
            colecaoMensagens(conversaId).document(mensagemId).update("deletadaParaTodos", true).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun excluirConversa(conversa: ConversaCarona): Result<Unit> {
        return try {
            val conversaId = conversa.id ?: throw IllegalStateException("Conversa sem id.")
            val mensagens = colecaoMensagens(conversaId).get().await()
            val batch = db.batch()
            mensagens.documents.forEach { doc -> batch.delete(doc.reference) }
            batch.delete(colecaoConversas().document(conversaId))
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
