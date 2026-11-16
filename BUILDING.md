# Manutenção

`main` contém os arquivos de manutenção. `repo` contém os índices e APKs usados pelos aplicativos. Os dois ramos mantêm os mesmos arquivos de publicação.

## RedeToons

O código fica em `src/`. O site é um Laravel com Inertia: cada página traz os dados num `<script id="su-page-data">`, e a extensão lê esse JSON.

- Catálogo: sliders de `/br`. Recentes: `/br/archive`. Busca: `/br/search?q=`.
- Título e episódios: `/br/titles/{id}-{slug}` e `/br/titles/{id}-{slug}/season-{n}`.
- Vídeo: a página `/br/watch/{id}?type=tv&e={episódio}` (ou `?type=movie`) carrega o player num iframe (`/br/iframe/_su.html?p=...`). Para episódios, a extensão tenta antes `/api/watch/{id}/next?e={episódio}&lang=br` e aproveita qualquer `.mp4`, `.m3u8`, `.mpd` ou `/playlist/...` da resposta. Se não houver vídeo, e sempre para filmes, `RedeBrowser` abre a página num WebView invisível, faz login com a conta salva nas configurações, espera a verificação do site, dá play no iframe e usa o primeiro arquivo de vídeo que o player pede. Os cookies do WebView vão junto no pedido do vídeo.
- O site carrega `su-guard.js`, que manda a página para o YouTube quando acha que há um depurador aberto (o que acontece num WebView). `RedeBrowser` devolve um script vazio no lugar dele e de `gw.js` e não sai do domínio do site.

Para gerar o APK (Java 17 ou mais recente):

```sh
REDE_KEY_PASS=... python build_extension.py --keystore rede-extensoes.p12 \
    --version 1000-19 --template apk/aniyomi-pt.redetoons-v1000-18.apk
```

O script baixa as ferramentas para `build-tools/.cache`, compila `src/` contra as interfaces em `build-tools/stubs`, reaproveita manifesto e ícone do APK indicado em `--template` e assina com esquema v2. O nome de versão começa com a versão da biblioteca (`14.`), usada pelo NyanTV para reconhecer a extensão.

A chave privada não fica no repositório; guarde o `.p12` e a senha fora dele. A partir da versão 1000-18, o certificado SHA-256 é `bf9ae854e18ecdceaf2a4cae112f006252e2632ba9ba18df5f7538adc7b84724` (1000-14 a 1000-17: `591ad454…96e3`).

Para adicionar uma extensão ao catálogo, coloque o APK em `apk/`, o ícone em `icon/`, atualize `extensions.json` e execute:

```sh
python build_indexes.py
```

O gerador mantém os índices modernos e legados. O endereço `store-v2-1000-7.json` continua atualizado para instalações existentes.
