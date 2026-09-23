package com.cjstudio.caronas

// Conteúdo da seção "Orientações" do painel Administração (botão logo abaixo de
// Financeiro): guia estático de como MONITORAR e AJUSTAR o Caronas. Não chama
// nada no servidor — é texto de referência. Foi escrito a partir do que o app
// realmente faz hoje (regras em firestore.rules, functions/index.js e as
// constantes citadas em "Onde ajustar"); se uma regra mudar, atualize aqui
// junto. Cada item vira um card que abre ao tocar (ver OrientacaoAdminAdapter).
object OrientacoesAdminConteudo {

    fun secoes(): List<Pair<String, String>> = listOf(

        "🧭 Rotina de monitoramento (o que olhar e quando)" to """
Todo dia (5 minutos):
• Botão Sugestões: se o número vermelho aparecer, há reclamações, sugestões ou denúncias pendentes. Denúncia primeiro — envolve segurança de pessoas.
• Aviso de erro nos e-mails do Firebase (Crashlytics e Functions), se você receber.

Toda semana:
• Financeiro: confira os pagamentos de motorista da semana (filtro "Semanal") e se o total bate com o painel do Mercado Pago.
• Motoristas e Passageiros: veja o contador de cada lista para acompanhar o crescimento e cadastros estranhos (nome vazio, e-mail descartável, muitos cadastros no mesmo dia).
• Viagens: procure viagens canceladas em excesso do mesmo motorista ou do mesmo passageiro.

Todo mês:
• Financeiro > Gerar relatório: PDF do mês com todos os pagamentos, guarde para a contabilidade.
• Firebase Console: uso e custo (Firestore, Functions, Authentication) para não ser surpreendido por cobrança.
• Google Play Console: nota, avaliações e falhas (ANR/travamentos) do app.
        """.trimIndent(),

        "🖥️ O que faz cada botão do painel" to """
• Editar Perfil (topo): abre a busca de administradores por CPF — encontra um admin/colaborador já cadastrado e abre o cadastro dele pra editar (exige a senha do administrador master). Não cadastra ninguém novo, só edita quem já existe.
• ⚙️ Configurações (canto superior esquerdo): Termos de Uso e Privacidade e, pra quem tem a permissão "administradores", o card Administradores.
• 👑 Administradores (dentro de Configurações): duas abas, Admins e Colaboradores. Cadastra, edita (inclusive foto — hoje dá pra trocar a foto de qualquer um, não só a própria) e exclui administradores/colaboradores, com a mesma grade de permissões por seção do painel. Colaborador precisa validar o e-mail antes do primeiro login (mesma tela de login do admin) e aparece com "(colaborador)" abaixo do próprio nome, no topo do painel.
• Motoristas: lista de quem tem veículo cadastrado, com número de viagens realizadas e total recebido. Toque para ver o cadastro completo (inclui o CPF); toque e segure para excluir.
• Passageiros: todos os cadastros (todo motorista também é passageiro). Mesmos gestos.
• 💰 Financeiro: pagamentos do acesso de motorista, com filtro por período e geração de relatório mensal em PDF.
• Sugestões: reclamações, sugestões e denúncias enviadas pelos usuários; responda por e-mail e arquive.
• Mensagens: busque um usuário (nome, telefone, e-mail ou CPF de motorista) para ler as conversas dele.
• Viagens: escolha entre viagens de motoristas (ofertas) ou de passageiros (solicitações).
• Orientações: este guia.
        """.trimIndent(),

        "👥 Cadastros: consultar, corrigir e excluir" to """
Consultar: em Motoristas ou Passageiros, toque no cadastro. Aparecem foto, e-mail, telefone, data de cadastro, veículo e, para motorista, o CPF vinculado (ou "Não informado" para motorista antigo que ainda não completou).

Corrigir: no cadastro, toque em Editar, altere nome, telefone ou dados do veículo e Salvar. O servidor pede a senha do administrador master. O e-mail (login) e a foto não mudam por aqui.
• Motorista: nome e telefone novos também precisam ser únicos entre motoristas — se já existir outro motorista com o mesmo nome ou telefone, o servidor recusa e mostra qual campo repetiu.

Excluir: toque e segure na linha, confirme e digite a senha master. A exclusão é COMPLETA e irreversível: apaga solicitações, caronas, conversas, avaliações, fotos, o vínculo de CPF e a conta de login. Só use quando o usuário pedir ou houver violação grave dos Termos.

Quando um usuário pede a própria exclusão, ele mesmo consegue em Editar Cadastro > Excluir cadastro (pede a senha dele). Você só precisa excluir quando ele não consegue acessar a conta.
        """.trimIndent(),

        "🪪 CPF do motorista e cadastros duplicados" to """
Regra: só o MOTORISTA informa CPF (passageiro não). O CPF, o nome completo, o e-mail e o telefone ficam vinculados a uma única conta de motorista — nenhum desses quatro pode se repetir em outro motorista. Isso existe para impedir que a mesma pessoa abra outra conta e ganhe de novo as 10 caronas grátis.

Como funciona por trás:
• O CPF fica guardado à parte (nunca no cadastro público do usuário) e só o dono e os administradores leem.
• Depois de vinculado, o CPF não pode ser alterado — nem pelo usuário nem pelo painel.
• Uma conta que era só de passageiro pode virar motorista sem conflito: o vínculo usa a mesma conta.
• Se a conta que segurava aquele CPF foi excluída, os dados ficam livres para um novo cadastro.
• Motorista antigo sem CPF NÃO consegue publicar caronas até informar o CPF em Editar Cadastro: ao entrar como motorista o app já o leva para essa tela, e ao tentar publicar avisa e leva de novo.

Quando o usuário reclamar "já existe um motorista com o mesmo CPF/telefone/nome":
1) Em Motoristas, busque pelo CPF, telefone ou nome informado e descubra quem é o outro cadastro.
2) Se for a mesma pessoa (conta antiga esquecida): peça que entre na conta antiga, ou exclua a antiga em Passageiros (toque e segure) e oriente a tentar de novo.
3) Se forem duas pessoas diferentes com o mesmo nome (homônimos): hoje o nome também precisa ser único entre motoristas. A saída é o segundo motorista usar o nome completo com um sobrenome a mais ou o nome do meio. Se isso virar problema frequente, dá para retirar o nome da regra (é um ajuste na Cloud Function registrarMotorista).
4) Se suspeitar de fraude (mesmo CPF em nomes diferentes), não libere: exclua o cadastro suspeito e registre o caso.

Limitação conhecida: quem exclui a própria conta e se cadastra de novo recomeça as 10 caronas grátis, porque os dados ficam livres. Se isso for abusado, o caminho é guardar o contador de caronas por CPF.
        """.trimIndent(),

        "💬 Chat: como funciona e como monitorar" to """
Regra do chat (vale para motorista e passageiro):
• Nasce quando o passageiro solicita a vaga: antes disso não existe conversa.
• Fica aberto até 6 horas depois da chegada prevista da viagem (partida + tempo aproximado de viagem).
• Se o passageiro OU o motorista cancelar a solicitação, o chat é bloqueado e não reabre.
• Depois de bloqueado ou encerrado, as mensagens continuam visíveis para os dois, só que sem enviar novas.
• Ninguém apaga mensagem nem conversa (nem "só para mim"). O histórico é definitivo, para servir de prova em denúncias.

Monitorar: em Mensagens, digite nome, telefone, e-mail (ou CPF de motorista), toque no usuário e leia as conversas. Use isso só para apurar denúncia, suporte ou segurança — mensagens são privadas, não leia por curiosidade.

Se um usuário disser "não consigo mandar mensagem": confira em Viagens (passageiros) o status da solicitação. Cancelada = chat bloqueado, é o esperado. Confirmada ou solicitada mas já passou mais de 6h da chegada = encerrado, também esperado. Fora isso, peça para atualizar o app e checar a internet.
        """.trimIndent(),

        "📝 Sugestões, reclamações e denúncias" to """
Tudo o que o usuário envia em Configurações > Reclamações, Sugestões e Denúncias chega em Sugestões. O número vermelho no botão é a quantidade sem resposta; você também recebe notificação no celular quando chega uma nova.

Como atender:
1) Abra o item, leia com calma e (se for denúncia) confira o contexto em Mensagens e Viagens.
2) Toque em responder e escreva a resposta. Ela é enviada por e-mail ao autor e o item passa a "respondido", registrando data, hora e o CPF de quem respondeu.
3) Depois de respondido, arquive para tirar da fila. O botão "Arquivados" alterna entre as duas listas.

Boas práticas:
• Responda denúncia em até 24 horas; sugestão e reclamação em até 3 dias úteis.
• Seja objetivo e cordial, sem expor dados de terceiros (não diga quem denunciou quem).
• Denúncia procedente de conduta grave: registre o caso, avise o denunciado se aplicável e, se necessário, exclua o cadastro (ver "Cadastros").
• A resposta só sai se o e-mail de suporte do servidor estiver configurado. Se der erro ao responder, veja "Monitoramento técnico".
        """.trimIndent(),

        "💰 Financeiro e acesso pago do motorista" to """
Modelo: o motorista tem 10 caronas oferecidas grátis. A partir da 11ª, precisa de um plano de acesso pago pelo Mercado Pago (pagamento único, sem renovação automática):
• Mensal: R${'$'} 17,99 por 30 dias.
• Trimestral: R${'$'} 44,99 por 90 dias.
Só dá para pagar de novo faltando no máximo 2 dias para o acesso atual vencer (evita empilhar períodos). Passageiro nunca paga nada.

O que você vê em Financeiro: cada pagamento aprovado (motorista, plano, valor, data e validade), total arrecadado, motoristas distintos que pagaram e filtro por período (hoje, semana, mês, trimestre, semestre, ano ou datas personalizadas).

Relatório mensal: botão de gerar relatório > escolha mês e ano > PDF com todos os pagantes e o total. Guarde mensalmente.

Estornos: quando o Mercado Pago avisa reembolso, estorno ou chargeback, o servidor marca o pagamento como estornado e remove os dias de acesso correspondentes. Pagamento estornado NÃO entra nos totais do Financeiro.

Conferência: compare o total do mês com o painel do Mercado Pago. Diferença costuma ser: pagamento pendente (ainda não aprovado), estorno recente ou pagamento aprovado cujo aviso (webhook) falhou.

Motorista diz "paguei e não liberou":
1) Confirme no painel do Mercado Pago que o pagamento está APROVADO (Pix pode demorar alguns minutos; cartão pode ficar em análise).
2) Confirme no Financeiro se o pagamento aparece. Se aparece, peça para o motorista fechar e reabrir o app (o acesso é relido ao abrir).
3) Se está aprovado no Mercado Pago mas não aparece no Financeiro, o aviso automático falhou: peça ao desenvolvedor para consultar o pagamento pelo servidor (função checkPaymentStatusMotorista) e verificar os logs do webhook.
        """.trimIndent(),

        "🚗 Viagens, solicitações e status" to """
Oferta (motorista): rota completa (origem, paradas e destino), data e hora, vagas, valor por vaga e distância. Status na tela do motorista: ativa, concluída ou cancelada.

Solicitação (passageiro): nasce como "solicitada", vira "confirmada" quando o motorista aceita e "cancelada" se qualquer um cancelar. Uma solicitação cancelada não volta a ficar ativa.

"Concluída" nunca é gravada: o app considera a viagem concluída 10 minutos depois do horário de partida. Por isso:
• Oferta concluída não pode mais ser editada (só excluída) — o servidor também bloqueia.
• A avaliação só é liberada depois que a viagem foi confirmada E já aconteceu.
• Em Minhas Ofertas do motorista, viagens concluídas aparecem recolhidas (toque para abrir).

Vagas: cada solicitação ocupa uma vaga só nos trechos que percorre (embarque até desembarque). O app desconta as solicitações pendentes e confirmadas — a vaga volta quando alguém cancela.

Para investigar uma viagem: em Viagens escolha Motoristas (ofertas) ou Passageiros (solicitações) e compare rota, data, valor e status. O total "recebido" do motorista soma só solicitações CONFIRMADAS; o "pago" do passageiro soma tudo que não foi cancelado.
        """.trimIndent(),

        "🧮 Valor sugerido e tempo aproximado de viagem" to """
Ambos são ESTIMATIVAS calculadas no aparelho, sem serviço pago de rotas. Não são preço de mercado nem horário garantido.

Valor sugerido (rateio do custo, não tarifa): distância em linha reta entre as cidades × 1,3 (fator de estrada) × tarifa por km (cerca de R${'$'} 0,27/km), com adicional de 30% em fim de semana e feriado nacional, arredondado para múltiplo de R${'$'} 5 e piso de R${'$'} 10. O motorista pode alterar o valor livremente; a plataforma não se manifesta sobre ele (ver Termos, item 2A).

Tempo aproximado: distância estimada ÷ velocidade média — até 20 km, 35 km/h; até 100 km, 60 km/h; acima disso, 75 km/h — arredondado de 5 em 5 minutos. Não considera trânsito, obras ou paradas. Aparece nos cards do motorista e do passageiro; o passageiro vê só o tempo do trecho que buscou.

Também usa esse tempo para a chegada prevista, que define quando o chat encerra (6h depois).

Se o usuário reclamar que o tempo ou o valor ficou "errado": explique que é uma aproximação e, no caso do valor, que o motorista define o preço final. Se houver divergência sistemática (sempre alto ou sempre baixo), é sinal de ajustar as constantes (ver "Onde ajustar").
        """.trimIndent(),

        "🔔 Notificações" to """
O app avisa por notificação: nova solicitação de vaga (para o motorista), viagem aceita, viagem cancelada (para quem NÃO cancelou), nova mensagem no chat e, para o administrador, nova reclamação/sugestão/denúncia. Os avisos são enviados pelo servidor.

O ícone do app também mostra o número de pendências (solicitações e cancelamentos não vistos, mensagens não lidas).

Se o usuário disser que não recebe:
1) Notificações permitidas para o app nas configurações do Android (a partir do Android 13 o app pede a permissão na primeira abertura).
2) Economia de bateria: alguns aparelhos (Xiaomi, Samsung, Motorola) matam o app em segundo plano — tirar o Caronas da otimização de bateria costuma resolver.
3) O usuário precisa estar logado no aparelho — sair da conta interrompe os avisos.
4) Se ninguém recebe nada, é problema do servidor: veja "Monitoramento técnico" (logs das funções notificarSolicitacaoViagem, notificarViagemAceita, notificarViagemCancelada, notificarMensagemCaronas e notificarNovaManifestacao).
        """.trimIndent(),

        "🩺 Monitoramento técnico (Firebase, Mercado Pago, Google Play)" to """
Projeto Firebase: caronas-6b0c4 (console.firebase.google.com). O que olhar:
• Crashlytics: travamentos do app (usuario e admin) por versão, com o trecho do código que falhou. Abra depois de cada versão nova publicada.
• Functions > Logs: erros das funções. Filtre por gravidade "Erro". Procure por paymentWebhookMotorista (pagamentos), responderManifestacao (e-mail de resposta), autocompletarEndereco (busca de cidades) e registrarMotorista (vínculo do CPF).
• Firestore > Uso: leituras, escritas e exclusões por dia. Pico anormal indica loop ou abuso. Coleções principais: usuarios, caronas (com subcoleção ocupacao), solicitacoes, conversas (subcoleção mensagens), avaliacoes, manifestacoes, pagamentosMotorista, motoristasVinculo.
• Authentication: lista de contas e e-mails verificados. Conta que não verifica o e-mail não consegue usar o app.
• Uso e faturamento: acompanhe o custo mensal e configure um alerta de orçamento (Faturamento > Orçamentos e alertas).

Mercado Pago (painel do vendedor): pagamentos, estornos e chargebacks. É a fonte oficial dos valores; o Financeiro do app deve bater com ele.

Google Play Console: estatísticas de instalação, avaliações, erros ANR/travamento e a versão em produção. Responda avaliações negativas.

Serviços de terceiros: a busca de cidades e endereços usa o LocationIQ (chave no servidor). Se o autocompletar parar de sugerir, verifique se a cota do plano do LocationIQ acabou ou se a chave foi alterada.

Sinais de problema e o que investigar:
• Muitos "Erro ao carregar" no app: Firestore fora do ar ou regras negando (veja Functions/Firestore no console e a página de status do Firebase).
• Pagamento aprovado e sem acesso: webhook (ver "Financeiro").
• E-mail de resposta de sugestão não sai: credenciais do e-mail de suporte do servidor.
        """.trimIndent(),

        "🛠️ Onde ajustar (parâmetros do app)" to """
Estes ajustes exigem mudar o código ou o servidor e publicar (ver "Publicar atualizações"). Não dá para mudar por este painel.

• Planos e preços do motorista: PLANOS_MOTORISTA em functions/index.js (valor e dias). Os textos de preço do app (strings de plano) precisam ser atualizados junto.
• Caronas grátis (hoje 10): AcessoMotoristaUtil.CARONAS_GRATUITAS no app E a regra permiteOferecerCarona em firestore.rules (o número 10). Os dois têm que ser iguais.
• Janela de renovação (2 dias): AcessoMotoristaUtil.JANELA_RENOVACAO_DIAS e ACESSO_MOTORISTA_JANELA_RENOVACAO_DIAS em functions/index.js.
• Tarifa por km, adicional de fim de semana/feriado, valor mínimo e arredondamento: DistanciaUtil (TARIFA_POR_KM_REAIS, ADICIONAL_FIM_DE_SEMANA_FERIADO, VALOR_MINIMO_REAIS).
• Velocidades do tempo de viagem: TempoViagemUtil (VELOCIDADE_URBANO_KMH, MEDIO e RODOVIA, e os limites de 20 e 100 km).
• Tempo para uma viagem virar "concluída" (10 min): StatusViagemUtil.TOLERANCIA_MS e viagemJaConcluida em firestore.rules (600000 ms).
• Janela do chat (6 h depois da chegada prevista): ChatUtil.JANELA_APOS_CHEGADA_MS e chatAberto em firestore.rules (21600000 ms).
• Regra de unicidade do motorista (CPF, nome, telefone, e-mail): vincularIdentidadeMotorista em functions/index.js.
• Termos de Uso e Privacidade: arquivo res/raw/termos_privacidade_caronas.txt (o app mostra o texto do arquivo).
• Textos e telas: res/values/strings.xml e os layouts em res/layout.
Sempre que mexer em regra ou função, teste antes com o emulador do Firebase.
        """.trimIndent(),

        "🆘 Problemas comuns e como resolver" to """
• "Não consigo entrar": o e-mail precisa estar verificado. No login, toque em Suporte > Reenviar e-mail de validação (confira o spam). Senha esquecida: Suporte > Esqueci minha senha. Se nada chega, procure o usuário em Passageiros e confirme o e-mail digitado.
• "Não consigo publicar carona": (1) motorista sem CPF vinculado — o app avisa e leva para completar o cadastro; (2) acabaram as 10 grátis e não há plano pago vigente — orientar Configurações > Pagamentos; (3) veículo sem todos os dados preenchidos.
• "Apareceu erro ao me cadastrar como motorista": leia a mensagem — geralmente é CPF inválido ou algum dado (CPF, nome, telefone ou e-mail) já usado por outro motorista. Ver "CPF do motorista e cadastros duplicados".
• "A busca não acha minha carona": a busca compara a cidade digitada (sem diferenciar acento ou maiúscula) com TODAS as paradas da rota, exige a MESMA data, a oferta ativa e a cidade de embarque ANTES da de desembarque na ordem da rota. Trecho na direção contrária não aparece.
• "Cidade ou endereço não sugere": autocompletar depende do serviço externo (LocationIQ) — cota ou chave. Enquanto isso o usuário pode digitar a cidade completa.
• "Passageiro cancelou e o chat sumiu": não sumiu — ficou bloqueado, só leitura. Está correto.
• "Quero apagar minhas mensagens": não é possível, por regra de segurança; explique que o histórico é preservado para os dois.
• "Não recebi a notificação": ver a seção Notificações.
• "Valor da carona está diferente do sugerido": o motorista pode alterar. O passageiro paga o valor do TRECHO que buscou (proporcional ao valor definido pelo motorista).
• "Como excluo minha conta?": Editar Cadastro > Excluir cadastro. Se não conseguir entrar, exclua você mesmo em Passageiros (toque e segure, senha master).
        """.trimIndent(),

        "🔐 Segurança, privacidade e boas práticas" to """
• Senha do administrador master: quem a tem faz ações destrutivas (excluir usuário, editar cadastro, criar/excluir admin). Guarde em gerenciador de senhas, nunca em mensagem ou print, e troque se houver suspeita de vazamento.
• Cada administrador deve usar a PRÓPRIA conta. Ações como responder denúncias ficam registradas com o CPF de quem fez.
• Dados sensíveis (CPF, telefone, e-mail, mensagens) só para a finalidade de suporte, segurança e obrigações legais — LGPD. Não copie, não compartilhe, não envie por aplicativo de mensagem.
• Nunca peça a senha do usuário. O reset é feito pelo e-mail dele.
• Remova o acesso de administrador de quem saiu da equipe.
• Suspeita de fraude (mesmo CPF em nomes diferentes, cadastros em massa, tentativa de comprar acesso sem pagar): não libere nada, registre o caso e, se preciso, exclua o cadastro.
• Denúncia envolvendo risco à integridade física: oriente a vítima a acionar as autoridades (190) e preserve as mensagens — elas não podem ser apagadas.
• Requisição de dados por autoridade: encaminhe ao responsável legal; não entregue por conta própria.
• A conta de administrador só existe para quem foi promovido pelo servidor — usuário comum nunca vira admin sozinho.
        """.trimIndent(),

        "🚀 Publicar atualizações (regras, funções, site e app)" to """
Quem cuida do desenvolvimento publica assim (projeto caronas-6b0c4). Sempre com o Node instalado e, para testar localmente, o Java do Android Studio:
• Regras do banco: npx --yes firebase-tools deploy --only firestore:rules --project caronas-6b0c4
• Cloud Functions: npx --yes firebase-tools deploy --only functions --project caronas-6b0c4
• Painel web (site): npx --yes firebase-tools deploy --only hosting --project caronas-6b0c4
• App (Android): aumente o versionCode e o versionName em app/build.gradle.kts, gere o bundle (.aab) do flavor usuario e envie no Google Play Console (Produção). O flavor admin é interno e distribuído à parte.
Ordem segura quando a mudança mexe em regras E no app: publique primeiro regras e funções, depois o app novo.
Antes de publicar: rode o app nos dois flavors (usuario e admin), teste o fluxo alterado e, para regras, use o emulador do Firestore. Depois de publicar, acompanhe o Crashlytics e os logs das funções por algumas horas.
Atenção: usuários com versão antiga do app continuam existindo. Regras novas mais rígidas podem fazer o app antigo mostrar erro — por isso o app novo deve sair logo em seguida.
        """.trimIndent()
    )
}
