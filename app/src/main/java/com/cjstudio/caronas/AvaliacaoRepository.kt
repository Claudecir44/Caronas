package com.cjstudio.caronas

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AvaliacaoRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) : IAvaliacaoRepository {

    private fun colecaoAvaliacoes() = db.collection("avaliacoes")

    override suspend fun avaliar(solicitacao: Solicitacao, avaliadoId: String, nota: Int, comentario: String?): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: throw IllegalStateException("Não há sessão ativa.")
            val solicitacaoId = solicitacao.id ?: throw IllegalStateException("Solicitação sem id.")
            if (nota < 1 || nota > 5) throw IllegalArgumentException("Nota precisa ser entre 1 e 5.")

            val avaliacao = Avaliacao(
                solicitacaoId = solicitacaoId,
                avaliadorId = uid,
                avaliadoId = avaliadoId,
                nota = nota,
                comentario = comentario?.trim()?.takeIf { it.isNotEmpty() }
            )
            // ID determinístico: uma segunda tentativa da mesma pessoa pra
            // mesma viagem vira "update" de um doc já existente, não
            // "create" — e a regra do Firestore recusa update (imutável).
            colecaoAvaliacoes().document("${solicitacaoId}_$uid").set(avaliacao).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun buscarAvaliacoesRecebidas(usuarioId: String): Result<List<Avaliacao>> {
        return try {
            val snapshot = colecaoAvaliacoes()
                .whereEqualTo("avaliadoId", usuarioId)
                .orderBy("criadoEm", Query.Direction.DESCENDING)
                .get()
                .await()
            val avaliacoes = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Avaliacao::class.java)?.apply { id = doc.id }
            }
            Result.success(avaliacoes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun jaAvaliei(solicitacaoId: String): Result<Boolean> {
        return try {
            val uid = auth.currentUser?.uid ?: throw IllegalStateException("Não há sessão ativa.")
            val doc = colecaoAvaliacoes().document("${solicitacaoId}_$uid").get().await()
            Result.success(doc.exists())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun buscarSolicitacoesJaAvaliadas(): Result<Set<String>> {
        return try {
            val uid = auth.currentUser?.uid ?: throw IllegalStateException("Não há sessão ativa.")
            val snapshot = colecaoAvaliacoes().whereEqualTo("avaliadorId", uid).get().await()
            val ids = snapshot.documents.mapNotNull { it.getString("solicitacaoId") }.toSet()
            Result.success(ids)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun listarTodasAvaliacoes(): Result<List<Avaliacao>> {
        return try {
            val snapshot = colecaoAvaliacoes().get().await()
            val avaliacoes = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Avaliacao::class.java)?.apply { id = doc.id }
            }
            Result.success(avaliacoes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
