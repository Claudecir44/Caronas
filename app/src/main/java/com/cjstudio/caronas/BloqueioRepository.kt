package com.cjstudio.caronas

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BloqueioRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) : IBloqueioRepository {

    private fun colecaoBloqueios() = db.collection("bloqueios")

    private fun uidLogado(): String = auth.currentUser?.uid ?: throw IllegalStateException("Não há sessão ativa.")

    override suspend fun bloquear(outroId: String, outroNome: String?): Result<Unit> {
        return try {
            val uid = uidLogado()
            if (uid == outroId) throw IllegalArgumentException("Não é possível bloquear a própria conta.")
            val bloqueio = Bloqueio(bloqueadorId = uid, bloqueadoId = outroId, bloqueadoNome = outroNome)
            colecaoBloqueios().document(Bloqueio.idDocumento(uid, outroId)).set(bloqueio).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun desbloquear(outroId: String): Result<Unit> {
        return try {
            colecaoBloqueios().document(Bloqueio.idDocumento(uidLogado(), outroId)).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun bloqueei(outroId: String): Result<Boolean> {
        return try {
            val doc = colecaoBloqueios().document(Bloqueio.idDocumento(uidLogado(), outroId)).get().await()
            Result.success(doc.exists())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun listarMeusBloqueios(): Result<List<Bloqueio>> {
        return try {
            val snapshot = colecaoBloqueios().whereEqualTo("bloqueadorId", uidLogado()).get().await()
            val bloqueios = snapshot.documents
                .mapNotNull { it.toObject(Bloqueio::class.java) }
                .sortedBy { it.bloqueadoNome?.lowercase() ?: "" }
            Result.success(bloqueios)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun idsComBloqueio(): Result<Set<String>> {
        return try {
            val uid = uidLogado()
            val ids = coroutineScope {
                val bloqueei = async { colecaoBloqueios().whereEqualTo("bloqueadorId", uid).get().await() }
                val meBloquearam = async { colecaoBloqueios().whereEqualTo("bloqueadoId", uid).get().await() }
                bloqueei.await().documents.mapNotNull { it.getString("bloqueadoId") } +
                    meBloquearam.await().documents.mapNotNull { it.getString("bloqueadorId") }
            }
            Result.success(ids.toSet())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
