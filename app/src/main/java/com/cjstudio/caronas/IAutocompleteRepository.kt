package com.cjstudio.caronas

interface IAutocompleteRepository {
    // Sugestões de cidade ou endereço pra "consulta" — sempre via Cloud
    // Function (nunca chama a LocationIQ direto do app, ver
    // AutocompleteRepository). "cidadeContexto" só vale pra ENDERECO: a cidade
    // já digitada no campo ao lado, pra achar a rua da cidade certa. Nunca
    // lança: falha de rede/servidor só resulta em lista vazia (autocomplete é
    // uma ajuda visual, não pode travar o formulário).
    suspend fun autocompletar(
        consulta: String,
        tipo: TipoAutocomplete,
        cidadeContexto: String? = null
    ): List<SugestaoEndereco>
}
