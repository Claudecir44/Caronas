package com.cjstudio.caronas

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

// "Orientações" do administrador: guia de como monitorar e ajustar o app,
// aberto por Configurações (⚙️ do painel Administração), logo abaixo dos
// Termos de Uso e Privacidade. Texto estático (ver OrientacoesAdminConteudo),
// um card por seção que abre ao tocar (ver OrientacaoAdminAdapter).
class OrientacoesAdminCaronasActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_orientacoes_admin_caronas)

        val lista = findViewById<RecyclerView>(R.id.rvOrientacoesAdmin)
        lista.layoutManager = LinearLayoutManager(this)
        lista.adapter = OrientacaoAdminAdapter(OrientacoesAdminConteudo.secoes())
    }
}
