package com.cjstudio.caronas

import android.util.Log
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

// Chama a Cloud Function "autocompletarEndereco" (ver functions/index.js) em
// vez de bater direto na API da LocationIQ — a chave real da LocationIQ só
// existe no servidor agora, nunca dentro do APK (mesmo motivo/padrão do
// proxy de Google Maps no Match: uma chave embarcada no cliente é
// extraível por engenharia reversa, e o autocomplete exige usuário
// autenticado de qualquer forma nas telas onde é usado).
@Singleton
class AutocompleteRepository @Inject constructor(
    private val functions: FirebaseFunctions
) : IAutocompleteRepository {

    private companion object {
        const val TAG = "AutocompleteRepository"
    }

    override suspend fun autocompletar(
        consulta: String,
        tipo: TipoAutocomplete,
        cidadeContexto: String?
    ): List<SugestaoEndereco> {
        if (consulta.isBlank()) return emptyList()
        return try {
            val parametros = mutableMapOf<String, Any>("consulta" to consulta, "tipo" to tipo.id)
            if (tipo == TipoAutocomplete.ENDERECO && !cidadeContexto.isNullOrBlank()) {
                parametros["cidade"] = cidadeContexto.trim()
            }
            val resultado = functions.getHttpsCallable("autocompletarEndereco")
                .call(parametros)
                .await()
            @Suppress("UNCHECKED_CAST")
            val dados = resultado.data as? Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val itens = dados?.get("itens") as? List<Map<String, Any?>> ?: return emptyList()
            itens.mapNotNull { item ->
                val texto = item["texto"] as? String ?: return@mapNotNull null
                val valor = item["valor"] as? String ?: texto
                SugestaoEndereco(texto, valor)
            }
        } catch (e: Exception) {
            // Ver comentário em IAutocompleteRepository — nunca propaga erro.
            Log.w(TAG, "Erro ao buscar sugestões: ${e.message}")
            emptyList()
        }
    }
}
