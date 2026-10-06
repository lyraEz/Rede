# RedeToons para Aniyomi

Extensão RedeToons `1000-7` para `https://redetoons.email`, baseada no APK `14.2` fornecido pelo usuário. A página inicial do site é `/browse`; a extensão consulta diretamente a API pública usada pelo site.

## Instalação

Adicione este repositório ao Aniyomi:

`https://raw.githubusercontent.com/lyraEz/Rede/repo/index.min.json`

O APK está em [`repo/apk/aniyomi-pt.redetoons-v1000-7-r1.apk`](https://github.com/lyraEz/Rede/blob/repo/apk/aniyomi-pt.redetoons-v1000-7-r1.apk).

O índice antigo (`index.min.json`) aponta para [`repo.json`](https://raw.githubusercontent.com/lyraEz/Rede/repo/repo.json), que encaminha o Aniyomi atual para [`store.json`](https://raw.githubusercontent.com/lyraEz/Rede/repo/store.json). Os três arquivos são mantidos para compatibilidade. No índice antigo, o campo `version` é `14.1000-7`, pois esse formato exige um prefixo numérico da biblioteca; a versão do APK e da loja atual é `1000-7`.

**Instalação da versão antiga:** o APK original `14.2` e a versão `14.3` da Awerkori usam a assinatura `Project Nox`. A versão `1000-7` usa uma nova assinatura. O Android não permite atualizar um aplicativo com outra assinatura: desinstale a extensão RedeToons antiga e instale a nova. O pacote e a classe da fonte continuam iguais (`eu.kanade.tachiyomi.animeextension.pt.redetoons` e `.RedeToons`).

## Alterações

- Domínio atualizado de `redetoons.win` para `redetoons.email`, inclusive nos cabeçalhos `Referer` e `Origin` gerados pela extensão.
- Capas aceitam URLs completas e caminhos relativos.
- Detalhes preservam `movie/ID` ou `tv/ID` para que a lista de episódios use o endpoint correto.
- `versionName` `1000-7`, `versionCode` `1000007`.
- Metadado `aniyomix.extensionLib=14` para o Aniyomi reconhecer o APK com a versão `1000-7`.

O catálogo, a busca, os detalhes, a lista de episódios e a reprodução foram verificados nas respostas da API em 6 de outubro de 2026. O APK foi validado quanto à estrutura, alinhamento e assinatura. A instalação e a reprodução dentro de um dispositivo Android ainda precisam de um teste no aparelho. A disponibilidade dos links de vídeo e os desafios da Cloudflare dependem do site.

## Recriar o APK

O arquivo [`patch_rede.py`](patch_rede.py) contém o patch aplicado ao APK `14.2` enviado pelo usuário (SHA-256 `7567651ee99208ed8ad3584a9164b1af28e60fe3a7e8d831c61255255388fb80`). Ele espera uma pasta decodificada com Apktool 3.0.3:

```sh
java -jar apktool_3.0.3.jar d -f 'Aniyomi_ RedeToons_14.2.apk' -o decoded
python3 patch_rede.py decoded
java -jar apktool_3.0.3.jar b decoded -o rede-unsigned.apk
```

Assine com sua própria chave usando `apksigner` ou `uber-apk-signer`. A chave privada usada nesta publicação não faz parte do repositório público. Atualizações futuras do APK publicado precisam ser assinadas com a mesma chave.

O projeto [Aniyomi Extensions](https://github.com/aniyomiorg/aniyomi-extensions) documenta o desenvolvimento de fontes e o formato do repositório.
