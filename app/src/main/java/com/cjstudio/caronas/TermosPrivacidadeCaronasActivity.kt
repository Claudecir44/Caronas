package com.cjstudio.caronas

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

// Mostra Termos de Uso + Política de Privacidade (um único documento
// combinado, já que o botão que abre esta tela é só um — ver
// ConfiguracoesCaronasActivity). Texto lido de res/raw, mesmo espírito do
// fallback offline do Match (ConsentimentoActivity) — sem o lado
// "override via Firestore" do Match, que só se justifica na escala e no
// ritmo de mudança regulatória que o Match já tem.
class TermosPrivacidadeCaronasActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_termos_privacidade_caronas)

        val texto = resources.openRawResource(R.raw.termos_privacidade_caronas)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        findViewById<TextView>(R.id.tvConteudoTermos).text = texto
    }
}
