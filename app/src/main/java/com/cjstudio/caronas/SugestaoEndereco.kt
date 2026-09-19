package com.cjstudio.caronas

// Tipo de campo que está pedindo sugestões — o servidor devolve coisas diferentes
// pra cada um (functions/index.js: autocompletarEndereco).
enum class TipoAutocomplete(val id: String) {
    // Cidade: só municípios; o campo recebe apenas o nome da cidade.
    CIDADE("cidade"),

    // Endereço: rua/local, número, bairro e cidade.
    ENDERECO("endereco")
}

// "texto" é o que aparece na lista (ex.: "Cachoeirinha - RS"); "valor" é o que vai
// pro campo ao tocar (ex.: "Cachoeirinha").
data class SugestaoEndereco(val texto: String, val valor: String)
