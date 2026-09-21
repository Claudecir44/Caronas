// Regenera, em public/index.html, o conteúdo das abas "Orientações" e
// "Configurações" (Termos de Uso e Privacidade) do painel web a partir das
// fontes do APP — o app é a fonte da verdade, o painel web é uma cópia:
//   - app/src/main/java/com/cjstudio/caronas/OrientacoesAdminConteudo.kt
//   - app/src/main/res/raw/termos_privacidade_caronas.txt
// Uso (na raiz do projeto):  node tools/gerar_orientacoes_web.js
// Depois publique o hosting. Rode sempre que um desses dois arquivos mudar.
const fs = require('fs');
const path = require('path');

const raiz = path.join(__dirname, '..');
const kt = fs.readFileSync(path.join(raiz, 'app/src/main/java/com/cjstudio/caronas/OrientacoesAdminConteudo.kt'), 'utf8');
const termos = fs.readFileSync(path.join(raiz, 'app/src/main/res/raw/termos_privacidade_caronas.txt'), 'utf8');
const arquivoHtml = path.join(raiz, 'public/index.html');

// (título, corpo) de cada seção do Kotlin: "Título" to """ corpo """.trimIndent()
const re = /"([^"\n]+)" to """\r?\n([\s\S]*?)\r?\n\s*""".trimIndent\(\)/g;
const secoes = [];
let m;
while ((m = re.exec(kt)) !== null) {
  secoes.push([m[1], m[2].replace(/\$\{'\$'\}/g, '$')]);
}
if (secoes.length < 10) throw new Error('Não consegui ler as seções do Kotlin (' + secoes.length + ').');

// O painel web tem abas em vez de botões, cliques em vez de toques e não tem
// a seção Mensagens (só o app administrativo tem).
const abasWeb = `Cada aba do painel:
• Motoristas: e-mail, CPF vinculado, telefone, veículo, viagens realizadas e total recebido. Clique na linha para ver o cadastro completo (dá para editar, com a senha do administrador master); o ícone 🗑️ exclui o cadastro.
• Passageiros: todos os cadastros (todo motorista também é passageiro), com viagens e total pago. Mesmos cliques.
• Viagens (Motoristas): as ofertas publicadas, com rota, data e hora, vagas e status.
• Viagens (Passageiros): as solicitações de vaga, com rota, passageiro, motorista, valor e status.
• 📝 Sugestões e Reclamações: reclamações, sugestões e denúncias dos usuários; responda por e-mail e arquive. O número vermelho é a quantidade sem resposta.
• 💰 Financeiro: pagamentos do acesso de motorista, filtro por período e relatório mensal.
• 🧭 Orientações: este guia.
• ⚙️ Configurações: Termos de Uso e Privacidade.

Só existem no aplicativo administrativo (Android): Mensagens (ler as conversas de um usuário), Editar Perfil e a criação de novos administradores.`;

const trocas = [
  ['Monitorar: em Mensagens, digite nome, telefone, e-mail (ou CPF de motorista), toque no usuário e leia as conversas.',
    'Monitorar: ler as conversas de um usuário é feito no aplicativo administrativo (Android), na seção Mensagens — busque por nome, telefone, e-mail ou CPF de motorista.'],
  ['• Botão Sugestões: se o número vermelho aparecer', '• Aba Sugestões e Reclamações: se o número vermelho aparecer'],
  ['chega em Sugestões.', 'chega na aba Sugestões e Reclamações.'],
  ['confira o contexto em Mensagens e Viagens', 'confira o contexto em Viagens (e, no app administrativo, em Mensagens)'],
  ['toque e segure na linha', 'clique no ícone 🗑️ da linha'],
  ['Toque e segure na linha', 'Clique no ícone 🗑️ da linha'],
  ['toque e segure', 'clique no 🗑️'],
];

const web = secoes.map(([titulo, corpo]) => {
  if (titulo.startsWith('🖥️')) return ['🖥️ O que faz cada aba do painel', abasWeb];
  let t = corpo;
  for (const [de, para] of trocas) t = t.split(de).join(para);
  t = t.replace(/\bToque (em|no|na|para)\b/g, 'Clique $1').replace(/\btoque (em|no|na|para)\b/g, 'clique $1');
  return [titulo, t];
});

let html = fs.readFileSync(arquivoHtml, 'utf8');
const reBloco = /const ORIENTACOES_ADMIN = [\s\S]*?;\nconst TERMOS_PRIVACIDADE_TEXTO = .*;\n/;
if (!reBloco.test(html)) throw new Error('Bloco ORIENTACOES_ADMIN não encontrado em public/index.html.');
const novo = 'const ORIENTACOES_ADMIN = ' + JSON.stringify(web, null, 2) + ';\n' +
  'const TERMOS_PRIVACIDADE_TEXTO = ' + JSON.stringify(termos) + ';\n';
html = html.replace(reBloco, () => novo);
fs.writeFileSync(arquivoHtml, html);
console.log('OK: ' + web.length + ' seções e os Termos atualizados em public/index.html');
