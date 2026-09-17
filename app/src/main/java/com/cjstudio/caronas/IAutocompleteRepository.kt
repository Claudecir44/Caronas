package com.cjstudio.caronas

interface IAutocompleteRepository {
    // Sugestões de cidade/endereço pra "consulta" — sempre via Cloud
    // Function (nunca chama a LocationIQ direto do app, ver
    // AutocompleteRepository). Nunca lança: falha de rede/servidor só
    // resulta em lista vazia (autocomplete é uma ajuda visual, não pode
    // travar o formulário).
    suspend fun autocompletar(consulta: String): List<String>
}
