package com.cjstudio.caronas

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// Criptografia simétrica (AES-256-GCM) do campo "conteudo" das mensagens do
// chat entre administradores (conversasAdmin/{id}/mensagens/{id}) — mesmo
// esquema do Match (MensagemCryptoUtil), chave própria do Caronas (as duas
// não têm relação, cada app com seu segredo). Protege contra leitura direta
// no Firestore (outro admin sem permissão "chatAdmin", um export/vazamento
// de dados) — mas a chave vive compilada no app E no painel web (os dois
// lados precisam decifrar em tempo real), então não é barreira contra quem
// descompila o APK ou lê o código-fonte do painel.
//
// Mensagens salvas antes desse recurso (não deveria haver nenhuma, já que
// nasce junto com o chat) continuam em texto puro: descriptografar()
// detecta a ausência do prefixo PREFIXO_CIFRADO e devolve o texto como veio.
object ChatAdminCryptoUtil {

    private const val CHAVE_BASE64 = "iy9R6dvK3JZL52hV2Z7101UDDYX7MCqsiWtDZx55T8g="
    private const val PREFIXO_CIFRADO = "ENC1:"
    private const val TAMANHO_IV = 12
    private const val TAMANHO_TAG_BITS = 128

    private val chaveSecreta by lazy {
        SecretKeySpec(Base64.decode(CHAVE_BASE64, Base64.NO_WRAP), "AES")
    }

    fun criptografar(textoPlano: String?): String? {
        if (textoPlano.isNullOrEmpty()) return textoPlano
        return try {
            val iv = ByteArray(TAMANHO_IV).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, chaveSecreta, GCMParameterSpec(TAMANHO_TAG_BITS, iv))
            val textoCifrado = cipher.doFinal(textoPlano.toByteArray(Charsets.UTF_8))
            PREFIXO_CIFRADO + Base64.encodeToString(iv + textoCifrado, Base64.NO_WRAP)
        } catch (e: Exception) {
            textoPlano
        }
    }

    fun descriptografar(textoArmazenado: String?): String? {
        if (textoArmazenado.isNullOrEmpty() || !textoArmazenado.startsWith(PREFIXO_CIFRADO)) {
            return textoArmazenado
        }
        return try {
            val dados = Base64.decode(textoArmazenado.removePrefix(PREFIXO_CIFRADO), Base64.NO_WRAP)
            val iv = dados.copyOfRange(0, TAMANHO_IV)
            val textoCifrado = dados.copyOfRange(TAMANHO_IV, dados.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, chaveSecreta, GCMParameterSpec(TAMANHO_TAG_BITS, iv))
            String(cipher.doFinal(textoCifrado), Charsets.UTF_8)
        } catch (e: Exception) {
            textoArmazenado
        }
    }
}
