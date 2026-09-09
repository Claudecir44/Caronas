package com.cjstudio.caronas

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

// Copia o conteúdo do Uri escolhido no Photo Picker do sistema pra um
// arquivo temporário local — necessário porque a permissão de leitura do
// content:// original pode ser revogada antes do upload de verdade
// terminar (mesmo padrão usado no CadastroUsuarioActivity.kt do Match).
object FotoUtil {
    fun copiarUriParaArquivoTemporario(context: Context, uri: Uri): Uri? {
        return try {
            val arquivo = File(context.cacheDir, "foto_${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { entrada ->
                arquivo.outputStream().use { saida -> entrada.copyTo(saida) }
            }
            Uri.fromFile(arquivo)
        } catch (e: Exception) {
            null
        }
    }
}
