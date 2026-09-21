package com.cjstudio.caronas

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
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
            // cidadeBusca de cada parada (incluindo origem/destino, sempre
            // paradas.first()/paradas.last()) + a lista paralela só de
            // cidades normalizadas, usada pela busca por trecho (ver
            // buscarCaronas: whereArrayContains).
            carona.paradas = carona.paradas.map { it.copy(cidadeBusca = it.cidade?.let { c -> TextoUtil.normalizar(c) }) }
            carona.cidadesBusca = carona.paradas.mapNotNull { it.cidadeBusca }

            // Cria a carona e soma 1 no contador de viagens oferecidas
            // (usuarios/{uid}.caronasOferecidas) no MESMO lote — é esse
            // contador que firestore.rules (permiteOferecerCarona) usa pra
            // saber se o motorista ainda está dentro das 10 gratuitas ou
            // precisa de acessoMotoristaExpiraEm válido. Precisa ser atômico
            // com a criação da carona: se só uma das duas partes fosse
            // gravada, o contador ficaria fora de sincronia com a realidade.
            val ref = colecaoCaronas().document()
            val batch = db.batch()
            batch.set(ref, carona)
            batch.update(db.collection("usuarios").document(uid), "caronasOferecidas", FieldValue.increment(1))
            batch.commit().await()
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
            val origemNormalizada = TextoUtil.normalizar(cidadeOrigem)
            val destinoNormalizado = TextoUtil.normalizar(cidadeDestino)

            // Firestore só permite 1 array-contains por query, então filtra
            // só pela cidade de ORIGEM aqui (toda carona que passa por essa
            // cidade, em qualquer posição da rota) — o destino e a ORDEM
            // (origem antes do destino na rota) são conferidos abaixo,
            // client-side, porque o Firestore não consegue comparar a
            // posição de dois elementos de um array numa única query.
            val snapshot = colecaoCaronas()
                .whereEqualTo("status", "ativa")
                .whereArrayContains("cidadesBusca", origemNormalizada)
                .whereGreaterThanOrEqualTo("dataHoraPartida", inicioDoDia.timeInMillis)
                .whereLessThan("dataHoraPartida", inicioDoDiaSeguinte.timeInMillis)
                .orderBy("dataHoraPartida")
                .get()
                .await()

            val caronas = snapshot.documents
                .mapNotNull { doc -> doc.toObject(Carona::class.java)?.apply { id = doc.id } }
                .filter { carona ->
                    val indiceOrigem = carona.paradas.indexOfFirst { it.cidadeBusca == origemNormalizada }
                    val indiceDestino = carona.paradas.indexOfLast { it.cidadeBusca == destinoNormalizado }
                    indiceOrigem != -1 && indiceDestino != -1 && indiceOrigem < indiceDestino
                }

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

    override suspend fun atualizarOferta(caronaId: String, rota: List<ParadaRota>, dataHoraPartida: Long, vagas: Int, valorPorVaga: Double, distanciaKm: Double?): Result<Unit> {
        return try {
            val rotaComBusca = rota.mapIndexed { indice, parada ->
                parada.copy(ordem = indice, cidadeBusca = parada.cidade?.let { TextoUtil.normalizar(it) })
            }
            colecaoCaronas().document(caronaId).update(
                mapOf(
                    "cidadeOrigem" to rotaComBusca.first().cidade,
                    "cidadeOrigemBusca" to rotaComBusca.first().cidadeBusca,
                    "cidadeDestino" to rotaComBusca.last().cidade,
                    "cidadeDestinoBusca" to rotaComBusca.last().cidadeBusca,
                    "paradas" to rotaComBusca,
                    "cidadesBusca" to rotaComBusca.mapNotNull { it.cidadeBusca },
                    "dataHoraPartida" to dataHoraPartida,
                    "vagas" to vagas,
                    "valorPorVaga" to valorPorVaga,
                    // A rota pode ter mudado: a distância gravada (base do
                    // tempo aproximado) é sempre a da rota nova. Sem valor
                    // (geocodificação falhou), apaga o campo em vez de deixar
                    // a distância da rota antiga — as telas recalculam.
                    "distanciaKm" to (distanciaKm ?: FieldValue.delete())
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
