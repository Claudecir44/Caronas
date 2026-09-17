package com.cjstudio.caronas

import android.content.Context
import android.util.Log
import me.leolin.shortcutbadger.ShortcutBadger

// Badge numérico no ícone do app na tela inicial (launcher) — chamado
// sempre que o total combinado de "não lido" muda (mensagens de chat +
// solicitações/confirmações/cancelamentos ainda não vistos, ver
// TelaCaronasActivity.atualizarBadgeIconeApp). A API pública do Android só
// deixa o CANAL de notificação pedir um badge (NotificationChannel
// .setShowBadge, já configurado em CaronasFirebaseMessagingService) — quem
// de fato desenha o número em cima do ícone é o launcher de cada
// fabricante, cada um com sua própria API proprietária (MIUI, Samsung,
// Sony, etc.), por isso o ShortcutBadger: ele detecta o launcher atual e
// manda o broadcast/intent certo pra cada um.
//
// Nem todo aparelho/launcher suporta — a própria biblioteca lança exceção
// nesses casos, por isso o try/catch (nunca deve derrubar o app só porque
// o launcher não sabe desenhar badge).
object AppIconBadgeUtil {
    private const val TAG = "AppIconBadgeUtil"

    fun atualizar(context: Context, total: Int) {
        try {
            if (total > 0) {
                ShortcutBadger.applyCount(context.applicationContext, total)
            } else {
                ShortcutBadger.removeCount(context.applicationContext)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Launcher não suporta badge de ícone: ${e.message}")
        }
    }
}
