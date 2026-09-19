package com.cjstudio.caronas

// Planos avulsos do acesso pago do motorista. O "id" é o nome do plano que o servidor
// conhece (functions/index.js: PLANOS_MOTORISTA — lá ficam preço e duração de verdade;
// o app só escolhe QUAL). Os valores exibidos ficam em strings.xml
// (plano_motorista_*) e precisam bater com a tabela do servidor.
enum class PlanoMotorista(val id: String) {
    MENSAL("Mensal"),
    TRIMESTRAL("Trimestral")
}
