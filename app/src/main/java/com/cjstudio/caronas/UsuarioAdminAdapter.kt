package com.cjstudio.caronas

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.error
import coil3.request.placeholder
import coil3.request.transformations
import coil3.transform.CircleCropTransformation

class UsuarioAdminAdapter(
    private val usuarios: List<Usuario>,
    // Null nas listas Motoristas/Passageiros (só exibição). Preenchido na
    // seção "Mensagens" quando a busca acha mais de um usuário — toque
    // escolhe de qual deles ver as conversas (ver
    // AdministracaoCaronasActivity.buscarMensagens).
    private val onClick: ((Usuario) -> Unit)? = null,
    // Toque e segure — remove o cadastro por completo, com confirmação de
    // senha (ver AdministracaoCaronasActivity.confirmarRemoverUsuario). Só
    // preenchido na lista de Passageiros.
    private val onLongClick: ((Usuario) -> Unit)? = null
) : RecyclerView.Adapter<UsuarioAdminAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivFoto: ImageView = view.findViewById(R.id.ivFotoUsuarioAdmin)
        val tvNome: TextView = view.findViewById(R.id.tvNomeUsuarioAdmin)
        val tvEmail: TextView = view.findViewById(R.id.tvEmailUsuarioAdmin)
        val tvTelefone: TextView = view.findViewById(R.id.tvTelefoneUsuarioAdmin)
        val tvVeiculo: TextView = view.findViewById(R.id.tvVeiculoUsuarioAdmin)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_usuario_admin, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val usuario = usuarios[position]
        val context = holder.itemView.context

        holder.tvNome.text = usuario.nomeCompleto ?: ""
        holder.tvEmail.text = usuario.email ?: ""
        holder.tvTelefone.text = usuario.telefone ?: ""

        val veiculo = usuario.veiculo
        if (veiculo != null && veiculo.estaPreenchido()) {
            holder.tvVeiculo.visibility = View.VISIBLE
            holder.tvVeiculo.text = context.getString(
                R.string.admin_veiculo_formato, veiculo.marca, veiculo.modelo, veiculo.cor, veiculo.placa
            )
        } else {
            holder.tvVeiculo.visibility = View.GONE
        }

        if (!usuario.fotoUrl.isNullOrEmpty()) {
            holder.ivFoto.load(usuario.fotoUrl) {
                transformations(CircleCropTransformation())
                placeholder(R.drawable.ic_person_default)
                error(R.drawable.ic_person_default)
            }
        } else {
            holder.ivFoto.setImageResource(R.drawable.ic_person_default)
        }

        if (onClick != null) {
            holder.itemView.isClickable = true
            holder.itemView.isFocusable = true
            holder.itemView.setOnClickListener { onClick.invoke(usuario) }
        }
        if (onLongClick != null) {
            holder.itemView.isLongClickable = true
            holder.itemView.setOnLongClickListener {
                onLongClick.invoke(usuario)
                true
            }
        }
    }

    override fun getItemCount() = usuarios.size
}
