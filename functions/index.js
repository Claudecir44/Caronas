// Carrega variáveis de ambiente do arquivo .env (se existir).
require('dotenv').config();

const functions = require('firebase-functions');
const { onDocumentCreated, onDocumentUpdated } = require('firebase-functions/v2/firestore');
const admin = require('firebase-admin');
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
const ACESSO_MOTORISTA_VALOR = 15.99;
const ACESSO_MOTORISTA_DIAS = 30;
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

exports.createPaymentPreferenceMotorista = functions.https.onCall(async (request) => {
    if (!MERCADOPAGO_ACCESS_TOKEN) {
        throw new functions.https.HttpsError('failed-precondition', 'Mercado Pago não configurado no servidor.');
    }
    if (!request.auth) {
        throw new functions.https.HttpsError('unauthenticated', 'Usuário não autenticado.');
    }
    const uid = request.auth.uid;

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
                id: 'acesso_motorista_30_dias',
                title: 'Acesso motorista Caronas — 30 dias',
                description: 'Libera oferecer caronas por mais 30 dias',
                quantity: 1,
                currency_id: 'BRL',
                unit_price: ACESSO_MOTORISTA_VALOR,
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
            metadata: { usuarioId: uid },
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

// Soma 30 dias à data de expiração atual (se ainda válida) ou a partir de
// agora (se vencida/nunca pagou) — evita que pagar de novo ANTES de
// vencer "perca" os dias que ainda restavam (mesmo raciocínio de
// concederPremium no Match, extends em vez de sobrescrever).
async function concederAcessoMotorista(usuarioId, paymentId) {
    const usuarioRef = admin.firestore().collection('usuarios').doc(usuarioId);
    const usuarioDoc = await usuarioRef.get();
    const usuarioData = usuarioDoc.exists ? usuarioDoc.data() : {};
    const atual = usuarioData.acessoMotoristaExpiraEm;
    const agora = Date.now();
    const baseMs = (atual && typeof atual.toMillis === 'function' && atual.toMillis() > agora) ? atual.toMillis() : agora;
    const novaExpiracao = new Date(baseMs + ACESSO_MOTORISTA_DIAS * 24 * 60 * 60 * 1000);

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
        valor: ACESSO_MOTORISTA_VALOR,
        dataCompra: agora,
        expiraEm: novaExpiracao.getTime(),
        mercadoPagoPaymentId: String(paymentId),
    });

    console.log(`✅ Acesso motorista concedido a ${usuarioId} até ${novaExpiracao.toISOString()} (pagamento ${paymentId})`);
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
        if (payment.metadata && payment.metadata.usuarioId) {
            usuarioId = payment.metadata.usuarioId;
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

            await concederAcessoMotorista(usuarioId, payment.id);
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

    try {
        const url =
            'https://api.locationiq.com/v1/autocomplete' +
            `?key=${LOCATIONIQ_API_KEY}` +
            `&q=${encodeURIComponent(consulta)}` +
            '&countryCodes=br&accept-language=pt&limit=5&normalizecity=1';
        const resposta = await fetch(url);

        // A própria API devolve 404 quando não acha nada — não é erro de
        // verdade, só "sem sugestão ainda" (comum com poucos caracteres).
        if (resposta.status === 404) {
            return { sugestoes: [] };
        }
        if (!resposta.ok) {
            console.error(`❌ LocationIQ respondeu ${resposta.status}`);
            return { sugestoes: [] };
        }

        const dados = await resposta.json();
        const sugestoes = Array.isArray(dados)
            ? dados.map((item) => item.display_name).filter(Boolean)
            : [];
        return { sugestoes };
    } catch (error) {
        console.error('❌ Erro ao consultar LocationIQ:', error);
        // Autocomplete é só uma ajuda visual — nunca deveria travar o
        // formulário do app, só deixar de sugerir.
        return { sugestoes: [] };
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
// escrevendo o próprio uid ali. Protegida por DOIS segredos fixos, só
// existentes aqui no .env (não são dados reais de nenhuma conta — CPF
// "do administrador master" aqui é só um identificador convencionado,
// mesmo valor usado como CPF do master no Match, sem ligação com nenhum
// campo "cpf" do Caronas, que nem existe): ADMIN_MASTER_CPF e
// ADMIN_MASTER_PASSWORD.
//
// Não recebe request.auth — quem está criando o admin ainda não tem
// sessão nenhuma (é literalmente o que está pedindo). Cria a conta com
// email+senha informados no formulário (reaproveitando uma conta já
// existente com esse e-mail, se houver — mesmo padrão do
// cadastrarAdminAutorizado do Match) e grava nome/sobrenome/email/
// telefone em admins/{uid}. A FOTO não entra aqui — o app faz login
// logo em seguida com o uid/senha recém-criados e sobe a foto
// diretamente pro Storage (ver AdminRepository.atualizarFotoAdmin),
// evitando ter que mandar um arquivo binário dentro do payload da
// function.
// ============================================================
const ADMIN_MASTER_PASSWORD = process.env.ADMIN_MASTER_PASSWORD || '';

if (!ADMIN_MASTER_PASSWORD) {
    console.error('❌ ADMIN_MASTER_PASSWORD NÃO CONFIGURADA!');
    console.error('👉 Defina ADMIN_MASTER_PASSWORD no arquivo .env da pasta functions.');
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

    if (!nome || !sobrenome || !email || !telefone || !cpf || !senha) {
        throw new functions.https.HttpsError('invalid-argument', 'Preencha todos os campos.');
    }
    if (senhaAutorizacao !== ADMIN_MASTER_PASSWORD) {
        throw new functions.https.HttpsError('permission-denied', 'Senha do administrador master incorreta.');
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
            // o uid, sem mexer na senha já existente dessa conta.
            const existente = await admin.auth().getUserByEmail(email);
            uid = existente.uid;
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
        criadoEm: admin.firestore.FieldValue.serverTimestamp(),
    }, { merge: true });

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
    if (senhaAutorizacao !== ADMIN_MASTER_PASSWORD) {
        throw new functions.https.HttpsError('permission-denied', 'Senha do administrador master incorreta.');
    }

    const ref = admin.firestore().collection('admins').doc(uid);
    const doc = await ref.get();
    if (!doc.exists) {
        throw new functions.https.HttpsError('not-found', 'Admin não encontrado.');
    }

    await ref.update({ nome, sobrenome, telefone, cpf });
    return { ok: true };
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
    if (senhaAutorizacao !== ADMIN_MASTER_PASSWORD) {
        throw new functions.https.HttpsError('permission-denied', 'Senha do administrador master incorreta.');
    }

    const ref = admin.firestore().collection('admins').doc(uid);
    const doc = await ref.get();
    if (!doc.exists) {
        throw new functions.https.HttpsError('not-found', 'Admin não encontrado.');
    }

    await ref.delete();
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
// Caronas não guarda CPF nem nenhum outro campo de unicidade pra usuário
// comum (só admin tem CPF) — então o único jeito de um recadastro "travar"
// é a conta Firebase Auth antiga ainda existir com o mesmo e-mail; por
// isso ela é sempre apagada por último, depois de tudo mais já ter sido
// limpo.
// ============================================================
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
    if (senhaAutorizacao !== ADMIN_MASTER_PASSWORD) {
        throw new functions.https.HttpsError('permission-denied', 'Senha do administrador master incorreta.');
    }

    const db = admin.firestore();

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

    return { ok: true };
});

// ============================================================
// Edita nome completo/telefone/veículo de um motorista ou passageiro —
// chamada por "ver cadastro completo" no painel admin (nativo e web).
// Precisa ser Cloud Function (Admin SDK), não um update direto do
// cliente: firestore.rules só deixa o PRÓPRIO dono escrever em
// usuarios/{uid} (ver match /usuarios/{usuarioId}), um admin não é o
// dono. Mesma trava de senha do administrador master das outras ações
// administrativas sensíveis (excluirAdmin/excluirUsuario/atualizarAdmin).
// Não mexe em email (login) nem fotoUrl (upload é fluxo separado) —
// só os campos que fazem sentido editar por aqui. "veiculo" é opcional:
// omitido/nulo pra passageiro, objeto completo (marca/modelo/cor/placa)
// pra motorista.
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
    if (senhaAutorizacao !== ADMIN_MASTER_PASSWORD) {
        throw new functions.https.HttpsError('permission-denied', 'Senha do administrador master incorreta.');
    }

    const ref = admin.firestore().collection('usuarios').doc(uid);
    const doc = await ref.get();
    if (!doc.exists) {
        throw new functions.https.HttpsError('not-found', 'Usuário não encontrado.');
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
// (LoginCaronasActivity, opção "Reenviar e-mail de validação").
//
// O SDK Admin do Firebase não tem um método pronto pra "reenviar o e-mail
// de verificação" (só admin.auth().generateEmailVerificationLink, que gera
// o link mas NÃO manda e-mail nenhum). A API REST que manda o e-mail de
// verdade (accounts:sendOobCode, requestType VERIFY_EMAIL) exige um ID
// TOKEN de usuário de verdade — um token OAuth desta Cloud Function sozinho
// não basta (erro INVALID_ID_TOKEN, já testado). Contorno padrão do Admin
// SDK pra "agir como" um usuário sem saber a senha dele: gerar um custom
// token (admin.auth().createCustomToken) e trocá-lo por um ID token real
// via REST (accounts:signInWithCustomToken, usando a chave web do projeto
// — não é secreta, é a mesma já embutida em public/index.html) — só então
// esse ID token serve pra pedir o reenvio de verdade.
//
// Não recebe request.auth — quem pede isso ainda não consegue logar (é
// literalmente o problema que está tentando resolver).
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

    try {
        const customToken = await admin.auth().createCustomToken(usuario.uid);

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
            throw new Error('Falha ao autenticar internamente.');
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
            throw new Error('Falha ao reenviar e-mail de verificação.');
        }
    } catch (error) {
        console.error('❌ Erro ao reenviar verificação:', error);
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
