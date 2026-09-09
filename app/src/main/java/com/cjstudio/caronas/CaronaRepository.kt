package com.cjstudio.caronas

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CaronaRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) : ICaronaRepository {

    private fun colecaoCaronas() = db.collection("caronas")

    override suspend fun publicarCarona(carona: Carona): Result<String> {
        return try {
            val uid = auth.currentUser?.uid ?: throw IllegalStateException("Não há sessão ativa.")
            val perfil = db.collection("usuarios").document(uid).get().await()

            carona.motoristaId = uid
            carona.motoristaNome = perfil.getString("nomeCompleto")
            carona.motoristaFotoUrl = perfil.getString("fotoUrl")
            carona.veiculo = perfil.toObject(Usuario::class.java)?.veiculo
            carona.cidadeOrigemBusca = carona.cidadeOrigem?.let { TextoUtil.normalizar(it) }
            carona.cidadeDestinoBusca = carona.cidadeDestino?.let { TextoUtil.normalizar(it) }

            val ref = colecaoCaronas().add(carona).await()
            Result.success(ref.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun buscarCaronas(cidadeOrigem: String, cidadeDestino: String, data: Calendar): Result<List<Carona>> {
        return try {
            val inicioDoDia = (data.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val inicioDoDiaSeguinte = (inicioDoDia.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }

            val snapshot = colecaoCaronas()
                .whereEqualTo("status", "ativa")
                .whereEqualTo("cidadeOrigemBusca", TextoUtil.normalizar(cidadeOrigem))
                .whereEqualTo("cidadeDestinoBusca", TextoUtil.normalizar(cidadeDestino))
                .whereGreaterThanOrEqualTo("dataHoraPartida", inicioDoDia.timeInMillis)
                .whereLessThan("dataHoraPartida", inicioDoDiaSeguinte.timeInMillis)
                .orderBy("dataHoraPartida")
                .get()
                .await()

            val caronas = snapshot.documents
                .mapNotNull { doc -> doc.toObject(Carona::class.java)?.apply { id = doc.id } }

            Result.success(caronas)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun buscarMinhasOfertas(): Result<List<Carona>> {
        return try {
            val uid = auth.currentUser?.uid ?: throw IllegalStateException("Não há sessão ativa.")

            val snapshot = colecaoCaronas()
                .whereEqualTo("motoristaId", uid)
                .orderBy("dataHoraPartida", Query.Direction.DESCENDING)
                .get()
                .await()

            val caronas = snapshot.documents
                .mapNotNull { doc -> doc.toObject(Carona::class.java)?.apply { id = doc.id } }

            Result.success(caronas)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun atualizarOferta(caronaId: String, dataHoraPartida: Long, vagas: Int, valorPorVaga: Double): Result<Unit> {
        return try {
            colecaoCaronas().document(caronaId).update(
                mapOf(
                    "dataHoraPartida" to dataHoraPartida,
                    "vagas" to vagas,
                    "valorPorVaga" to valorPorVaga
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun excluirOferta(caronaId: String): Result<Unit> {
        return try {
            colecaoCaronas().document(caronaId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
