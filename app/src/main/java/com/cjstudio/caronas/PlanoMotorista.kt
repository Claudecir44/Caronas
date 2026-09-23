package com.cjstudio.caronas

// Planos avulsos do acesso pago do motorista. O "id" é o nome do plano que o servidor
// conhece (functions/index.js: PLANOS_MOTORISTA — lá ficam preço e duração de verdade;
// o app só escolhe QUAL). Os valores exibidos ficam em strings.xml
// (plano_motorista_*) e precisam bater com a tabela do servidor.
// "produtoGooglePlay" é o ID do produto avulso cadastrado no Play Console
// (Monetizar → Produtos no app) — igual a PRODUTOS_GOOGLE_PLAY_MOTORISTA no
// servidor, com o mesmo preço de PLANOS_MOTORISTA.
enum class PlanoMotorista(val id: String, val produtoGooglePlay: String) {
    MENSAL("Mensal", "acesso_motorista_mensal"),
    TRIMESTRAL("Trimestral", "acesso_motorista_trimestral")
}
