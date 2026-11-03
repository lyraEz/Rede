# Manutenção

`main` contém os arquivos de manutenção. `repo` contém os índices e APKs usados pelos aplicativos. Os dois ramos mantêm os mesmos arquivos de publicação.

## RedeToons

O código fica em `src/`. O site é um Laravel com Inertia: cada página traz os dados num `<script id="su-page-data">`, e a extensão lê esse JSON.

- Catálogo: sliders de `/br`. Recentes: `/br/archive`. Busca: `/br/search?q=`.
- Título e episódios: `/br/titles/{id}-{slug}` e `/br/titles/{id}-{slug}/season-{n}`.
- Vídeo: `/api/watch/{id}/next?e={episódio}&lang=br` devolve o MP4 em `play.sources`. Exige conta logada que passou pela verificação do site; sem isso a resposta é `{"error":"login"}`.

Para gerar o APK (Java 17 ou mais recente):

```sh
REDE_KEY_PASS=... python build_extension.py --keystore rede-extensoes.p12 \
    --version 1000-15 --template apk/aniyomi-pt.redetoons-v1000-14.apk
```

O script baixa as ferramentas para `build-tools/.cache`, compila `src/` contra as interfaces em `build-tools/stubs`, reaproveita manifesto e ícone do APK indicado em `--template` e assina com esquema v2. O nome de versão começa com a versão da biblioteca (`14.`), usada pelo NyanTV para reconhecer a extensão.

A chave privada não fica no repositório; guarde o `.p12` e a senha fora dele. A partir da versão 1000-14, o certificado SHA-256 é `591ad45499955a7380d53c627e8f96128a53aec00020f154de917ab7b8e896e3`.

Para adicionar uma extensão ao catálogo, coloque o APK em `apk/`, o ícone em `icon/`, atualize `extensions.json` e execute:

```sh
python build_indexes.py
```

O gerador mantém os índices modernos e legados. O endereço `store-v2-1000-7.json` continua atualizado para instalações existentes.
