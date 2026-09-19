package com.cjstudio.caronas

// Relatório de falhas (Firebase Crashlytics). Crashes e ANRs são capturados
// sozinhos; aqui ficam só os pontos de contexto que ajudam a achar a causa:
// quem estava logado e em qual flavor/build. Nunca lança — falha silenciosa.
interface IFalhasRepository {
    // uid do Firebase Auth do usuário/admin logado (identificador anônimo, sem nome
    // nem e-mail); null ao sair da conta.
    fun definirUsuario(uid: String?)

    // Chave de contexto anexada a todo relatório (ex.: "tipo" = usuario/admin).
    fun definirChave(chave: String, valor: String)

    // Registra um erro tratado (não derruba o app) pra aparecer no painel.
    fun registrar(erro: Throwable)
}
