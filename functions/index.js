// Carrega variáveis de ambiente do arquivo .env (se existir).
require('dotenv').config();

const functions = require('firebase-functions');
const { onDocumentCreated, onDocumentUpdated } = require('firebase-functions/v2/firestore');
const admin = require('firebase-admin');
const crypto = require('crypto');
const nodemailer = require('nodemailer');
const mercadopago = require('mercadopago');

admin.initializeApp();

function escapeHtml(valor) {
    if (valor === null || valor === undefined) return '';
    return String(valor)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

// ============================================================
// E-mail de suporte (resposta às reclamações/sugestões/denúncias — ver
// responderManifestacao mais abaixo) — mesmo padrão do Match
// (SUPPORT_EMAIL_USER/SUPPORT_EMAIL_PASSWORD, senha de app do Gmail, não a
// senha normal da conta). Sem essas credenciais, o app ainda aceita novas
// manifestações normalmente (isso é um create direto do cliente, ver
// firestore.rules) — só a resposta por e-mail fica bloqueada.
// ============================================================
const SUPPORT_EMAIL_TO = process.env.SUPPORT_EMAIL_TO || '';
const SUPPORT_EMAIL_USER = process.env.SUPPORT_EMAIL_USER || '';
const SUPPORT_EMAIL_PASSWORD = process.env.SUPPORT_EMAIL_PASSWORD || '';

let supportMailTransporter = null;
if (SUPPORT_EMAIL_USER && SUPPORT_EMAIL_PASSWORD) {
    supportMailTransporter = nodemailer.createTransport({
        service: 'gmail',
        auth: {
            user: SUPPORT_EMAIL_USER,
            pass: SUPPORT_EMAIL_PASSWORD,
        },
    });
    console.log('✅ Transporte de e-mail de suporte configurado com sucesso!');
} else {
    console.error('❌ Credenciais de e-mail de suporte NÃO CONFIGURADAS!');
    console.error('👉 Crie/edite o arquivo .env na pasta functions com:');
    console.error('   SUPPORT_EMAIL_TO=caronasappsuporte@gmail.com');
    console.error('   SUPPORT_EMAIL_USER=caronasappsuporte@gmail.com');
    console.error('   SUPPORT_EMAIL_PASSWORD=SENHA_DE_APP_DE_16_CARACTERES');
    console.error('   (crie uma conta Gmail dedicada + uma "senha de app" em myaccount.google.com/apppasswords)');
}

// ============================================================
// Mercado Pago — acesso pago do motorista. Modelo: as 10 primeiras
// caronas oferecidas são grátis (ver Usuario.caronasOferecidas,
// firestore.rules:permiteOferecerCarona); da 11ª em diante, precisa pagar
// R$15,99 pra liberar 30 dias de acesso — pagamento AVULSO, sem
// renovação automática (mesmo modelo "pagamento único" já usado no
// Premium do Match, só que aqui com um preço/prazo só, sem planos).
// ============================================================
const MERCADOPAGO_ACCESS_TOKEN = process.env.MERCADOPAGO_ACCESS_TOKEN || '';
// Planos avulsos (sem renovação automática) — preço e duração vêm SEMPRE daqui,
// indexados pelo nome do plano; o app só diz QUAL plano quer, nunca quanto vale.
// Os mesmos valores aparecem em strings.xml do app (plano_motorista_*) e
// precisam bater com esta tabela.
const PLANOS_MOTORISTA = {
    Mensal: {
        valor: 17.99,
        dias: 30,
        titulo: 'Acesso motorista Caronas — 30 dias',
        descricao: 'Libera oferecer caronas por 30 dias',
    },
    Trimestral: {
        valor: 44.99,
        dias: 90,
        titulo: 'Acesso motorista Caronas — 90 dias',
        descricao: 'Libera oferecer caronas por 90 dias (3 meses)',
    },
};
const PLANO_MOTORISTA_PADRAO = 'Mensal';
// Pagamentos antigos (antes dos planos) não guardaram a duração: eram todos de 30 dias.
const ACESSO_MOTORISTA_DIAS_LEGADO = 30;
// Só dá pra pagar de novo faltando no máximo isso pro acesso atual vencer —
// evita empilhar vários períodos de uma vez (a tela do app mostra a mesma
// regra, ver AcessoMotoristaUtil.JANELA_RENOVACAO_DIAS, mas a trava de
// verdade é a daqui).
const ACESSO_MOTORISTA_JANELA_RENOVACAO_DIAS = 2;

if (MERCADOPAGO_ACCESS_TOKEN) {
    mercadopago.configure({ access_token: MERCADOPAGO_ACCESS_TOKEN });
    console.log('✅ Mercado Pago configurado com sucesso!');
} else {
    console.error('❌ Token do Mercado Pago NÃO CONFIGURADO!');
    console.error('👉 Crie/edite o arquivo .env na pasta functions com:');
    console.error('   MERCADOPAGO_ACCESS_TOKEN=SEU_TOKEN');
    console.error('   (Mercado Pago → Suas integrações → credenciais de produção)');
    console.error('   Enquanto isso não estiver configurado, o motorista não consegue pagar pra continuar oferecendo caronas depois das 10 gratuitas.');
}

// ============================================================
// Google Play — "User Choice Billing" (Brasil), mesmo esquema do Match: o
// passe do motorista libera uma função do app, então a política de
// pagamentos do Google exige que o próprio Google ofereça a escolha Google
// Play x Mercado Pago antes da compra (ver GooglePlayBillingManager.kt).
// A Google Play Developer API serve pra:
//  1) reportarTransacaoExternaAoGooglePlay — avisar o Google de cada
//     pagamento feito pelo Mercado Pago (obrigatório em até 24h);
//  2) confirmarCompraGooglePlayMotorista — verificar e reconhecer uma
//     compra feita pelo Google Play.
// Credenciais: conta de serviço com acesso ao app no Play Console
// (Usuários e permissões → "Ver dados financeiros" + "Gerenciar pedidos"),
// chave JSON inteira numa linha só.
// ============================================================
const { google } = require('googleapis');

const GOOGLE_PLAY_PACKAGE_NAME = process.env.GOOGLE_PLAY_PACKAGE_NAME || '';
const GOOGLE_PLAY_SERVICE_ACCOUNT_JSON = process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON || '';

if (!GOOGLE_PLAY_PACKAGE_NAME || !GOOGLE_PLAY_SERVICE_ACCOUNT_JSON) {
    console.error('❌ Integração com o Google Play (User Choice Billing) NÃO CONFIGURADA!');
    console.error('👉 Crie/edite o arquivo .env na pasta functions com:');
    console.error('   GOOGLE_PLAY_PACKAGE_NAME=com.cjstudio.caronas.usuario');
    console.error('   GOOGLE_PLAY_SERVICE_ACCOUNT_JSON=\'{"type":"service_account",...}\' (a chave JSON inteira, numa linha só)');
    console.error('   Enquanto isso não estiver configurado, pagamentos pelo Mercado Pago NÃO são reportados ao Google e compras pelo Google Play não são confirmadas.');
}

// Produtos avulsos (compra única) cadastrados no Play Console — IDs iguais
// a PlanoMotorista.produtoGooglePlay no app; os preços lá precisam bater
// com PLANOS_MOTORISTA.
const PRODUTOS_GOOGLE_PLAY_MOTORISTA = {
    acesso_motorista_mensal: 'Mensal',
    acesso_motorista_trimestral: 'Trimestral',
};

let androidPublisherClient = null;
function obterAndroidPublisher() {
    if (androidPublisherClient) return androidPublisherClient;
    if (!GOOGLE_PLAY_SERVICE_ACCOUNT_JSON) return null;
    const auth = new google.auth.GoogleAuth({
        credentials: JSON.parse(GOOGLE_PLAY_SERVICE_ACCOUNT_JSON),
        scopes: ['https://www.googleapis.com/auth/androidpublisher'],
    });
    androidPublisherClient = google.androidpublisher({ version: 'v3', auth });
    return androidPublisherClient;
}

// Avisa o Google de um pagamento feito pelo Mercado Pago depois da escolha
// na tela do Google. Não derruba o webhook se falhar — o acesso já foi
// concedido; só loga pra reportar manualmente se preciso.
async function reportarTransacaoExternaAoGooglePlay({ externalTransactionToken, valor, paymentId }) {
    const androidpublisher = obterAndroidPublisher();
    if (!androidpublisher) {
        console.error('❌ Transação externa NÃO reportada ao Google Play (credenciais ausentes). Pagamento:', paymentId);
        return;
    }
    // Id estável por pagamento: se o MP reenviar a notificação, o Google
    // recusa a duplicata em vez de contar duas vezes.
    const externalTransactionId = `mp_${paymentId}`;
    try {
        await androidpublisher.externaltransactions.createexternaltransaction({
            parent: `applications/${GOOGLE_PLAY_PACKAGE_NAME}`,
            externalTransactionId,
            requestBody: {
                originalPreTaxAmount: { priceMicros: String(Math.round(valor * 1000000)), currency: 'BRL' },
                originalTaxAmount: { priceMicros: '0', currency: 'BRL' },
                transactionTime: new Date().toISOString(),
                oneTimeTransaction: { externalTransactionToken },
                userTaxAddress: { regionCode: 'BR' },
            },
        });
        console.log('✅ Transação externa reportada ao Google Play:', externalTransactionId);
    } catch (error) {
        console.error('❌ Erro ao reportar transação externa ao Google Play (acesso já concedido):', error.message);
    }
}

exports.createPaymentPreferenceMotorista = functions.https.onCall(async (request) => {
    if (!MERCADOPAGO_ACCESS_TOKEN) {
        throw new functions.https.HttpsError('failed-precondition', 'Mercado Pago não configurado no servidor.');
    }
    if (!request.auth) {
        throw new functions.https.HttpsError('unauthenticated', 'Usuário não autenticado.');
    }
    const uid = request.auth.uid;

    const nomePlano = (request.data && request.data.plano) || PLANO_MOTORISTA_PADRAO;
    // Token da escolha "pagar por fora" na tela do Google (User Choice
    // Billing). Vai no metadata pro webhook reportar a transação ao Google.
    const externalTransactionToken = (request.data && request.data.externalTransactionToken) || null;
    const plano = PLANOS_MOTORISTA[nomePlano];
    if (!plano) {
        throw new functions.https.HttpsError('invalid-argument', 'Plano inválido.');
    }

    const userDoc = await admin.firestore().collection('usuarios').doc(uid).get();
    const userData = userDoc.exists ? userDoc.data() : {};
    const email = userData.email || `${uid}@caronasapp.com`;
    const nome = userData.nomeCompleto || 'Motorista';

    const expiraAtual = userData.acessoMotoristaExpiraEm;
    if (expiraAtual && typeof expiraAtual.toMillis === 'function') {
        const restanteMs = expiraAtual.toMillis() - Date.now();
        if (restanteMs > ACESSO_MOTORISTA_JANELA_RENOVACAO_DIAS * 24 * 60 * 60 * 1000) {
            throw new functions.https.HttpsError(
                'failed-precondition',
                'Seu acesso ainda está válido. Você poderá renovar a partir de ' +
                    new Date(expiraAtual.toMillis() - ACESSO_MOTORISTA_JANELA_RENOVACAO_DIAS * 24 * 60 * 60 * 1000).toLocaleDateString('pt-BR', { timeZone: 'America/Sao_Paulo' }) + '.'
            );
        }
    }

    try {
        const preference = {
            items: [{
                id: `acesso_motorista_${plano.dias}_dias`,
                title: plano.titulo,
                description: plano.descricao,
                quantity: 1,
                currency_id: 'BRL',
                unit_price: plano.valor,
            }],
            payer: { email, name: nome },
            // Mesmo formato do Match (usuarioId_timestamp) — só um
            // fallback pra identificar o pagador (ver paymentWebhookMotorista,
            // que prefere metadata.usuarioId, mais confiável quando o
            // próprio uid contém "_").
            external_reference: `${uid}_${Date.now()}`,
            back_urls: {
                success: 'caronasapp://payment_success',
                failure: 'caronasapp://payment_failure',
                pending: 'caronasapp://payment_pending',
            },
            auto_return: 'approved',
            notification_url: `https://us-central1-${process.env.GCLOUD_PROJECT}.cloudfunctions.net/paymentWebhookMotorista`,
            metadata: { usuarioId: uid, plano: nomePlano, externalTransactionToken },
            statement_descriptor: 'CARONAS APP',
        };

        console.log('📦 Criando preferência de acesso motorista para:', uid);
        const response = await mercadopago.preferences.create(preference);
        console.log('✅ Preferência criada:', response.body.id);

        return { preferenceId: response.body.id, initPoint: response.body.init_point };
    } catch (error) {
        console.error('❌ Erro ao criar preferência (motorista):', error);
        throw new functions.https.HttpsError('internal', 'Erro ao criar pagamento: ' + error.message);
    }
});

// Soma os dias do plano à data de expiração atual (se ainda válida) ou a partir de
// agora (se vencida/nunca pagou) — evita que pagar de novo ANTES de
// vencer "perca" os dias que ainda restavam (mesmo raciocínio de
// concederPremium no Match, extends em vez de sobrescrever).
// referencia: { mercadoPagoPaymentId } ou { googlePlayPurchaseToken, googlePlayOrderId }
// — vai inteira pro documento em pagamentosMotorista.
async function concederAcessoMotorista(usuarioId, referencia, nomePlano, valorPago) {
    const plano = PLANOS_MOTORISTA[nomePlano] || PLANOS_MOTORISTA[PLANO_MOTORISTA_PADRAO];
    const usuarioRef = admin.firestore().collection('usuarios').doc(usuarioId);
    const usuarioDoc = await usuarioRef.get();
    const usuarioData = usuarioDoc.exists ? usuarioDoc.data() : {};
    const atual = usuarioData.acessoMotoristaExpiraEm;
    const agora = Date.now();
    const baseMs = (atual && typeof atual.toMillis === 'function' && atual.toMillis() > agora) ? atual.toMillis() : agora;
    const novaExpiracao = new Date(baseMs + plano.dias * 24 * 60 * 60 * 1000);

    await usuarioRef.update({
        acessoMotoristaExpiraEm: admin.firestore.Timestamp.fromDate(novaExpiracao),
    });

    // Histórico de cobranças pra tela "Financeiro" do painel admin (ver
    // FinanceiroCaronasActivity/public/index.html) — usuarios.
    // acessoMotoristaExpiraEm só guarda a validade da ÚLTIMA compra, não dá
    // pra montar um relatório de receita em cima dele sozinho. Mesmo padrão
    // da coleção "pagamentos" do Match (concederPremium).
    await admin.firestore().collection('pagamentosMotorista').add({
        usuarioId,
        usuarioNome: usuarioData.nomeCompleto || '',
        usuarioEmail: usuarioData.email || '',
        // O que foi realmente cobrado (transaction_amount do Mercado Pago); cai no
        // preço da tabela só se o MP não informar.
        valor: typeof valorPago === 'number' ? valorPago : plano.valor,
        plano: nomePlano in PLANOS_MOTORISTA ? nomePlano : PLANO_MOTORISTA_PADRAO,
        dias: plano.dias,
        dataCompra: agora,
        expiraEm: novaExpiracao.getTime(),
        ...referencia,
    });

    console.log(`✅ Acesso motorista (${nomePlano}, ${plano.dias} dias) concedido a ${usuarioId} até ${novaExpiracao.toISOString()}`, referencia);
}

// Estorno (refunded), contestação no cartão (charged_back) ou cancelamento de um
// pagamento que JÁ tinha liberado acesso: tira da validade os dias que esse
// pagamento deu (campo "dias"; 30 nos pagamentos antigos, sem o campo) (pagamentos empilhados perdem só a parte deste) e marca o
// documento em pagamentosMotorista como estornado — o painel financeiro deixa de
// contar e "Meus pagamentos" mostra o estorno. Idempotente (o MP reenvia a
// notificação): a transação só age uma vez por pagamento. Sem documento em
// pagamentosMotorista = o pagamento nunca liberou nada, não há o que revogar.
async function revogarAcessoMotoristaPorEstorno(usuarioId, paymentId, statusMp) {
    const db = admin.firestore();
    const pagSnap = await db.collection('pagamentosMotorista')
        .where('mercadoPagoPaymentId', '==', String(paymentId)).limit(1).get();
    if (pagSnap.empty) {
        console.log('ℹ️ Pagamento', paymentId, 'sem acesso concedido — nada a revogar.');
        return;
    }
    const pagRef = pagSnap.docs[0].ref;
    const usuarioRef = db.collection('usuarios').doc(usuarioId);

    const revogou = await db.runTransaction(async (t) => {
        const [pag, usuario] = await Promise.all([t.get(pagRef), t.get(usuarioRef)]);
        if (pag.get('estornado') === true) return false;

        const diasDoPagamento = pag.get('dias') || ACESSO_MOTORISTA_DIAS_LEGADO;
        t.update(pagRef, { estornado: true, estornadoEm: Date.now(), statusMercadoPago: statusMp });

        const atual = usuario.exists ? usuario.get('acessoMotoristaExpiraEm') : null;
        if (atual && typeof atual.toMillis === 'function') {
            const novoMs = atual.toMillis() - diasDoPagamento * 24 * 60 * 60 * 1000;
            t.update(usuarioRef, {
                acessoMotoristaExpiraEm: novoMs > Date.now()
                    ? admin.firestore.Timestamp.fromMillis(novoMs)
                    : admin.firestore.FieldValue.delete(),
            });
        }
        return true;
    });

    console.log(revogou
        ? `↩️ Acesso motorista revogado por ${statusMp}: ${usuarioId} (pagamento ${paymentId})`
        : `⚠️ Pagamento ${paymentId} já estava estornado — notificação repetida ignorada.`);
}

exports.paymentWebhookMotorista = functions.https.onRequest(async (req, res) => {
    if (!MERCADOPAGO_ACCESS_TOKEN) {
        console.error('❌ Token não configurado.');
        res.status(500).send('Mercado Pago não configurado.');
        return;
    }
    if (req.method !== 'POST') {
        res.sendStatus(405);
        return;
    }

    try {
        const { id, topic } = req.query;
        if (topic !== 'payment' || !id) {
            res.sendStatus(200);
            return;
        }

        const paymentResponse = await mercadopago.payment.findById(id);
        const payment = paymentResponse.body;

        let usuarioId = null;
        // O Mercado Pago devolve as chaves do metadata em snake_case.
        const usuarioIdMetadata = payment.metadata && (payment.metadata.usuario_id || payment.metadata.usuarioId);
        if (usuarioIdMetadata) {
            usuarioId = usuarioIdMetadata;
        } else if (payment.external_reference) {
            usuarioId = payment.external_reference.split('_')[0];
        }
        if (!usuarioId) {
            console.warn('⚠️ Não foi possível identificar o motorista no pagamento', id);
            res.sendStatus(200);
            return;
        }

        if (payment.status === 'approved') {
            // Idempotência: cada payment.id do Mercado Pago só pode
            // conceder acesso UMA vez — o MP reenvia a mesma notificação
            // por retry, e esse endpoint é público. create() falha
            // atomicamente se o documento já existir (mesmo padrão do
            // paymentWebhook do Match).
            const idempotenciaRef = admin.firestore().collection('pagamentosMotoristaProcessados').doc(String(payment.id));
            try {
                await idempotenciaRef.create({ usuarioId, processadoEm: Date.now() });
            } catch (idempotenciaError) {
                if (idempotenciaError.code === 6) { // ALREADY_EXISTS
                    console.log('⚠️ Pagamento', payment.id, 'já processado — notificação repetida ignorada.');
                    res.sendStatus(200);
                    return;
                }
                throw idempotenciaError;
            }

            const planoPago = payment.metadata && payment.metadata.plano;
            await concederAcessoMotorista(usuarioId, { mercadoPagoPaymentId: String(payment.id) }, planoPago, payment.transaction_amount);

            // O Mercado Pago devolve as chaves do metadata em snake_case.
            const metadata = payment.metadata || {};
            const externalTransactionToken = metadata.external_transaction_token || metadata.externalTransactionToken;
            if (externalTransactionToken) {
                await reportarTransacaoExternaAoGooglePlay({
                    externalTransactionToken,
                    valor: payment.transaction_amount,
                    paymentId: payment.id,
                });
            } else {
                console.warn('⚠️ Pagamento', payment.id, 'sem externalTransactionToken — não reportado ao Google Play.');
            }
        } else if (['refunded', 'charged_back', 'cancelled'].includes(payment.status)) {
            await revogarAcessoMotoristaPorEstorno(usuarioId, payment.id, payment.status);
        }

        res.sendStatus(200);
    } catch (error) {
        console.error('❌ Erro no webhook de pagamento (motorista):', error);
        res.sendStatus(500);
    }
});

// Chamado pela tela de checkout no app (ver AssinaturaMotoristaActivity)
// enquanto espera a confirmação assíncrona do webhook — mesmo padrão de
// checkPaymentStatus no Match (polling curto depois de voltar do
// navegador de pagamento).
exports.checkPaymentStatusMotorista = functions.https.onCall(async (request) => {
    if (!request.auth) {
        throw new functions.https.HttpsError('unauthenticated', 'Usuário não autenticado.');
    }
    const usuarioDoc = await admin.firestore().collection('usuarios').doc(request.auth.uid).get();
    const dados = usuarioDoc.exists ? usuarioDoc.data() : {};
    const expiraEm = dados.acessoMotoristaExpiraEm;
    const valido = !!(expiraEm && typeof expiraEm.toMillis === 'function' && expiraEm.toMillis() > Date.now());
    return { acessoValido: valido, expiraEm: expiraEm ? expiraEm.toMillis() : null };
});

// Confirma uma compra feita pelo Google Play (o motorista escolheu essa
// opção na tela do Google): verifica o token, consome a compra
// (obrigatório em até 3 dias, senão o Google estorna) e concede o acesso
// pelo mesmo concederAcessoMotorista do Mercado Pago. Mesmo desenho de
// confirmarCompraGooglePlay no Match.
exports.confirmarCompraGooglePlayMotorista = functions.https.onCall(async (request) => {
    if (!request.auth) {
        throw new functions.https.HttpsError('unauthenticated', 'Usuário não autenticado.');
    }
    const usuarioId = request.auth.uid;
    const { purchaseToken, productId } = request.data || {};
    if (!purchaseToken || !productId) {
        throw new functions.https.HttpsError('invalid-argument', 'Dados incompletos.');
    }
    const nomePlano = PRODUTOS_GOOGLE_PLAY_MOTORISTA[productId];
    if (!nomePlano) {
        throw new functions.https.HttpsError('invalid-argument', 'Produto desconhecido: ' + productId);
    }
    const androidpublisher = obterAndroidPublisher();
    if (!androidpublisher) {
        throw new functions.https.HttpsError('failed-precondition', 'Integração com o Google Play não configurada no servidor.');
    }

    let compra;
    try {
        const resposta = await androidpublisher.purchases.products.get({
            packageName: GOOGLE_PLAY_PACKAGE_NAME,
            productId,
            token: purchaseToken,
        });
        compra = resposta.data;
    } catch (error) {
        console.error('❌ Erro ao verificar compra no Google Play:', error.message);
        throw new functions.https.HttpsError('internal', 'Não foi possível verificar a compra junto ao Google Play.');
    }

    // purchaseState: 0 = comprado, 1 = cancelado, 2 = pendente (Pix/boleto
    // ainda não confirmado) — pendente não é erro, o app tenta de novo.
    if (compra.purchaseState === 2) {
        return { success: false, pendente: true };
    }
    if (compra.purchaseState !== 0) {
        throw new functions.https.HttpsError('failed-precondition', 'Compra não concluída (purchaseState=' + compra.purchaseState + ').');
    }
    // A compra tem que ser desta conta: o app manda o uid como
    // obfuscatedAccountId ao abrir a compra (ver GooglePlayBillingManager).
    if (compra.obfuscatedExternalAccountId && compra.obfuscatedExternalAccountId !== usuarioId) {
        throw new functions.https.HttpsError('permission-denied', 'Esta compra pertence a outra conta.');
    }

    // Idempotência: o mesmo token só concede acesso uma vez. Trava só depois
    // de confirmar "comprado", senão uma compra pendente nunca mais liberaria.
    const idempotenciaRef = admin.firestore().collection('pagamentosMotoristaProcessados').doc(`gp_${purchaseToken}`);
    try {
        await idempotenciaRef.create({ usuarioId, processadoEm: Date.now() });
    } catch (idempotenciaError) {
        if (idempotenciaError.code === 6) { // ALREADY_EXISTS
            return { success: true, jaProcessado: true };
        }
        throw idempotenciaError;
    }

    // CONSUMIR (não só reconhecer): o passe é comprado de novo a cada
    // período, e um produto avulso só reconhecido fica "já comprado" pra
    // sempre no Google Play (ITEM_ALREADY_OWNED na renovação). Consumir
    // também conta como reconhecer (senão o Google estorna em 3 dias).
    if (compra.consumptionState !== 1) {
        try {
            await androidpublisher.purchases.products.consume({
                packageName: GOOGLE_PLAY_PACKAGE_NAME,
                productId,
                token: purchaseToken,
            });
        } catch (error) {
            console.error('❌ Erro ao consumir compra no Google Play (acesso concedido mesmo assim):', error.message);
        }
    }

    await concederAcessoMotorista(
        usuarioId,
        { googlePlayPurchaseToken: purchaseToken, googlePlayOrderId: compra.orderId || '' },
        nomePlano,
        PLANOS_MOTORISTA[nomePlano].valor
    );
    return { success: true };
});

// ============================================================
// Proxy autenticado para o Autocomplete da LocationIQ
// (https://locationiq.com) — mesmo motivo e mesmo padrão do
// googleMapsDirections/googleMapsGeocode no Match: a chave dessa API antes
// ficava embarcada no app (BuildConfig, lida de local.properties) — vai
// dentro do APK e qualquer um consegue extrair via engenharia reversa,
// usando-a por conta própria e gastando a cota gratuita (5.000/dia) do
// projeto. Agora o app chama esta Cloud Function (exige usuário
// autenticado) e a chave real só existe aqui no servidor (.env), nunca no
// cliente. Ver AutocompleteEnderecoUtil.kt/LocationIqRepository.kt no app.
// ============================================================
const LOCATIONIQ_API_KEY = process.env.LOCATIONIQ_API_KEY || '';

if (!LOCATIONIQ_API_KEY) {
    console.error('❌ LOCATIONIQ_API_KEY NÃO CONFIGURADA!');
    console.error('👉 Crie/edite o arquivo .env na pasta functions com:');
    console.error('   LOCATIONIQ_API_KEY=SUA_CHAVE_DA_LOCATIONIQ');
    console.error('   (cadastro gratuito, sem cartão, em https://locationiq.com/register)');
}

// Sigla de cada estado a partir do nome que a LocationIQ devolve (em português).
const UF_POR_ESTADO = {
    'Acre': 'AC', 'Alagoas': 'AL', 'Amapá': 'AP', 'Amazonas': 'AM', 'Bahia': 'BA', 'Ceará': 'CE',
    'Distrito Federal': 'DF', 'Espírito Santo': 'ES', 'Goiás': 'GO', 'Maranhão': 'MA', 'Mato Grosso': 'MT',
    'Mato Grosso do Sul': 'MS', 'Minas Gerais': 'MG', 'Pará': 'PA', 'Paraíba': 'PB', 'Paraná': 'PR',
    'Pernambuco': 'PE', 'Piauí': 'PI', 'Rio de Janeiro': 'RJ', 'Rio Grande do Norte': 'RN',
    'Rio Grande do Sul': 'RS', 'Rondônia': 'RO', 'Roraima': 'RR', 'Santa Catarina': 'SC',
    'São Paulo': 'SP', 'Sergipe': 'SE', 'Tocantins': 'TO',
};

// Campo de CIDADE: só municípios, no formato "Cachoeirinha - RS" pra quem escolhe
// (há uma em RS, PE e TO), mas o valor que vai pro campo é SÓ o nome
// ("Cachoeirinha") — a busca de caronas compara o texto da cidade por igualdade
// (normalizado, sem acento/caixa), então gravar "Viamão, Região Metropolitana de
// Porto Alegre, Rio Grande do Sul, Brasil" fazia quem digitasse só "Viamão" não
// achar a carona. Sem municípios na resposta, aceita cidade/vila/povoado.
function sugestoesDeCidade(itens) {
    const municipios = itens.filter((i) => i.class === 'place' && i.type === 'municipality');
    const base = municipios.length
        ? municipios
        : itens.filter((i) => i.class === 'place' && ['city', 'town', 'village'].includes(i.type));
    const vistos = new Set();
    const resultado = [];
    for (const item of base) {
        const a = item.address || {};
        const nome = a.name || a.city || a.town || a.village;
        if (!nome) continue;
        const uf = UF_POR_ESTADO[a.state];
        const texto = uf ? `${nome} - ${uf}` : nome;
        if (vistos.has(texto)) continue;
        vistos.add(texto);
        resultado.push({ texto, valor: nome });
        if (resultado.length >= 5) break;
    }
    return resultado;
}

// Campo de ENDEREÇO: rua (ou local), número quando houver, bairro e cidade — sem
// região metropolitana, CEP nem "Brasil". Rios, montanhas e limites administrativos
// não servem de endereço.
function sugestoesDeEndereco(itens) {
    const ignorar = new Set(['waterway', 'natural', 'boundary']);
    const vistos = new Set();
    const resultado = [];
    for (const item of itens) {
        if (ignorar.has(item.class)) continue;
        if (item.class === 'place' && ['municipality', 'city', 'town', 'village', 'state', 'region'].includes(item.type)) continue;
        const a = item.address || {};
        const via = a.road;
        const partes = [];
        if (a.name && a.name !== via) partes.push(a.name);
        if (via) partes.push(a.house_number ? `${via}, ${a.house_number}` : via);
        if (!partes.length) continue;
        const bairro = a.suburb || a.neighbourhood;
        if (bairro && !partes.includes(bairro)) partes.push(bairro);
        const cidade = a.city || a.town || a.village || a.municipality;
        if (cidade && !partes.includes(cidade)) partes.push(cidade);
        const uf = UF_POR_ESTADO[a.state];
        const texto = partes.join(', ') + (uf ? ` - ${uf}` : '');
        if (vistos.has(texto)) continue;
        vistos.add(texto);
        resultado.push({ texto, valor: texto });
        if (resultado.length >= 5) break;
    }
    return resultado;
}

// Parâmetros: consulta (texto digitado), tipo ('cidade' | 'endereco') e, no
// endereço, cidade (o que está no campo de cidade ao lado — vira parte da busca,
// pra achar a rua da cidade certa). Sem "tipo" (app antigo) segue no formato de
// antes — frases longas —, só que agora restrito ao Brasil. A API ignora acento
// ("viamao" acha Viamão).
exports.autocompletarEndereco = functions.https.onCall(async (request) => {
    if (!request.auth) {
        throw new functions.https.HttpsError('unauthenticated', 'Usuário não autenticado.');
    }
    if (!LOCATIONIQ_API_KEY) {
        throw new functions.https.HttpsError('failed-precondition', 'Autocomplete de endereço não configurado no servidor.');
    }

    const consulta = (request.data && request.data.consulta || '').trim();
    if (!consulta) {
        throw new functions.https.HttpsError('invalid-argument', 'consulta é obrigatória.');
    }
    const tipo = request.data && request.data.tipo;
    const cidadeContexto = (request.data && request.data.cidade || '').trim();
    const busca = tipo === 'endereco' && cidadeContexto ? `${consulta} ${cidadeContexto}` : consulta;

    try {
        // O parâmetro é "countrycodes" (minúsculo): com "countryCodes" a API ignorava
        // o filtro e devolvia cidades da França, Itália, Argentina...
        const url =
            'https://api.locationiq.com/v1/autocomplete' +
            `?key=${LOCATIONIQ_API_KEY}` +
            `&q=${encodeURIComponent(busca)}` +
            `&countrycodes=br&accept-language=pt&normalizecity=1&limit=${tipo ? 10 : 5}`;
        const resposta = await fetch(url);

        // A própria API devolve 404 quando não acha nada — não é erro de
        // verdade, só "sem sugestão ainda" (comum com poucos caracteres).
        if (resposta.status === 404) {
            return { sugestoes: [], itens: [] };
        }
        if (!resposta.ok) {
            console.error(`❌ LocationIQ respondeu ${resposta.status}`);
            return { sugestoes: [], itens: [] };
        }

        const dados = await resposta.json();
        const lista = Array.isArray(dados) ? dados : [];

        if (tipo === 'cidade' || tipo === 'endereco') {
            const itens = tipo === 'cidade' ? sugestoesDeCidade(lista) : sugestoesDeEndereco(lista);
            return { sugestoes: itens.map((i) => i.texto), itens };
        }

        // Formato antigo (app sem "tipo").
        const sugestoes = lista.map((item) => item.display_name).filter(Boolean).slice(0, 5);
        return { sugestoes };
    } catch (error) {
        console.error('❌ Erro ao consultar LocationIQ:', error);
        // Autocomplete é só uma ajuda visual — nunca deveria travar o
        // formulário do app, só deixar de sugerir.
        return { sugestoes: [], itens: [] };
    }
});

// ============================================================
// Cria uma conta de administrador nova (Firebase Auth + admins/{uid}) —
// chamada pela tela "Cadastrar admin" na flavor admin (ver
// CadastroAdminCaronasActivity.kt/AdminRepository.kt).
//
// Sempre via Cloud Function, nunca um "create" direto do cliente em
// admins/{uid} (ver firestore.rules: "allow create: if false") — sem essa
// trava, qualquer conta autenticada poderia se autopromover a admin
// escrevendo o próprio uid ali. Protegida por um segredo fixo, só existente
// aqui no .env (ADMIN_MASTER_PASSWORD) — mais a permissão "administradores"
// de quem está chamando, quando já existe pelo menos um admin (ver bootstrap
// logo abaixo).
//
// Cria a conta com email+senha informados no formulário (reaproveitando uma
// conta já existente com esse e-mail, se houver — mesmo padrão do
// cadastrarAdminAutorizado do Match) e grava nome/sobrenome/email/
// telefone/role/permissoes em admins/{uid}. A FOTO não entra aqui — o app
// faz login logo em seguida com o uid/senha recém-criados e sobe a foto
// diretamente pro Storage (ver AdminRepository.atualizarFotoAdmin),
// evitando ter que mandar um arquivo binário dentro do payload da
// function.
// ============================================================
const ADMIN_MASTER_PASSWORD = process.env.ADMIN_MASTER_PASSWORD || '';

if (!ADMIN_MASTER_PASSWORD) {
    console.error('❌ ADMIN_MASTER_PASSWORD NÃO CONFIGURADA!');
    console.error('👉 Defina ADMIN_MASTER_PASSWORD no arquivo .env da pasta functions.');
}

// ============================================================
// Permissões granulares por admin — cada chave gate uma seção do painel
// (ver ConfiguracoesCaronasActivity/AdministracaoCaronasActivity no app).
// Um admin "legado" (criado antes deste sistema existir, sem o campo
// permissoes) continua com acesso total — sem isso, todo admin já
// cadastrado perderia acesso ao publicar esta mudança. Isso só controla
// alcance/visibilidade das ações — a gestão de OUTROS admins continua
// exigindo a senha master além da permissão "administradores" (decisão
// explícita: a permissão decide quem VÊ a tela, a senha master continua
// sendo a trava final no servidor).
// ============================================================
const CHAVES_PERMISSOES = [
    'usuarios', 'viagens', 'financeiro', 'mensagens', 'manifestacoes',
    'relatorios', 'administradores', 'chatAdmin',
];

// Admin master de verdade, identificado por CPF fixo — mesmo CPF já usado
// como master no Match (CPF_ADMIN_MASTER, functions/index.js de lá). Tem
// permissão total e irrestrita sempre, independente do mapa permissoes.
const CPF_ADMIN_MASTER = '56413025034';

function ehMaster(dadosAdmin) {
    return !!dadosAdmin && dadosAdmin.cpf === CPF_ADMIN_MASTER;
}

function sanitizarPermissoes(permissoes) {
    if (!permissoes || typeof permissoes !== 'object') return null;
    const limpo = {};
    for (const chave of CHAVES_PERMISSOES) {
        limpo[chave] = permissoes[chave] === true;
    }
    return limpo;
}

function temPermissao(dadosAdmin, chave) {
    if (ehMaster(dadosAdmin)) return true;
    if (!dadosAdmin || !dadosAdmin.permissoes) return true; // legado = acesso total
    return dadosAdmin.permissoes[chave] === true;
}

async function exigirPermissao(uid, chave) {
    const doc = await admin.firestore().collection('admins').doc(uid).get();
    if (!doc.exists) {
        throw new functions.https.HttpsError('permission-denied', 'Acesso restrito a administradores.');
    }
    if (!temPermissao(doc.data(), chave)) {
        throw new functions.https.HttpsError('permission-denied', 'Você não tem permissão para esta ação.');
    }
    return doc.data();
}

// Confirma a senha da PRÓPRIA conta de quem está chamando — diferente de
// verificarSenhaAdminMaster-style checks (que comparam com um segredo
// fixo): aqui o e-mail vem de quem está autenticado. Mesmo endpoint REST
// (Identity Toolkit) que reenviarVerificacaoEmail já usa mais abaixo neste
// arquivo — WEB_API_KEY é declarada lá, mas como esta função só é chamada
// em resposta a uma requisição real (nunca durante o carregamento do
// módulo), o módulo inteiro já terminou de carregar e a constante já
// existe nesse momento.
async function verificarSenhaAdminPropria(email, senha) {
    if (!WEB_API_KEY || !email || !senha) return false;
    try {
        const resposta = await fetch(
            `https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${WEB_API_KEY}`,
            {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ email, password: senha, returnSecureToken: false }),
            }
        );
        return resposta.ok;
    } catch (e) {
        return false;
    }
}

// Manda o e-mail de verificação (VERIFY_EMAIL) pra um uid qualquer sem
// precisar logar como essa pessoa no cliente — mesmo truque de
// reenviarVerificacaoEmail (customToken -> ID token real via REST ->
// sendOobCode), extraído pra cá pra ser reaproveitado também por
// cadastrarAdmin: quando quem está cadastrando já tem uma sessão própria
// aberta, o cliente não pode logar temporariamente como a conta nova pra
// mandar esse e-mail (derrubaria a sessão de quem está cadastrando — ver
// CadastroAdminCaronasActivity.salvarCadastroNovo), então precisa ser feito
// aqui, via Admin SDK, sem afetar sessão nenhuma. Nunca lança — falha de
// envio não pode derrubar o cadastro em si, só fica no log.
async function enviarEmailVerificacaoParaUid(uid) {
    if (!WEB_API_KEY) return false;
    try {
        const customToken = await admin.auth().createCustomToken(uid);

        const respostaLogin = await fetch(
            `https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=${WEB_API_KEY}`,
            {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ token: customToken, returnSecureToken: true }),
            }
        );
        if (!respostaLogin.ok) {
            console.error('❌ signInWithCustomToken falhou:', respostaLogin.status, await respostaLogin.text());
            return false;
        }
        const { idToken } = await respostaLogin.json();

        const respostaEnvio = await fetch(
            `https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=${WEB_API_KEY}`,
            {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ requestType: 'VERIFY_EMAIL', idToken }),
            }
        );
        if (!respostaEnvio.ok) {
            console.error('❌ sendOobCode falhou:', respostaEnvio.status, await respostaEnvio.text());
            return false;
        }
        return true;
    } catch (error) {
        console.error('❌ Erro ao enviar e-mail de verificação:', error);
        return false;
    }
}

// Autoriza uma ação sensível sobre um USUÁRIO comum (editar/excluir) por
// QUALQUER uma das duas vias: a senha master fixa (funciona pra quem a
// souber, sem precisar de permissão nenhuma — como sempre foi, mantido sem
// mudança), OU a própria senha do admin logado, desde que ele tenha a
// permissão indicada (novo, ver excluirUsuario/admAtualizarUsuario).
// Devolve qual via foi usada, pra registrar no log.
async function autorizarComSenhaMasterOuPropria(request, senhaAutorizacao, chavePermissao) {
    if (senhaAutorizacao && senhaAutorizacao === ADMIN_MASTER_PASSWORD) {
        return 'senhaMaster';
    }
    const uidChamador = request.auth && request.auth.uid;
    if (uidChamador) {
        const dadosAdminSnap = await admin.firestore().collection('admins').doc(uidChamador).get();
        if (dadosAdminSnap.exists && temPermissao(dadosAdminSnap.data(), chavePermissao)) {
            const emailChamador = dadosAdminSnap.data().email;
            if (await verificarSenhaAdminPropria(emailChamador, senhaAutorizacao)) {
                return 'senhaPropria';
            }
        }
    }
    throw new functions.https.HttpsError('permission-denied', 'Senha incorreta ou permissão insuficiente.');
}

// Registro append-only de ações administrativas sensíveis (criar/editar/
// excluir admin, editar/excluir usuário) — mostrado na seção
// "Administração" de Relatórios (ver RelatoriosCaronasActivity).
// "autorizadoPor" diferencia se a ação foi liberada pela senha master ou
// pela própria senha do admin que a executou. Nunca lança erro pra fora —
// falhar ao registrar o log não pode derrubar a ação em si.
async function registrarLogAdministracao(uidExecutor, tipo, alvoNome, alvoId, autorizadoPor) {
    try {
        const execDoc = await admin.firestore().collection('admins').doc(uidExecutor).get();
        const exec = execDoc.exists ? execDoc.data() : {};
        await admin.firestore().collection('logsAdministracao').add({
            tipo,
            alvoNome: alvoNome || '-',
            alvoId: alvoId || null,
            executadoPorAdminId: uidExecutor,
            executadoPorNome: [exec.nome, exec.sobrenome].filter(Boolean).join(' ') || exec.email || '-',
            executadoPorCpf: exec.cpf || '-',
            autorizadoPor,
            criadoEm: admin.firestore.FieldValue.serverTimestamp(),
        });
    } catch (e) {
        console.warn('⚠️ Falha ao registrar log de administração:', e.message);
    }
}

exports.cadastrarAdmin = functions.https.onCall(async (request) => {
    if (!ADMIN_MASTER_PASSWORD) {
        throw new functions.https.HttpsError('failed-precondition', 'Cadastro de admin não configurado no servidor.');
    }

    const dados = request.data || {};
    const nome = (dados.nome || '').trim();
    const sobrenome = (dados.sobrenome || '').trim();
    const email = (dados.email || '').trim().toLowerCase();
    const telefone = (dados.telefone || '').trim();
    const cpf = (dados.cpf || '').replace(/\D/g, '');
    const senha = dados.senha || '';
    const senhaAutorizacao = dados.senhaAutorizacao || '';
    const role = dados.role === 'colaborador' ? 'colaborador' : 'admin';
    const permissoes = sanitizarPermissoes(dados.permissoes);

    if (!nome || !sobrenome || !email || !telefone || !cpf || !senha) {
        throw new functions.https.HttpsError('invalid-argument', 'Preencha todos os campos.');
    }
    if (senhaAutorizacao !== ADMIN_MASTER_PASSWORD) {
        throw new functions.https.HttpsError('permission-denied', 'Senha do administrador master incorreta.');
    }

    // Bootstrap: sem NENHUM admin ainda, este é o cadastro público da tela
    // de login (sem sessão) — continua liberado só pela senha master, como
    // sempre foi. A partir do segundo admin em diante, só quem já está
    // logado E tem a permissão "administradores" pode criar outro.
    const totalAdmins = (await admin.firestore().collection('admins').limit(1).get()).size;
    if (totalAdmins > 0) {
        const uidChamador = request.auth && request.auth.uid;
        if (!uidChamador) {
            throw new functions.https.HttpsError('unauthenticated', 'Faça login como administrador para cadastrar outro admin.');
        }
        await exigirPermissao(uidChamador, 'administradores');
    }

    let uid;
    try {
        const novoAdmin = await admin.auth().createUser({
            email,
            password: senha,
            displayName: `${nome} ${sobrenome}`,
        });
        uid = novoAdmin.uid;
    } catch (error) {
        if (error.code === 'auth/email-already-exists') {
            // Já existe uma conta (comum ou admin) com esse e-mail — reaproveita
            // o uid, mas TAMBÉM aplica a senha digitada aqui. Sem isso, quem
            // cadastra vê um campo de senha, acha que é a senha de login da
            // conta nova, mas ela era silenciosamente ignorada — a conta
            // continuava com a senha antiga (de quando virou usuário comum,
            // por exemplo), e o login com a senha "certa" (a que apareceu
            // nesta tela) falhava com "supplied auth credentials" (bug real,
            // reproduzido cadastrando colaborador num e-mail já existente).
            const existente = await admin.auth().getUserByEmail(email);
            uid = existente.uid;
            await admin.auth().updateUser(uid, { password: senha, displayName: `${nome} ${sobrenome}` });
        } else {
            console.error('❌ Erro ao criar conta de admin:', error.code || error.message, error);
            throw new functions.https.HttpsError('internal', 'Erro ao criar a conta: ' + (error.message || error.code));
        }
    }

    await admin.firestore().collection('admins').doc(uid).set({
        nome,
        sobrenome,
        email,
        telefone,
        cpf,
        role,
        permissoes,
        criadoEm: admin.firestore.FieldValue.serverTimestamp(),
    }, { merge: true });

    if (request.auth && request.auth.uid) {
        await registrarLogAdministracao(request.auth.uid, 'admin_criado', `${nome} ${sobrenome}`, uid, 'senhaMaster');
    }

    // O cliente só consegue mandar esse e-mail sozinho (via
    // finalizarCadastroAdmin, logando temporariamente como a conta nova)
    // quando ninguém mais estava logado (bootstrap) — quando quem cadastra
    // já tem sessão própria (o caso normal, admin/colaborador cadastrado
    // pelo painel), o cliente pula esse passo de propósito pra não derrubar
    // essa sessão. Sem isto aqui, a conta ficava com e-mail nunca verificado
    // e travada pra sempre no login (ver AdminRepository.loginAdmin,
    // isEmailVerified). Checa emailVerified em vez de "é conta nova?" pra
    // cobrir também uma conta REAPROVEITADA (auth/email-already-exists
    // acima) que por acaso nunca tinha verificado o e-mail.
    const contaAuth = await admin.auth().getUser(uid);
    if (!contaAuth.emailVerified) {
        await enviarEmailVerificacaoParaUid(uid);
    }

    return { uid };
});

// ============================================================
// Edita um admin JÁ existente (nome/sobrenome/telefone/cpf) — usada pela
// tela de busca por CPF (ver BuscarAdminActivity/CadastroAdminCaronasActivity
// em modo edição). Trava só por senha do administrador master (sem CPF —
// só a senha mesmo, por pedido do usuário) — sem isso, qualquer admin
// logado poderia editar o perfil de QUALQUER outro admin só por saber o
// uid (a leitura de admins/{uid} já é liberada pra qualquer admin, ver
// firestore.rules "allow read: if ehAdmin()" — a edição precisa de uma
// trava adicional, essa function). Não mexe em e-mail nem senha de login
// (mudar e-mail de uma conta Auth já existente tem mais implicações — fora
// do escopo pedido aqui) nem em foto (a própria pessoa troca a própria
// foto direto pelo app, ver AdminRepository.atualizarFotoAdmin).
// ============================================================
exports.atualizarAdmin = functions.https.onCall(async (request) => {
    if (!ADMIN_MASTER_PASSWORD) {
        throw new functions.https.HttpsError('failed-precondition', 'Edição de admin não configurada no servidor.');
    }

    const dados = request.data || {};
    const uid = (dados.uid || '').trim();
    const nome = (dados.nome || '').trim();
    const sobrenome = (dados.sobrenome || '').trim();
    const telefone = (dados.telefone || '').trim();
    const cpf = (dados.cpf || '').replace(/\D/g, '');
    const senhaAutorizacao = dados.senhaAutorizacao || '';

    if (!uid || !nome || !sobrenome || !telefone || !cpf) {
        throw new functions.https.HttpsError('invalid-argument', 'Preencha todos os campos.');
    }
    const uidChamador = request.auth && request.auth.uid;
    if (!uidChamador) {
        throw new functions.https.HttpsError('unauthenticated', 'Faça login como administrador.');
    }
    await exigirPermissao(uidChamador, 'administradores');
    if (senhaAutorizacao !== ADMIN_MASTER_PASSWORD) {
        throw new functions.https.HttpsError('permission-denied', 'Senha do administrador master incorreta.');
    }

    const ref = admin.firestore().collection('admins').doc(uid);
    const doc = await ref.get();
    if (!doc.exists) {
        throw new functions.https.HttpsError('not-found', 'Admin não encontrado.');
    }

    await ref.update({ nome, sobrenome, telefone, cpf });
    await registrarLogAdministracao(uidChamador, 'admin_editado', `${nome} ${sobrenome}`, uid, 'senhaMaster');
    return { ok: true };
});

// ============================================================
// Edita só role+permissoes de um admin já existente (separado dos dados
// básicos acima, mesmo padrão do Match — admAtualizarPermissoesAdmin).
// Mesma trava dupla (permissão "administradores" de quem chama + senha
// master).
// ============================================================
exports.atualizarPermissoesAdmin = functions.https.onCall(async (request) => {
    if (!ADMIN_MASTER_PASSWORD) {
        throw new functions.https.HttpsError('failed-precondition', 'Edição de permissões não configurada no servidor.');
    }

    const dados = request.data || {};
    const uid = (dados.uid || '').trim();
    const role = dados.role === 'colaborador' ? 'colaborador' : 'admin';
    const permissoes = sanitizarPermissoes(dados.permissoes);
    const senhaAutorizacao = dados.senhaAutorizacao || '';

    if (!uid) {
        throw new functions.https.HttpsError('invalid-argument', 'uid é obrigatório.');
    }
    const uidChamador = request.auth && request.auth.uid;
    if (!uidChamador) {
        throw new functions.https.HttpsError('unauthenticated', 'Faça login como administrador.');
    }
    await exigirPermissao(uidChamador, 'administradores');
    if (senhaAutorizacao !== ADMIN_MASTER_PASSWORD) {
        throw new functions.https.HttpsError('permission-denied', 'Senha do administrador master incorreta.');
    }

    const ref = admin.firestore().collection('admins').doc(uid);
    const doc = await ref.get();
    if (!doc.exists) {
        throw new functions.https.HttpsError('not-found', 'Admin não encontrado.');
    }

    await ref.update({ role, permissoes });
    const alvo = doc.data();
    await registrarLogAdministracao(uidChamador, 'admin_editado', [alvo.nome, alvo.sobrenome].filter(Boolean).join(' '), uid, 'senhaMaster');
    return { ok: true };
});

// ============================================================
// Troca a foto de OUTRO admin/colaborador (não o próprio dono) — o
// storage.rules (fotos_perfil/{usuarioId}) só libera escrita pro dono do
// uid, então o cliente não consegue subir a foto de outra pessoa direto no
// Storage. Contorno: o cliente sobe a foto numa pasta que ELE pode escrever
// (fotos_perfil/{uidChamador}/...), essa function (Admin SDK, ignora as
// regras) copia esse arquivo pra fotos_perfil/{uid}/..., apaga a cópia de
// origem, e grava a URL de download em admins/{uid}.fotoUrl — mesmo formato
// de URL (?alt=media&token=) que o Client SDK gera sozinho ao fazer
// upload, então funciona igual pra quem já lê fotoUrl hoje. Mesma trava
// dupla de atualizarAdmin/atualizarPermissoesAdmin (permissão
// "administradores" de quem chama + senha master).
// ============================================================
exports.atualizarFotoAdminAutorizado = functions.https.onCall(async (request) => {
    if (!ADMIN_MASTER_PASSWORD) {
        throw new functions.https.HttpsError('failed-precondition', 'Edição de foto não configurada no servidor.');
    }

    const dados = request.data || {};
    const uid = (dados.uid || '').trim();
    const storagePathOrigem = (dados.storagePathOrigem || '').trim();
    const senhaAutorizacao = dados.senhaAutorizacao || '';

    if (!uid || !storagePathOrigem) {
        throw new functions.https.HttpsError('invalid-argument', 'uid e storagePathOrigem são obrigatórios.');
    }
    const uidChamador = request.auth && request.auth.uid;
    if (!uidChamador) {
        throw new functions.https.HttpsError('unauthenticated', 'Faça login como administrador.');
    }
    // A origem TEM que estar na própria pasta de quem chamou — é a mesma
    // pasta que o storage.rules já libera pro cliente escrever, então isso
    // garante que a function só mexe num arquivo que quem chamou realmente
    // acabou de subir, nunca num path arbitrário de outra pessoa.
    if (!storagePathOrigem.startsWith(`fotos_perfil/${uidChamador}/`)) {
        throw new functions.https.HttpsError('permission-denied', 'Origem da foto inválida.');
    }
    await exigirPermissao(uidChamador, 'administradores');
    if (senhaAutorizacao !== ADMIN_MASTER_PASSWORD) {
        throw new functions.https.HttpsError('permission-denied', 'Senha do administrador master incorreta.');
    }

    const ref = admin.firestore().collection('admins').doc(uid);
    const doc = await ref.get();
    if (!doc.exists) {
        throw new functions.https.HttpsError('not-found', 'Admin não encontrado.');
    }

    const bucket = admin.storage().bucket();
    const arquivoOrigem = bucket.file(storagePathOrigem);
    const [existeOrigem] = await arquivoOrigem.exists();
    if (!existeOrigem) {
        throw new functions.https.HttpsError('not-found', 'Foto enviada não encontrada — tente selecionar de novo.');
    }

    const caminhoDestino = `fotos_perfil/${uid}/perfil_${Date.now()}.jpg`;
    const arquivoDestino = bucket.file(caminhoDestino);
    const token = crypto.randomUUID();

    try {
        await arquivoOrigem.copy(arquivoDestino);
        await arquivoDestino.setMetadata({ contentType: 'image/jpeg', metadata: { firebaseStorageDownloadTokens: token } });
        await arquivoOrigem.delete().catch((e) => console.warn('⚠️ Erro ao apagar foto de origem temporária:', e.message));
    } catch (error) {
        console.error('❌ Erro ao copiar foto no Storage:', error);
        throw new functions.https.HttpsError('internal', 'Erro ao salvar a foto: ' + error.message);
    }

    const url = `https://firebasestorage.googleapis.com/v0/b/${bucket.name}/o/${encodeURIComponent(caminhoDestino)}?alt=media&token=${token}`;
    await ref.update({ fotoUrl: url });

    const alvo = doc.data();
    await registrarLogAdministracao(uidChamador, 'admin_editado', [alvo.nome, alvo.sobrenome].filter(Boolean).join(' '), uid, 'senhaMaster');
    return { fotoUrl: url };
});

// ============================================================
// Exclui o CADASTRO de admin de alguém (revoga acesso ao painel), sem
// apagar a conta Firebase Auth nem o cadastro de usuário comum dessa
// pessoa — "excluir" aqui é só remover admins/{uid}, não a conta inteira.
// Mesma trava só-por-senha do administrador master.
// ============================================================
exports.excluirAdmin = functions.https.onCall(async (request) => {
    if (!ADMIN_MASTER_PASSWORD) {
        throw new functions.https.HttpsError('failed-precondition', 'Exclusão de admin não configurada no servidor.');
    }

    const dados = request.data || {};
    const uid = (dados.uid || '').trim();
    const senhaAutorizacao = dados.senhaAutorizacao || '';

    if (!uid) {
        throw new functions.https.HttpsError('invalid-argument', 'uid é obrigatório.');
    }
    const uidChamador = request.auth && request.auth.uid;
    if (!uidChamador) {
        throw new functions.https.HttpsError('unauthenticated', 'Faça login como administrador.');
    }
    await exigirPermissao(uidChamador, 'administradores');
    if (senhaAutorizacao !== ADMIN_MASTER_PASSWORD) {
        throw new functions.https.HttpsError('permission-denied', 'Senha do administrador master incorreta.');
    }

    const ref = admin.firestore().collection('admins').doc(uid);
    const doc = await ref.get();
    if (!doc.exists) {
        throw new functions.https.HttpsError('not-found', 'Admin não encontrado.');
    }

    const alvo = doc.data();
    await ref.delete();
    await registrarLogAdministracao(uidChamador, 'admin_excluido', [alvo.nome, alvo.sobrenome].filter(Boolean).join(' '), uid, 'senhaMaster');
    return { ok: true };
});

// ============================================================
// Remove um usuário comum (motorista ou passageiro) POR COMPLETO — toque
// e segure na lista de Passageiros do painel admin (nativo ou web).
// Precisa de Admin SDK (apaga a conta Firebase Auth de OUTRA pessoa, o
// cliente só consegue apagar a PRÓPRIA conta) e apaga todo rastro desse
// uid em qualquer coleção, pra não deixar nada travando um recadastro com
// o mesmo e-mail. Mesma trava só-por-senha do administrador master das
// outras ações destrutivas do painel.
//
// Passageiro não tem campo de unicidade além do e-mail (Auth); motorista
// também tem o vínculo CPF/telefone/nome (ver vincularIdentidadeMotorista),
// apagado abaixo. O que mais "trava" um recadastro é a conta Firebase Auth
// antiga ainda existir com o mesmo e-mail; por isso ela é sempre apagada
// por último, depois de tudo mais já ter sido limpo.
// ============================================================
// Exclusão completa de uma conta (perfil, viagens, conversas, avaliações,
// fotos, vínculo de motorista e a conta de autenticação) — corpo
// compartilhado por excluirUsuario (admin, com a senha master) e
// excluirContaPropria (o próprio usuário, ver mais abaixo). Antes deste
// refactor, a exclusão pelo próprio usuário era feita direto pelo cliente
// (ver UsuarioRepository.excluirContaPropria) e não limpava viagens/
// conversas/avaliações/vínculo — só o doc de perfil e as fotos —, deixando
// esses dados órfãos e, pior, mantendo a trava motoristasUnicos "presa" a um
// uid cujo usuarios/{uid} some quando o passo final abaixo apaga o
// documento, então funcionava por acidente (o cadastro novo já achava a
// trava "abandonada"), mas todo o resto ficava para trás. Unificar os dois
// caminhos aqui corrige os dois problemas de uma vez.
async function excluirUsuarioCompleto(db, uid) {
    async function apagarDocsDaQuery(query) {
        const snap = await query.get();
        if (snap.empty) return;
        const batch = db.batch();
        snap.docs.forEach((doc) => batch.delete(doc.ref));
        await batch.commit();
    }

    // Solicitações — como passageiro (quem pediu) ou como motorista (quem
    // recebeu o pedido, se essa conta também ofereceu carona alguma vez).
    await apagarDocsDaQuery(db.collection('solicitacoes').where('passageiroId', '==', uid));
    await apagarDocsDaQuery(db.collection('solicitacoes').where('motoristaId', '==', uid));

    // Ocupação de vaga em QUALQUER carona (vive como subcoleção de cada
    // carona — collectionGroup alcança todas de uma vez, mesmo as de
    // motoristas diferentes).
    await apagarDocsDaQuery(db.collectionGroup('ocupacao').where('passageiroId', '==', uid));

    // Caronas oferecidas (se essa conta já foi motorista) — precisa apagar
    // a subcoleção ocupacao de CADA uma antes do doc pai, já que o
    // Firestore não apaga subcoleção sozinho ao apagar o documento pai.
    const caronasSnap = await db.collection('caronas').where('motoristaId', '==', uid).get();
    for (const caronaDoc of caronasSnap.docs) {
        const ocupacaoSnap = await caronaDoc.ref.collection('ocupacao').get();
        if (!ocupacaoSnap.empty) {
            const batchOcupacao = db.batch();
            ocupacaoSnap.docs.forEach((o) => batchOcupacao.delete(o.ref));
            await batchOcupacao.commit();
        }
        await caronaDoc.ref.delete();
    }

    // Conversas (como motorista ou passageiro) + a subcoleção de mensagens
    // de cada uma, mesmo motivo de ocupacao acima.
    const conversasMotorista = await db.collection('conversas').where('motoristaId', '==', uid).get();
    const conversasPassageiro = await db.collection('conversas').where('passageiroId', '==', uid).get();
    for (const conversaDoc of [...conversasMotorista.docs, ...conversasPassageiro.docs]) {
        const mensagensSnap = await conversaDoc.ref.collection('mensagens').get();
        if (!mensagensSnap.empty) {
            const batchMensagens = db.batch();
            mensagensSnap.docs.forEach((m) => batchMensagens.delete(m.ref));
            await batchMensagens.commit();
        }
        await conversaDoc.ref.delete();
    }

    // Avaliações feitas ou recebidas por essa conta.
    await apagarDocsDaQuery(db.collection('avaliacoes').where('avaliadorId', '==', uid));
    await apagarDocsDaQuery(db.collection('avaliacoes').where('avaliadoId', '==', uid));

    // Bloqueios feitos por ou contra essa conta (ver firestore.rules /bloqueios).
    await apagarDocsDaQuery(db.collection('bloqueios').where('bloqueadorId', '==', uid));
    await apagarDocsDaQuery(db.collection('bloqueios').where('bloqueadoId', '==', uid));

    // Se por acaso essa conta também tinha sido promovida a admin.
    await db.collection('admins').doc(uid).delete().catch(() => {});

    // Fotos de perfil no Storage — best-effort: a conta de serviço das
    // Cloud Functions pode não ter permissão de Storage configurada (já
    // vimos nesta mesma sessão faltar IAM de FCM), então isso NUNCA deve
    // travar a exclusão em si, só avisar no log se falhar.
    try {
        const bucket = admin.storage().bucket();
        await bucket.deleteFiles({ prefix: `fotos_perfil/${uid}/` });
    } catch (e) {
        console.warn('⚠️ Erro ao apagar fotos do Storage:', e.message);
    }

    // Antes de apagar o vínculo: preserva por CPF quantas caronas grátis já
    // foram usadas e quanto de acesso pago ainda resta (ver
    // preservarCreditoMotoristaPorCpf) — sem isso, excluir e recadastrar com
    // outro e-mail zerava as 10 caronas grátis e descartava dias de acesso
    // pago ainda válidos, já que esses dois campos vivem em usuarios/{uid},
    // que está prestes a sumir.
    await preservarCreditoMotoristaPorCpf(db, uid);

    // Vínculo de identidade de motorista (CPF etc.) e as travas de
    // unicidade dele — libera CPF/telefone/e-mail pra um recadastro.
    await apagarDocsDaQuery(db.collection('motoristasUnicos').where('uid', '==', uid));
    await db.collection('motoristasVinculo').doc(uid).delete();

    // Documento de perfil.
    await db.collection('usuarios').doc(uid).delete();

    // Por último, a conta de autenticação — é o único que de fato "trava"
    // um recadastro com o mesmo e-mail (ver comentário no topo da
    // função). Se já não existir (ex.: uma exclusão parcial anterior),
    // não é erro — o objetivo já foi alcançado.
    try {
        await admin.auth().deleteUser(uid);
    } catch (e) {
        if (e.code !== 'auth/user-not-found') throw e;
    }
}

exports.excluirUsuario = functions.https.onCall(async (request) => {
    if (!ADMIN_MASTER_PASSWORD) {
        throw new functions.https.HttpsError('failed-precondition', 'Exclusão de usuário não configurada no servidor.');
    }

    const dados = request.data || {};
    const uid = (dados.uid || '').trim();
    const senhaAutorizacao = dados.senhaAutorizacao || '';

    if (!uid) {
        throw new functions.https.HttpsError('invalid-argument', 'uid é obrigatório.');
    }
    const autorizadoPor = await autorizarComSenhaMasterOuPropria(request, senhaAutorizacao, 'usuarios');

    const alvoSnap = await admin.firestore().collection('usuarios').doc(uid).get();
    const nomeAlvo = alvoSnap.exists ? (alvoSnap.data().nomeCompleto || alvoSnap.data().email || '-') : '-';

    await excluirUsuarioCompleto(admin.firestore(), uid);
    if (request.auth && request.auth.uid) {
        await registrarLogAdministracao(request.auth.uid, 'usuario_excluido', nomeAlvo, uid, autorizadoPor);
    }
    return { ok: true };
});

// Exclusão da PRÓPRIA conta — mesma limpeza completa de excluirUsuario,
// sem senha de administrador master: quem prova a identidade aqui é o
// próprio token de autenticação de quem chama (ver UsuarioRepository
// .excluirContaPropria, que reautentica com a senha atual ANTES de chamar
// esta function — barreira extra contra um celular desbloqueado na mão de
// outra pessoa, embora o token sozinho já bastasse pro servidor confiar).
exports.excluirContaPropria = functions.https.onCall(async (request) => {
    const uid = request.auth && request.auth.uid;
    if (!uid) {
        throw new functions.https.HttpsError('unauthenticated', 'Usuário não autenticado.');
    }
    await excluirUsuarioCompleto(admin.firestore(), uid);
    return { ok: true };
});

// ============================================================
// Edita nome completo/telefone/veículo de um motorista ou passageiro —
// chamada por "ver cadastro completo" no painel admin (nativo e web).
// Precisa ser Cloud Function (Admin SDK), não um update direto do
// cliente: firestore.rules só deixa o PRÓPRIO dono escrever em
// usuarios/{uid} (ver match /usuarios/{usuarioId}), um admin não é o
// dono. Autorizada pela senha master (como sempre) OU pela própria senha
// do admin logado, se ele tiver a permissão "usuarios" (ver
// autorizarComSenhaMasterOuPropria) — cada chamada fica registrada em
// logsAdministracao. Não mexe em email (login) nem fotoUrl (upload é fluxo
// separado) — só os campos que fazem sentido editar por aqui. "veiculo" é
// opcional: omitido/nulo pra passageiro, objeto completo (marca/modelo/
// cor/placa) pra motorista.
// ============================================================
exports.admAtualizarUsuario = functions.https.onCall(async (request) => {
    if (!ADMIN_MASTER_PASSWORD) {
        throw new functions.https.HttpsError('failed-precondition', 'Edição de usuário não configurada no servidor.');
    }

    const dados = request.data || {};
    const uid = (dados.uid || '').trim();
    const nomeCompleto = (dados.nomeCompleto || '').trim();
    const telefone = (dados.telefone || '').trim();
    const veiculo = dados.veiculo;
    const senhaAutorizacao = dados.senhaAutorizacao || '';

    if (!uid || !nomeCompleto || !telefone) {
        throw new functions.https.HttpsError('invalid-argument', 'Preencha nome completo e telefone.');
    }
    const autorizadoPor = await autorizarComSenhaMasterOuPropria(request, senhaAutorizacao, 'usuarios');

    const ref = admin.firestore().collection('usuarios').doc(uid);
    const doc = await ref.get();
    if (!doc.exists) {
        throw new functions.https.HttpsError('not-found', 'Usuário não encontrado.');
    }

    // Motorista vinculado (CPF cadastrado): nome/telefone novos também
    // precisam ser únicos entre os motoristas e o vínculo acompanha a edição
    // (ver vincularIdentidadeMotorista) — senão firestore.rules bloquearia
    // o próprio motorista de salvar depois, por divergir do vínculo.
    const vinculoDoc = await admin.firestore().collection('motoristasVinculo').doc(uid).get();
    if (vinculoDoc.exists) {
        const vinculo = vinculoDoc.data();
        await vincularIdentidadeMotorista(uid, { nomeCompleto, cpf: vinculo.cpf, telefone, email: vinculo.email });
    }

    const atualizacao = { nomeCompleto, telefone };
    if (veiculo && typeof veiculo === 'object') {
        atualizacao.veiculo = {
            marca: (veiculo.marca || '').trim(),
            modelo: (veiculo.modelo || '').trim(),
            cor: (veiculo.cor || '').trim(),
            placa: (veiculo.placa || '').trim(),
        };
    }

    await ref.update(atualizacao);
    if (request.auth && request.auth.uid) {
        await registrarLogAdministracao(request.auth.uid, 'usuario_editado', nomeCompleto, uid, autorizadoPor);
    }
    return { ok: true };
});

// ============================================================
// Vínculo de identidade do MOTORISTA (só motorista — conta apenas de
// passageiro não tem CPF nem reserva nada aqui): CPF, e-mail e telefone
// ficam vinculados a UMA conta e nenhum dos três pode se repetir em outro
// motorista. Existe pra impedir que a mesma pessoa abra outra conta (com
// outro e-mail) e recomece as 10 caronas grátis. Nome completo NÃO entra
// nessa trava (removido — travava homônimos reais, dois motoristas
// diferentes que por coincidência têm o mesmo nome; CPF/telefone/e-mail já
// bastam pra identificar a pessoa).
//
// Como funciona: motoristasUnicos/{chave} é uma "trava" por valor
// (cpf_<11 dígitos>, tel_<dígitos>, email_<sha256>) apontando pro uid dono;
// motoristasVinculo/{uid} guarda os valores em si (com o CPF, que NÃO vai
// pro documento público usuarios/{uid}). Ambas só são escritas aqui (Admin
// SDK) — firestore.rules nega qualquer escrita do cliente. "Repetido" só
// conta se o dono da trava ainda existe (usuarios/{dono}); trava de uma
// conta já apagada é reaproveitada, senão quem excluiu a conta ficaria
// impedido de voltar. Passageiro que vira motorista usa o MESMO uid: as
// travas dele mesmo nunca contam como conflito, então não há choque com a
// conta de passageiro.
//
// motoristasGratisPorCpf/{cpf} é o complemento disso: guarda, por CPF (não
// por uid), quantas caronas grátis já foram usadas e até quando ainda vale
// o acesso pago — ver preservarCreditoMotoristaPorCpf/
// restaurarCreditoMotoristaPorCpf mais abaixo. Sem isso, excluir a conta e
// recadastrar com outro e-mail (mesmo bloqueado de repetir CPF) ainda assim
// zerava as 10 caronas grátis, porque esses contadores viviam só em
// usuarios/{uid}, que some junto com a conta excluída.
// ============================================================
function somenteDigitos(valor) {
    return String(valor || '').replace(/\D/g, '');
}

function cpfValido(cpf) {
    const d = somenteDigitos(cpf);
    if (d.length !== 11 || /^(\d)\1{10}$/.test(d)) return false;
    for (const tamanho of [9, 10]) {
        let soma = 0;
        for (let i = 0; i < tamanho; i++) soma += Number(d[i]) * (tamanho + 1 - i);
        const digito = ((soma * 10) % 11) % 10;
        if (digito !== Number(d[tamanho])) return false;
    }
    return true;
}

// Sem DDI 55 e sem máscara, pra "(11) 99999-0000" e "+55 11 99999-0000"
// serem o mesmo telefone.
function normalizarTelefoneMotorista(valor) {
    let digitos = somenteDigitos(valor);
    if ((digitos.length === 12 || digitos.length === 13) && digitos.startsWith('55')) digitos = digitos.slice(2);
    return digitos;
}

function hashChaveMotorista(valor) {
    return crypto.createHash('sha256').update(valor).digest('hex');
}

function chavesIdentidadeMotorista({ cpf, telefone, email }) {
    return [
        { campo: 'CPF', id: `cpf_${somenteDigitos(cpf)}` },
        { campo: 'telefone', id: `tel_${normalizarTelefoneMotorista(telefone)}` },
        { campo: 'e-mail', id: `email_${hashChaveMotorista(String(email || '').trim().toLowerCase())}` },
    ];
}

// Snapshot, por CPF, de quanto crédito de motorista uma conta tinha bem no
// momento em que ela está sendo excluída (própria ou pelo admin) — chamado
// de dentro de excluirUsuarioCompleto, ANTES de apagar motoristasVinculo/
// usuarios/{uid}. Só existe algo a preservar se a conta chegou a vincular
// CPF (motoristasVinculo/{uid}); conta só-passageiro não tem nada aqui.
async function preservarCreditoMotoristaPorCpf(db, uid) {
    const vinculoSnap = await db.collection('motoristasVinculo').doc(uid).get();
    if (!vinculoSnap.exists) return;
    const cpf = vinculoSnap.data().cpf;
    if (!cpf) return;

    const perfilSnap = await db.collection('usuarios').doc(uid).get();
    if (!perfilSnap.exists) return;
    const perfil = perfilSnap.data();

    await db.collection('motoristasGratisPorCpf').doc(cpf).set({
        caronasOferecidas: perfil.caronasOferecidas || 0,
        acessoMotoristaExpiraEm: perfil.acessoMotoristaExpiraEm || null,
        atualizadoEm: admin.firestore.FieldValue.serverTimestamp(),
        ultimoUsuarioId: uid,
    }, { merge: true });
}

// Contraparte de preservarCreditoMotoristaPorCpf — chamada só na PRIMEIRA
// vez que um uid vincula um CPF (ver vincularIdentidadeMotorista). Se esse
// CPF já tem um crédito preservado de uma conta excluída antes, restaura as
// caronas grátis já usadas (pra não dar mais 10 de graça de novo) e, se
// ainda não tiver vencido, o acesso pago que ainda restava.
async function restaurarCreditoMotoristaPorCpf(db, uid, cpf) {
    const historicoSnap = await db.collection('motoristasGratisPorCpf').doc(cpf).get();
    if (!historicoSnap.exists) return;
    const historico = historicoSnap.data();

    const atualizacao = {};
    if (typeof historico.caronasOferecidas === 'number' && historico.caronasOferecidas > 0) {
        atualizacao.caronasOferecidas = historico.caronasOferecidas;
    }
    if (typeof historico.acessoMotoristaExpiraEm === 'number' && historico.acessoMotoristaExpiraEm > Date.now()) {
        atualizacao.acessoMotoristaExpiraEm = historico.acessoMotoristaExpiraEm;
    }
    if (Object.keys(atualizacao).length === 0) return;

    await db.collection('usuarios').doc(uid).update(atualizacao);
    await db.collection('motoristasGratisPorCpf').doc(cpf).set({ ultimoUsuarioId: uid }, { merge: true });
    console.log(`Crédito de motorista restaurado por CPF (${cpf}) na conta ${uid}.`);
}

async function vincularIdentidadeMotorista(uid, dados) {
    const db = admin.firestore();
    const nomeCompleto = String(dados.nomeCompleto || '').trim();
    const telefone = String(dados.telefone || '').trim();
    const email = String(dados.email || '').trim().toLowerCase();
    const cpf = somenteDigitos(dados.cpf);

    if (!nomeCompleto) {
        throw new functions.https.HttpsError('invalid-argument', 'Informe o nome completo.');
    }
    if (!cpfValido(cpf)) {
        throw new functions.https.HttpsError('invalid-argument', 'CPF inválido.');
    }
    if (normalizarTelefoneMotorista(telefone).length < 10) {
        throw new functions.https.HttpsError('invalid-argument', 'Telefone inválido.');
    }
    if (!email) {
        throw new functions.https.HttpsError('invalid-argument', 'A conta não tem e-mail.');
    }

    // O dono da trava só conta como "vivo" se o documento dele existe — sem
    // esse doc, as travas de QUEM ESTÁ VINCULANDO pareceriam abandonadas
    // pra o próximo cadastro e seriam tomadas.
    const perfil = await db.collection('usuarios').doc(uid).get();
    if (!perfil.exists) {
        throw new functions.https.HttpsError('failed-precondition', 'Cadastro do usuário não encontrado.');
    }

    const novas = chavesIdentidadeMotorista({ nomeCompleto, cpf, telefone, email });
    const vinculoRef = db.collection('motoristasVinculo').doc(uid);
    const travaRef = (chave) => db.collection('motoristasUnicos').doc(chave.id);

    let eraVinculoNovo = false;
    await db.runTransaction(async (tx) => {
        const vinculoSnap = await tx.get(vinculoRef);
        if (vinculoSnap.exists && vinculoSnap.data().cpf !== cpf) {
            throw new functions.https.HttpsError('failed-precondition', 'O CPF já vinculado a este cadastro não pode ser alterado.');
        }
        eraVinculoNovo = !vinculoSnap.exists;

        const travasNovas = await tx.getAll(...novas.map(travaRef));

        // Travas antigas deste mesmo uid que deixam de valer (telefone/e-mail
        // trocados) — liberadas pra outro motorista poder usar.
        const idsNovos = new Set(novas.map((c) => c.id));
        const antigas = vinculoSnap.exists
            ? chavesIdentidadeMotorista(vinculoSnap.data()).filter((c) => !idsNovos.has(c.id))
            : [];
        const travasAntigas = antigas.length ? await tx.getAll(...antigas.map(travaRef)) : [];

        const donosOcupados = [...new Set(
            travasNovas.filter((s) => s.exists && s.data().uid !== uid).map((s) => s.data().uid)
        )];
        const donoVivo = {};
        for (const dono of donosOcupados) {
            donoVivo[dono] = (await tx.get(db.collection('usuarios').doc(dono))).exists;
        }

        const conflitos = novas
            .filter((c, i) => travasNovas[i].exists && travasNovas[i].data().uid !== uid && donoVivo[travasNovas[i].data().uid])
            .map((c) => c.campo);
        if (conflitos.length > 0) {
            throw new functions.https.HttpsError(
                'already-exists',
                `Já existe um motorista cadastrado com o mesmo ${conflitos.join(', ')}.`
            );
        }

        travasAntigas.forEach((snap) => {
            if (snap.exists && snap.data().uid === uid) tx.delete(snap.ref);
        });
        const agora = admin.firestore.FieldValue.serverTimestamp();
        novas.forEach((chave) => tx.set(travaRef(chave), { uid, campo: chave.campo, atualizadoEm: agora }));
        tx.set(vinculoRef, {
            uid,
            nomeCompleto,
            cpf,
            telefone,
            email,
            atualizadoEm: agora,
            ...(vinculoSnap.exists ? {} : { criadoEm: agora }),
        }, { merge: true });
    });

    // Só na primeira vez que ESTE uid vincula um CPF — uma edição de
    // telefone/e-mail num vínculo já existente não deve reaplicar o
    // histórico de novo por cima do contador que já está rodando ao vivo.
    if (eraVinculoNovo) {
        await restaurarCreditoMotoristaPorCpf(db, uid, cpf);
    }
}

exports.registrarMotorista = functions.https.onCall(async (request) => {
    const uid = request.auth && request.auth.uid;
    if (!uid) {
        throw new functions.https.HttpsError('unauthenticated', 'Usuário não autenticado.');
    }
    const email = request.auth.token && request.auth.token.email;
    const dados = request.data || {};
    await vincularIdentidadeMotorista(uid, {
        nomeCompleto: dados.nomeCompleto,
        cpf: dados.cpf,
        telefone: dados.telefone,
        email,
    });
    return { ok: true };
});

// ============================================================
// Responde uma reclamação/sugestão/denúncia (ver Manifestacao.kt,
// "Ver Sugestões e Reclamações" no painel admin nativo/web) — manda a
// resposta por e-mail pro autor e marca status="respondido" com
// data/hora + CPF de quem respondeu. Precisa ser Cloud Function (não um
// update direto do cliente) por dois motivos: só o servidor pode mandar
// e-mail de verdade, e o CPF de quem respondeu é buscado AQUI no
// documento admins/{uid} do chamador — nunca confiando num campo que o
// app poderia mandar errado/forjado.
//
// Gate mais leve que cadastrarAdmin/excluirAdmin/excluirUsuario: aqui
// não é preciso a senha do administrador master, só que quem está
// chamando seja mesmo um admin logado (não é uma ação destrutiva, é só
// responder) — mesmo espírito do admResponderSugestao do Match.
// ============================================================
exports.responderManifestacao = functions.https.onCall(async (request) => {
    const uid = request.auth && request.auth.uid;
    if (!uid) {
        throw new functions.https.HttpsError('unauthenticated', 'Usuário não autenticado.');
    }
    if (!supportMailTransporter) {
        throw new functions.https.HttpsError('failed-precondition', 'E-mail de suporte não configurado no servidor.');
    }

    const db = admin.firestore();
    const adminDoc = await db.collection('admins').doc(uid).get();
    if (!adminDoc.exists) {
        throw new functions.https.HttpsError('permission-denied', 'Só administradores podem responder.');
    }
    const cpfAdmin = adminDoc.get('cpf') || null;

    const { manifestacaoId, resposta } = request.data || {};
    if (!manifestacaoId) {
        throw new functions.https.HttpsError('invalid-argument', 'manifestacaoId é obrigatório.');
    }
    if (typeof resposta !== 'string' || !resposta.trim()) {
        throw new functions.https.HttpsError('invalid-argument', 'resposta é obrigatória.');
    }

    const manifestacaoRef = db.collection('manifestacoes').doc(manifestacaoId);
    const manifestacaoDoc = await manifestacaoRef.get();
    if (!manifestacaoDoc.exists) {
        throw new functions.https.HttpsError('not-found', 'Mensagem não encontrada.');
    }
    const manifestacao = manifestacaoDoc.data();
    if (!manifestacao.email) {
        throw new functions.https.HttpsError('failed-precondition', 'Esta mensagem não tem e-mail associado para resposta.');
    }

    const rotulos = { reclamacao: 'reclamação', sugestao: 'sugestão', denuncia: 'denúncia' };
    const rotulo = rotulos[manifestacao.tipo] || 'mensagem';
    const nomeAutor = manifestacao.nomeCompleto || 'Usuário';
    const dataFormatada = new Date().toLocaleString('pt-BR', { timeZone: 'America/Sao_Paulo' });

    const corpoEmail = `
        <h2>Olá, ${escapeHtml(nomeAutor)}!</h2>
        <p>Sua ${rotulo} enviada pelo app Caronas foi respondida:</p>
        <blockquote style="border-left:3px solid #ccc;margin:12px 0;padding-left:12px;color:#333;">
            ${escapeHtml(resposta).replace(/\n/g, '<br>')}
        </blockquote>
        <hr>
        <p style="color:#888;font-size:12px;"><strong>Mensagem original:</strong><br>${escapeHtml(manifestacao.mensagem || '')}</p>
        <p style="color:#888;font-size:12px;">Enviado em ${dataFormatada}</p>
    `;

    try {
        await supportMailTransporter.sendMail({
            from: `"Caronas - Suporte" <${SUPPORT_EMAIL_USER}>`,
            to: manifestacao.email,
            replyTo: SUPPORT_EMAIL_TO || SUPPORT_EMAIL_USER,
            subject: `Resposta à sua ${rotulo} — Caronas`,
            html: corpoEmail,
        });

        await manifestacaoRef.update({
            resposta,
            status: 'respondido',
            respondidoEm: admin.firestore.FieldValue.serverTimestamp(),
            respondidoPorAdminId: uid,
            respondidoPorCpf: cpfAdmin,
        });

        console.log('📨 Resposta de manifestação enviada com sucesso para:', manifestacao.email);
        return { ok: true };
    } catch (error) {
        console.error('❌ Erro ao enviar resposta de manifestação:', error);
        throw new functions.https.HttpsError('internal', 'Erro ao enviar e-mail: ' + error.message);
    }
});

// ============================================================
// Exclui em definitivo uma reclamação/sugestão/denúncia (arquivada ou
// não) — só com a senha do administrador master, mesma trava de
// excluirAdmin/excluirUsuario. firestore.rules bloqueia delete direto do
// cliente (allow delete: if false), só o Admin SDK aqui consegue.
// ============================================================
exports.admExcluirManifestacao = functions.https.onCall(async (request) => {
    if (!ADMIN_MASTER_PASSWORD) {
        throw new functions.https.HttpsError('failed-precondition', 'Exclusão não configurada no servidor.');
    }

    const { manifestacaoId, senhaAutorizacao } = request.data || {};
    if (!manifestacaoId) {
        throw new functions.https.HttpsError('invalid-argument', 'manifestacaoId é obrigatório.');
    }
    if (senhaAutorizacao !== ADMIN_MASTER_PASSWORD) {
        throw new functions.https.HttpsError('permission-denied', 'Senha do administrador master incorreta.');
    }

    try {
        await admin.firestore().collection('manifestacoes').doc(manifestacaoId).delete();
    } catch (error) {
        console.error('❌ Erro ao excluir manifestação:', error);
        throw new functions.https.HttpsError('internal', 'Erro ao excluir: ' + error.message);
    }

    return { ok: true };
});

// ============================================================
// Reenvia o e-mail de verificação de cadastro pra um endereço já
// cadastrado — usada pela caixa "Suporte" da tela de login
// (LoginCaronasActivity, opção "Reenviar e-mail de validação") e também
// (via enviarEmailVerificacaoParaUid, ver acima) por cadastrarAdmin, pra
// mandar o primeiro e-mail de verificação de um admin/colaborador novo.
//
// O SDK Admin do Firebase não tem um método pronto pra "mandar o e-mail de
// verificação" (só admin.auth().generateEmailVerificationLink, que gera o
// link mas NÃO manda e-mail nenhum). A API REST que manda o e-mail de
// verdade (accounts:sendOobCode, requestType VERIFY_EMAIL) exige um ID
// TOKEN de usuário de verdade — um token OAuth desta Cloud Function sozinho
// não basta (erro INVALID_ID_TOKEN, já testado). Contorno padrão do Admin
// SDK pra "agir como" um usuário sem saber a senha dele: gerar um custom
// token (admin.auth().createCustomToken) e trocá-lo por um ID token real
// via REST (accounts:signInWithCustomToken, usando a chave web do projeto
// — não é secreta, é a mesma já embutida em public/index.html) — só então
// esse ID token serve pra pedir o envio de verdade. Essa troca é o que
// enviarEmailVerificacaoParaUid faz.
//
// Esta function não recebe request.auth — quem pede isso ainda não
// consegue logar (é literalmente o problema que está tentando resolver).
// ============================================================
const WEB_API_KEY = process.env.WEB_API_KEY || '';

exports.reenviarVerificacaoEmail = functions.https.onCall(async (request) => {
    if (!WEB_API_KEY) {
        throw new functions.https.HttpsError('failed-precondition', 'Reenvio de verificação não configurado no servidor.');
    }

    const dados = request.data || {};
    const email = (dados.email || '').trim().toLowerCase();

    if (!email) {
        throw new functions.https.HttpsError('invalid-argument', 'E-mail é obrigatório.');
    }

    let usuario;
    try {
        usuario = await admin.auth().getUserByEmail(email);
    } catch (error) {
        // Não revela se o e-mail existe ou não (mesmo espírito de
        // sendPasswordResetEmail do Firebase, que também não revela) —
        // sempre responde sucesso pro cliente.
        return { ok: true };
    }

    if (usuario.emailVerified) {
        return { ok: true, jaVerificado: true };
    }

    const enviado = await enviarEmailVerificacaoParaUid(usuario.uid);
    if (!enviado) {
        throw new functions.https.HttpsError('internal', 'Erro ao reenviar e-mail de verificação.');
    }

    return { ok: true };
});

// ============================================================
// Notificações push (FCM) do fluxo de caronas — mesmo padrão do
// notificarNovaMensagemChatAdmin do Match: sempre "data-only" (nunca um
// bloco "notification" mandado daqui), token lido de usuarios/{uid}.fcmToken
// (gravado pelo app em FcmTokenUtil.kt sempre que TelaCaronasActivity
// carrega o perfil), e limpeza do token quando o FCM reporta
// "registration-token-not-registered" (app desinstalado, dados do Google
// Play Services limpos etc.) — sem isso ficaria tentando enviar pra um
// token morto pra sempre. Quem decide título/canal/ícone é sempre o cliente
// (CaronasFirebaseMessagingService.onMessageReceived), baseado no campo
// "tipo" do payload.
// ============================================================

async function enviarNotificacaoCarona(destinatarioId, tipo, corpo, extras) {
    if (!destinatarioId) return;
    const db = admin.firestore();
    const destinatarioSnap = await db.collection('usuarios').doc(destinatarioId).get();
    const token = destinatarioSnap.exists ? destinatarioSnap.data().fcmToken : null;
    if (!token) return;

    try {
        await admin.messaging().send({
            token,
            data: { tipo, corpo, ...extras },
            android: { priority: 'high' },
        });
    } catch (error) {
        console.warn(`⚠️ Erro ao enviar push (${tipo}):`, error.message);
        if (error.code === 'messaging/registration-token-not-registered') {
            await db.collection('usuarios').doc(destinatarioId)
                .update({ fcmToken: admin.firestore.FieldValue.delete() });
        }
    }
}

// Passageiro solicitou vaga -> avisa o motorista. Dispara ao criar o
// documento em solicitacoes/{id} (ver SolicitacaoRepository.solicitarVaga) —
// toda solicitação nova nasce com status "solicitada", não precisa checar
// status aqui.
exports.notificarSolicitacaoViagem = onDocumentCreated('solicitacoes/{solicitacaoId}', async (event) => {
    const dados = event.data?.data();
    if (!dados) return;

    const nomePassageiro = (dados.passageiroNome || '').trim().split(' ')[0] || 'Um passageiro';
    const corpo = `${nomePassageiro} quer uma vaga de ${dados.cidadeOrigem || '?'} para ${dados.cidadeDestino || '?'}`;

    await enviarNotificacaoCarona(dados.motoristaId, 'solicitacaoViagem', corpo, {
        id: event.params.solicitacaoId,
        solicitacaoId: event.params.solicitacaoId,
    });
});

// Motorista aceitou a solicitação -> avisa o passageiro. Dispara ao
// ATUALIZAR solicitacoes/{id}, só quando o status vira "confirmada" agora
// (não dispara de novo em qualquer outro update do mesmo documento, ver
// SolicitacaoRepository.confirmarSolicitacao).
exports.notificarViagemAceita = onDocumentUpdated('solicitacoes/{solicitacaoId}', async (event) => {
    const antes = event.data?.before?.data();
    const depois = event.data?.after?.data();
    if (!antes || !depois) return;
    if (antes.status === 'confirmada' || depois.status !== 'confirmada') return;

    const corpo = `Sua viagem de ${depois.cidadeOrigem || '?'} para ${depois.cidadeDestino || '?'} foi aceita!`;

    await enviarNotificacaoCarona(depois.passageiroId, 'viagemAceita', corpo, {
        id: event.params.solicitacaoId,
        solicitacaoId: event.params.solicitacaoId,
    });
});

// Passageiro OU motorista cancelou a viagem -> avisa o OUTRO lado. Dispara
// ao ATUALIZAR solicitacoes/{id}, só quando o status vira "cancelada" agora
// (mesmo espírito de notificarViagemAceita acima). Quem cancelou vem do
// campo canceladoPor ("passageiro"/"motorista", gravado por
// SolicitacaoRepository.cancelarSolicitacao/cancelarComoMotorista) — sem
// esse campo não dava pra saber qual dos dois participantes precisa ser
// avisado (o status sozinho não diz quem tomou a ação).
exports.notificarViagemCancelada = onDocumentUpdated('solicitacoes/{solicitacaoId}', async (event) => {
    const antes = event.data?.before?.data();
    const depois = event.data?.after?.data();
    if (!antes || !depois) return;
    if (antes.status === 'cancelada' || depois.status !== 'cancelada') return;

    const foiMotoristaQueCancelou = depois.canceladoPor === 'motorista';
    const destinatarioId = foiMotoristaQueCancelou ? depois.passageiroId : depois.motoristaId;
    const nomeQuemCancelou = foiMotoristaQueCancelou
        ? ((depois.motoristaNome || '').trim().split(' ')[0] || 'O motorista')
        : ((depois.passageiroNome || '').trim().split(' ')[0] || 'O passageiro');
    const corpo = `${nomeQuemCancelou} cancelou a viagem de ${depois.cidadeOrigem || '?'} para ${depois.cidadeDestino || '?'}`;

    await enviarNotificacaoCarona(destinatarioId, 'viagemCancelada', corpo, {
        id: event.params.solicitacaoId,
        solicitacaoId: event.params.solicitacaoId,
    });
});

// Nova mensagem no chat de uma carona -> avisa quem recebeu. Dispara ao
// criar um documento em conversas/{conversaId}/mensagens/{mensagemId} (ver
// ChatCaronaRepository.enviarMensagem) — destinatarioId já vem gravado na
// própria mensagem, não precisa olhar o documento pai da conversa. Nunca
// manda o conteúdo real da mensagem na notificação (mesma razão do Match:
// notificação do sistema fica visível na tela de bloqueio).
exports.notificarMensagemCaronas = onDocumentCreated(
    'conversas/{conversaId}/mensagens/{mensagemId}',
    async (event) => {
        const dados = event.data?.data();
        if (!dados) return;

        await enviarNotificacaoCarona(dados.destinatarioId, 'mensagemCaronas', 'Você recebeu uma nova mensagem', {
            id: event.params.mensagemId,
            conversaId: event.params.conversaId,
        });
    }
);

// Nova mensagem no chat entre ADMINISTRADORES (separado do chat de carona
// acima) -> avisa quem recebeu. Token lido de admins/{id}.fcmToken (gravado
// por NotificacaoRepository.atualizarTokenAdmin, sempre que
// AdministracaoCaronasActivity carrega o perfil) — mesmo padrão de
// notificarNovaMensagemChatAdmin do Match, incluindo nunca mandar o
// conteúdo real da mensagem na notificação (nem cifrado, nem decifrado):
// o campo "preview" é sempre um texto fixo.
exports.notificarNovaMensagemChatAdmin = onDocumentCreated(
    'conversasAdmin/{conversaId}/mensagens/{mensagemId}',
    async (event) => {
        const dados = event.data?.data();
        if (!dados) return;

        const destinatarioId = dados.destinatarioId;
        if (!destinatarioId) return;

        const db = admin.firestore();
        const destinatarioSnap = await db.collection('admins').doc(destinatarioId).get();
        const token = destinatarioSnap.exists ? destinatarioSnap.data().fcmToken : null;
        if (!token) return;

        try {
            const remetenteNome = dados.remetenteNome || 'Administrador';
            await admin.messaging().send({
                token,
                data: {
                    tipo: 'chatAdmin',
                    id: event.params.mensagemId,
                    conversaId: event.params.conversaId,
                    remetenteId: dados.remetenteId || '',
                    remetenteNome,
                    // Nunca o conteúdo real da mensagem (nem cifrado, nem
                    // decifrado) — só quem mandou, igual ao Match.
                    corpo: `Nova mensagem de ${remetenteNome}`,
                },
                android: { priority: 'high' },
            });
        } catch (error) {
            console.warn('⚠️ Erro ao enviar push do chat admin:', error.message);
            if (error.code === 'messaging/registration-token-not-registered') {
                await db.collection('admins').doc(destinatarioId)
                    .update({ fcmToken: admin.firestore.FieldValue.delete() });
            }
        }
    }
);

// ============================================================
// Nova reclamação/sugestão/denúncia -> avisa TODOS os admins (não um uid
// específico como os gatilhos acima, já que qualquer admin pode
// responder) — dispara ao criar o documento em manifestacoes/{id} (ver
// Manifestacao.kt/EnviarManifestacaoActivity). Título fixo "Mensagem de
// usuário" no app (ver CaronasFirebaseMessagingService, campo "tipo" =
// "novaManifestacao"); o corpo aqui já vem pronto porque data-only não
// tem "notification block" pro servidor escolher o título sozinho.
// ============================================================
exports.notificarNovaManifestacao = onDocumentCreated('manifestacoes/{manifestacaoId}', async (event) => {
    const dados = event.data?.data();
    if (!dados) return;

    const db = admin.firestore();
    const adminsSnap = await db.collection('admins').get();
    const tokens = adminsSnap.docs
        .map((doc) => doc.get('fcmToken'))
        .filter((token) => typeof token === 'string' && token.length > 0);
    if (tokens.length === 0) return;

    const rotulos = { reclamacao: 'reclamação', sugestao: 'sugestão', denuncia: 'denúncia' };
    const rotulo = rotulos[dados.tipo] || 'mensagem';
    const nome = (dados.nomeCompleto || 'Um usuário').trim() || 'Um usuário';
    const corpo = `Nova ${rotulo} de ${nome}`;

    try {
        const resposta = await admin.messaging().sendEachForMulticast({
            tokens,
            data: { tipo: 'novaManifestacao', corpo, id: event.params.manifestacaoId },
            android: { priority: 'high' },
        });

        // Limpa tokens inválidos (conta removida, app desinstalado, etc.) —
        // mesmo motivo de enviarNotificacaoCarona acima, só que aqui precisa
        // mapear de volta qual admin tinha qual token, já que é um envio
        // multicast pra vários destinatários de uma vez.
        resposta.responses.forEach((r, i) => {
            if (r.success) return;
            if (r.error?.code !== 'messaging/registration-token-not-registered') return;
            const adminDoc = adminsSnap.docs.find((doc) => doc.get('fcmToken') === tokens[i]);
            if (adminDoc) adminDoc.ref.update({ fcmToken: admin.firestore.FieldValue.delete() }).catch(() => {});
        });
    } catch (error) {
        console.warn('⚠️ Erro ao enviar push de nova manifestação:', error.message);
    }
});
