package com.cjstudio.caronas

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SolicitacaoRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) : ISolicitacaoRepository {

    private fun colecaoSolicitacoes() = db.collection("solicitacoes")
    private fun colecaoOcupacao(caronaId: String) = db.collection("caronas").document(caronaId).collection("ocupacao")

    override suspend fun solicitarVaga(carona: Carona, indiceOrigem: Int, indiceDestino: Int, valorTrecho: Double, distanciaTrechoKm: Double?): Result<String> {
        return try {
            val uid = auth.currentUser?.uid ?: throw IllegalStateException("Não há sessão ativa.")
            val caronaId = carona.id ?: throw IllegalStateException("Carona sem id.")
            if (indiceDestino <= indiceOrigem) throw IllegalArgumentException("Trecho inválido.")
            val paradaOrigem = carona.paradas.getOrNull(indiceOrigem) ?: throw IllegalStateException("Parada de origem inválida.")
            val paradaDestino = carona.paradas.getOrNull(indiceDestino) ?: throw IllegalStateException("Parada de destino inválida.")

            val disponiveis = vagasDisponiveis(carona, indiceOrigem, indiceDestino).getOrThrow()
            if (disponiveis < 1) throw IllegalStateException("Não há mais vagas disponíveis para esse trecho.")

            val perfil = db.collection("usuarios").document(uid).get().await()

            val novaRef = colecaoSolicitacoes().document()
            val solicitacao = Solicitacao(
                caronaId = caronaId,
                passageiroId = uid,
                passageiroNome = perfil.getString("nomeCompleto"),
                passageiroFotoUrl = perfil.getString("fotoUrl"),
                motoristaId = carona.motoristaId,
                cidadeOrigem = paradaOrigem.cidade,
                cidadeDestino = paradaDestino.cidade,
                enderecoOrigem = paradaOrigem.endereco,
                enderecoDestino = paradaDestino.endereco,
                indiceOrigemNaRota = indiceOrigem,
                indiceDestinoNaRota = indiceDestino,
                dataHoraPartida = carona.dataHoraPartida,
                valorPago = valorTrecho,
                distanciaKm = distanciaTrechoKm,
                // Chegada da viagem INTEIRA (não só do trecho do passageiro):
                // é o que fecha o chat pros dois, motorista e passageiro.
                chegadaPrevistaEm = carona.dataHoraPartida?.let {
                    TempoViagemUtil.chegadaPrevistaEm(it, carona.distanciaKm ?: distanciaTrechoKm)
                }
            )

            // Grava a solicitação e o registro de ocupação (usado só pra
            // calcular vaga disponível, ver vagasDisponiveis) no mesmo lote
            // — não é uma transação de verdade (não valida vaga de novo no
            // servidor, ver ressalva na interface), mas garante que as duas
            // escritas acontecem juntas ou nenhuma acontece.
            val ocupacaoRef = colecaoOcupacao(caronaId).document(novaRef.id)
            val ocupacao = mapOf(
                "passageiroId" to uid,
                "indiceOrigemNaRota" to indiceOrigem,
                "indiceDestinoNaRota" to indiceDestino
            )
            db.batch()
                .set(novaRef, solicitacao)
                .set(ocupacaoRef, ocupacao)
                .commit()
                .await()

            Result.success(novaRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun vagasDisponiveis(carona: Carona, indiceOrigem: Int, indiceDestino: Int): Result<Int> {
        return try {
            val caronaId = carona.id ?: throw IllegalStateException("Carona sem id.")
            val snapshot = colecaoOcupacao(caronaId).get().await()
            val ocupacoes = snapshot.documents.mapNotNull { doc ->
                val a = doc.getLong("indiceOrigemNaRota")?.toInt()
                val b = doc.getLong("indiceDestinoNaRota")?.toInt()
                if (a != null && b != null) a to b else null
            }
            // Ocupação por PERNA (intervalo entre duas paradas consecutivas)
            // dentro do trecho pedido — uma solicitação [a,b) ocupa a perna
            // k se a <= k < b. A vaga livre do trecho inteiro é limitada
            // pela perna mais cheia dele (não dá pra aceitar um passageiro
            // que cruza uma perna já lotada, mesmo que outras pernas do
            // trecho tenham espaço).
            var maxOcupacao = 0
            for (k in indiceOrigem until indiceDestino) {
                val ocupacaoNestaPerna = ocupacoes.count { (a, b) -> a <= k && k < b }
                if (ocupacaoNestaPerna > maxOcupacao) maxOcupacao = ocupacaoNestaPerna
            }
            Result.success(carona.vagas - maxOcupacao)
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

    override suspend fun cancelarSolicitacao(solicitacaoId: String): Result<Unit> =
        cancelarInterno(solicitacaoId, canceladoPor = "passageiro")

    override suspend fun cancelarComoMotorista(solicitacaoId: String): Result<Unit> =
        cancelarInterno(solicitacaoId, canceladoPor = "motorista")

    private suspend fun cancelarInterno(solicitacaoId: String, canceladoPor: String): Result<Unit> {
        return try {
            // Precisa apagar o registro de ocupação junto (ver solicitarVaga)
            // — senão a vaga continuaria "ocupada" pra sempre depois de
            // cancelada, mesmo o trecho estando livre de novo.
            val caronaId = colecaoSolicitacoes().document(solicitacaoId).get().await().getString("caronaId")
            val batch = db.batch()
            batch.update(
                colecaoSolicitacoes().document(solicitacaoId),
                mapOf("status" to "cancelada", "canceladoPor" to canceladoPor)
            )
            if (caronaId != null) {
                batch.delete(colecaoOcupacao(caronaId).document(solicitacaoId))
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun excluirSolicitacao(solicitacaoId: String): Result<Unit> {
        return try {
            val caronaId = colecaoSolicitacoes().document(solicitacaoId).get().await().getString("caronaId")
            val batch = db.batch()
            batch.delete(colecaoSolicitacoes().document(solicitacaoId))
            if (caronaId != null) {
                batch.delete(colecaoOcupacao(caronaId).document(solicitacaoId))
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Pendentes (status "solicitada") + cancelamentos feitos pelo
    // passageiro que o motorista ainda não viu — dois listeners
    // combinados num só Flow (mesmo padrão de
    // ChatCaronaRepository.escutarMinhasConversas), já que são duas
    // queries diferentes (campos diferentes) que juntas formam o número
    // do badge de "Minhas Ofertas".
    override fun escutarContagemPendentes(): Flow<Int> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(0)
            close()
            return@callbackFlow
        }

        var pendentes = 0
        var cancelamentosNaoVistos = 0
        fun emitirCombinado() = trySend(pendentes + cancelamentosNaoVistos)

        val reg1 = colecaoSolicitacoes()
            .whereEqualTo("motoristaId", uid)
            .whereEqualTo("status", "solicitada")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                pendentes = snapshot?.size() ?: 0
                emitirCombinado()
            }

        val reg2 = colecaoSolicitacoes()
            .whereEqualTo("motoristaId", uid)
            .whereEqualTo("status", "cancelada")
            .whereEqualTo("canceladoPor", "passageiro")
            .whereEqualTo("canceladoVisto", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                cancelamentosNaoVistos = snapshot?.size() ?: 0
                emitirCombinado()
            }

        awaitClose {
            reg1.remove()
            reg2.remove()
        }
    }

    // Confirmadas ainda não vistas + cancelamentos feitos pelo motorista
    // que o passageiro ainda não viu — mesmo espírito de
    // escutarContagemPendentes acima, do outro lado.
    override fun escutarContagemConfirmacoesNaoVistas(): Flow<Int> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(0)
            close()
            return@callbackFlow
        }

        var confirmadasNaoVistas = 0
        var cancelamentosNaoVistos = 0
        fun emitirCombinado() = trySend(confirmadasNaoVistas + cancelamentosNaoVistos)

        val reg1 = colecaoSolicitacoes()
            .whereEqualTo("passageiroId", uid)
            .whereEqualTo("status", "confirmada")
            .whereEqualTo("confirmacaoVista", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                confirmadasNaoVistas = snapshot?.size() ?: 0
                emitirCombinado()
            }

        val reg2 = colecaoSolicitacoes()
            .whereEqualTo("passageiroId", uid)
            .whereEqualTo("status", "cancelada")
            .whereEqualTo("canceladoPor", "motorista")
            .whereEqualTo("canceladoVisto", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                cancelamentosNaoVistos = snapshot?.size() ?: 0
                emitirCombinado()
            }

        awaitClose {
            reg1.remove()
            reg2.remove()
        }
    }

    override suspend fun marcarConfirmacoesComoVistas(): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: throw IllegalStateException("Não há sessão ativa.")
            val confirmadas = colecaoSolicitacoes()
                .whereEqualTo("passageiroId", uid)
                .whereEqualTo("status", "confirmada")
                .whereEqualTo("confirmacaoVista", false)
                .get().await()
            val canceladas = colecaoSolicitacoes()
                .whereEqualTo("passageiroId", uid)
                .whereEqualTo("status", "cancelada")
                .whereEqualTo("canceladoPor", "motorista")
                .whereEqualTo("canceladoVisto", false)
                .get().await()
            if (!confirmadas.isEmpty || !canceladas.isEmpty) {
                val lote = db.batch()
                confirmadas.documents.forEach { doc -> lote.update(doc.reference, "confirmacaoVista", true) }
                canceladas.documents.forEach { doc -> lote.update(doc.reference, "canceladoVisto", true) }
                lote.commit().await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun marcarCancelamentosComoVistos(): Result<Unit> {
        return try {
            val uid = auth.currentUser?.uid ?: throw IllegalStateException("Não há sessão ativa.")
            val snapshot = colecaoSolicitacoes()
                .whereEqualTo("motoristaId", uid)
                .whereEqualTo("status", "cancelada")
                .whereEqualTo("canceladoPor", "passageiro")
                .whereEqualTo("canceladoVisto", false)
                .get().await()
            if (!snapshot.isEmpty) {
                val lote = db.batch()
                snapshot.documents.forEach { doc -> lote.update(doc.reference, "canceladoVisto", true) }
                lote.commit().await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
