package com.cjstudio.caronas

import android.content.Context
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder

// Diálogo genérico "tem certeza + senha do admin master" — reusado por
// qualquer ação destrutiva do painel admin que exige essa segunda trava
// (mesmo padrão de confirmação já usado em
// CadastroAdminCaronasActivity.confirmarExclusao, só que ali o campo de
// senha já fica sempre visível no formulário; aqui, chamado a partir de
// uma LISTA (toque e segure), não tem formulário aberto, então o campo de
// senha entra dentro do próprio diálogo).
object ConfirmarSenhaMasterDialogUtil {

    fun mostrar(
        context: Context,
        titulo: String,
        mensagem: String,
        // Texto do botão positivo — "Excluir" por padrão (a maioria dos usos
        // até aqui era ação destrutiva), mas outras ações também exigem
        // senha master (ver DetalhesUsuarioAdminActivity.confirmarSalvar,
        // que passa R.string.salvar).
        textoBotaoConfirmar: Int = R.string.excluir,
        // Dica do campo de senha — a maioria das ações (admin CRUD) só
        // aceita a senha master mesmo; editar/excluir um USUÁRIO comum
        // passa uma dica diferente, porque agora também aceita a própria
        // senha do admin logado, se ele tiver permissão (ver
        // autorizarComSenhaMasterOuPropria em functions/index.js).
        hintSenha: Int = R.string.admin_cadastro_senha_autorizacao_hint,
        onConfirmar: (senhaMaster: String) -> Unit
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_confirmar_senha_master, null)
        view.findViewById<TextView>(R.id.tvMensagemConfirmarSenha).text = mensagem
        val etSenha = view.findViewById<EditText>(R.id.etSenhaMasterConfirmar)
        etSenha.hint = context.getString(hintSenha)
        etSenha.habilitarToggleSenha()

        MaterialAlertDialogBuilder(context)
            .setTitle(titulo)
            .setView(view)
            .setPositiveButton(textoBotaoConfirmar) { _, _ ->
                val senha = etSenha.text.toString()
                if (senha.isEmpty()) {
                    Toast.makeText(context, R.string.admin_cadastro_erro_senha_autorizacao, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                onConfirmar(senha)
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }
}
