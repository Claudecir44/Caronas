package com.cjstudio.caronas

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

// Mesmo padrão do MatchFirebaseMessagingService: mensagens SEMPRE
// data-only (nunca um bloco "notification" mandado pelo servidor) — é
// este onMessageReceived quem decide o título e monta a notificação,
// baseado no campo "tipo" do payload (ver functions/index.js:
// notificarSolicitacaoViagem/notificarViagemAceita/notificarMensagemCaronas).
// Um canal por tipo, criado sob demanda aqui dentro (não no
// Application.onCreate). Tocar na notificação sempre abre a tela principal
// — sem deep-link pra tela específica (mesma simplificação do Match: abre
// a lista, não o item exato).
class CaronasFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        FcmTokenUtil.atualizarToken(uid)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val dados = message.data
        val corpo = dados["corpo"] ?: return

        val (titulo, canalId, canalNome) = when (dados["tipo"]) {
            "solicitacaoViagem" -> Triple(
                getString(R.string.notificacao_titulo_solicitacao), "solicitacoes_caronas", getString(R.string.notificacao_canal_solicitacao)
            )
            "viagemAceita" -> Triple(
                getString(R.string.notificacao_titulo_aceita), "viagens_aceitas_caronas", getString(R.string.notificacao_canal_aceita)
            )
            "viagemCancelada" -> Triple(
                getString(R.string.notificacao_titulo_cancelada), "viagens_canceladas_caronas", getString(R.string.notificacao_canal_cancelada)
            )
            "mensagemCaronas" -> Triple(
                getString(R.string.notificacao_titulo_mensagem), "mensagens_caronas", getString(R.string.notificacao_canal_mensagem)
            )
            else -> return
        }

        val idNotificacao = dados["id"]?.hashCode() ?: System.currentTimeMillis().toInt()
        mostrarNotificacao(canalId, canalNome, titulo, corpo, idNotificacao)
    }

    private fun mostrarNotificacao(canalId: String, canalNome: String, titulo: String, corpo: String, idNotificacao: Int) {
        val intent = Intent(this, TelaCaronasActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, idNotificacao, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val somPadrao = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(canalId, canalNome, NotificationManager.IMPORTANCE_HIGH).apply {
                // Som + vibração explícitos (não só confiar no padrão do
                // canal) e badge no ícone do app na tela inicial — uma vez
                // que o canal é criado num aparelho, essas configurações não
                // mudam mais sozinhas em criações futuras (só reinstalando o
                // app ou o próprio usuário ajustando nas configs do
                // sistema), então valem sempre que o canal ainda não existia.
                setSound(somPadrao, AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
                enableVibration(true)
                setShowBadge(true)
            }
            manager.createNotificationChannel(canal)
        }

        val notificacao = NotificationCompat.Builder(this, canalId)
            .setSmallIcon(R.drawable.ic_carro_default)
            .setContentTitle(titulo)
            .setContentText(corpo)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            // Ignorado a partir do Android 8 (o canal acima manda), mas
            // garante som/vibração em versões mais antigas, que não têm
            // canal de notificação.
            .setSound(somPadrao)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
            .build()

        manager.notify(idNotificacao, notificacao)
    }
}
