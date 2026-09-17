// Carrega variáveis de ambiente do arquivo .env (se existir).
require('dotenv').config();

const functions = require('firebase-functions');
const { onDocumentCreated, onDocumentUpdated } = require('firebase-functions/v2/firestore');
const admin = require('firebase-admin');

admin.initializeApp();

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
