package com.cjstudio.caronas

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SolicitacaoRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) : ISolicitacaoRepository {

    private fun colecaoSolicitacoes() = db.collection("solicitacoes")

    override suspend fun solicitarVaga(carona: Carona): Result<String> {
        return try {
            val uid = auth.currentUser?.uid ?: throw IllegalStateException("Não há sessão ativa.")
            val caronaId = carona.id ?: throw IllegalStateException("Carona sem id.")
            val perfil = db.collection("usuarios").document(uid).get().await()

            val solicitacao = Solicitacao(
                caronaId = caronaId,
                passageiroId = uid,
                passageiroNome = perfil.getString("nomeCompleto"),
                passageiroFotoUrl = perfil.getString("fotoUrl"),
                motoristaId = carona.motoristaId,
                cidadeOrigem = carona.cidadeOrigem,
                cidadeDestino = carona.cidadeDestino,
                dataHoraPartida = carona.dataHoraPartida,
                valorPago = carona.valorPorVaga
            )

            val ref = colecaoSolicitacoes().add(solicitacao).await()
            Result.success(ref.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun buscarMinhasSolicitacoes(): Result<List<Solicitacao>> {
        return try {
            val uid = auth.currentUser?.uid ?: throw IllegalStateException("Não há sessão ativa.")

            val snapshot = colecaoSolicitacoes()
                .whereEqualTo("passageiroId", uid)
                .orderBy("dataHoraPartida", Query.Direction.DESCENDING)
                .get()
                .await()

            val solicitacoes = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Solicitacao::class.java)?.apply { id = doc.id }
            }
            Result.success(solicitacoes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun buscarSolicitacoesRecebidas(): Result<List<Solicitacao>> {
        return try {
            val uid = auth.currentUser?.uid ?: throw IllegalStateException("Não há sessão ativa.")

            val snapshot = colecaoSolicitacoes()
                .whereEqualTo("motoristaId", uid)
                .orderBy("dataHoraPartida", Query.Direction.DESCENDING)
                .get()
                .await()

            val solicitacoes = snapshot.documents.mapNotNull { doc ->
                doc.toObject(Solicitacao::class.java)?.apply { id = doc.id }
            }
            Result.success(solicitacoes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun confirmarSolicitacao(solicitacaoId: String): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: throw IllegalStateException("Não há sessão ativa.")
            val perfil = db.collection("usuarios").document(uid).get().await()
            val veiculo = perfil.toObject(Usuario::class.java)?.veiculo

            // Só grava foto/nome/veículo do motorista na hora da confirmação
            // — o passageiro só precisa saber quem vai buscá-lo depois que a
            // viagem é aceita (ver item_minha_viagem.xml/MinhaViagemAdapter).
            val atualizacao = mapOf(
                "status" to "confirmada",
                "motoristaNome" to perfil.getString("nomeCompleto"),
                "motoristaFotoUrl" to perfil.getString("fotoUrl"),
                "veiculo" to veiculo
            )
            colecaoSolicitacoes().document(solicitacaoId).update(atualizacao).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun cancelarSolicitacao(solicitacaoId: String): Result<Unit> {
        return try {
            colecaoSolicitacoes().document(solicitacaoId).update("status", "cancelada").await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
