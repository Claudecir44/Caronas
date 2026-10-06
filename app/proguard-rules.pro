# Regras do R8 (otimização/ofuscação do release) — ver app/build.gradle.kts.

# Linhas reais nos relatórios do Crashlytics (o mapping.txt é enviado
# automaticamente pelo plugin do Crashlytics e vai junto no .aab pro Play).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ============================================================
# Firestore converte documento <-> classe por REFLECTION
# (doc.toObject(X::class.java) / set(objeto)), casando o nome de cada campo
# do documento com os getters/setters gerados pelo Kotlin. Se o R8 renomear
# ou remover esses métodos (ou o construtor vazio), toObject() falha com
# "No properties to serialize found" — só no release, nunca no debug. Os
# campos privados também ficam com o nome original: o Firestore lê os
# campos junto com os getters, e campos renomeados pra "a"/"A" dão
# "Found two getters or fields with conflicting case" (caso real no
# Solicitacao, achado testando o release no aparelho).
# Só os modelos gravados/lidos pelo Firestore ficam com nomes; o resto do
# app é ofuscado normalmente. Classe NOVA de modelo do Firestore tem que
# entrar nesta lista.
# ============================================================
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

-keep class com.cjstudio.caronas.Admin { *; }
-keep class com.cjstudio.caronas.Avaliacao { *; }
-keep class com.cjstudio.caronas.Bloqueio { *; }
-keep class com.cjstudio.caronas.Carona { *; }
-keep class com.cjstudio.caronas.ConversaAdmin { *; }
-keep class com.cjstudio.caronas.ConversaCarona { *; }
-keep class com.cjstudio.caronas.LogAdministracao { *; }
-keep class com.cjstudio.caronas.Manifestacao { *; }
-keep class com.cjstudio.caronas.MensagemCarona { *; }
-keep class com.cjstudio.caronas.MensagemChatAdmin { *; }
-keep class com.cjstudio.caronas.PagamentoMotorista { *; }
-keep class com.cjstudio.caronas.ParadaRota { *; }
-keep class com.cjstudio.caronas.Solicitacao { *; }
-keep class com.cjstudio.caronas.Usuario { *; }
-keep class com.cjstudio.caronas.Veiculo { *; }
-keep class com.cjstudio.caronas.VinculoMotorista { *; }

# ============================================================
# ShortcutBadger (número no ícone do app) instancia a implementação de
# cada fabricante por reflection (Class.newInstance).
# ============================================================
-keep class me.leolin.shortcutbadger.impl.** { <init>(); }
