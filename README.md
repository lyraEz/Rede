# Rede

Extensões para Aniyomi, AniZen e NyanTV.

## Adicionar a loja

**Aniyomi**

```text
https://raw.githubusercontent.com/lyraEz/Rede/repo/store-v2.json
```

**AniZen e NyanTV**

```text
https://raw.githubusercontent.com/lyraEz/Rede/repo/index.min.json
```

Depois de adicionar a loja, atualize a lista de extensões no aplicativo.

> **Atenção para a versão 1000-13:** a chave de assinatura foi renovada. Esse procedimento é necessário somente nesta troca de chave:
>
> 1. Desinstale a RedeToons 1000-12 ou anterior.
> 2. Remova a loja Rede do aplicativo e adicione o endereço novamente. O aplicativo guarda a chave da loja no momento em que ela é adicionada e não aceita a troca ao atualizar a lista; sem esse passo, a extensão instalada não é reconhecida e o botão **Instalar** continua aparecendo.
> 3. Atualize a lista de extensões e instale a RedeToons. Se ela aparecer como não confiável, toque em **Confiar**.

## Extensões

| Extensão | Versão | Site |
| --- | --- | --- |
| [RedeToons](https://raw.githubusercontent.com/lyraEz/Rede/repo/apk/aniyomi-pt.redetoons-v1000-13.apk) | 1000-13 | [redetoons.watch](https://redetoons.watch/br) |

A RedeToons corrige o APK da extensão publicado pela [Awerkori](https://github.com/Awerkori/anime-extensoes). As versões anteriores estão preservadas nas tags [1000-7](https://github.com/lyraEz/Rede/tree/redetoons-v1000-7), [1000-8](https://github.com/lyraEz/Rede/tree/redetoons-v1000-8), [1000-9](https://github.com/lyraEz/Rede/tree/redetoons-v1000-9), [1000-10](https://github.com/lyraEz/Rede/tree/redetoons-v1000-10), [1000-11](https://github.com/lyraEz/Rede/tree/redetoons-v1000-11) e [1000-12](https://github.com/lyraEz/Rede/tree/redetoons-v1000-12).

Quem já usa o endereço da loja com `store-v2-1000-7.json` pode mantê-lo; ele também recebe as versões novas.

A extensão usa a assinatura da Rede. Instalações assinadas pela Awerkori ou pela chave antiga da Rede precisam ser desinstaladas antes da versão 1000-13. As próximas atualizações usarão a nova assinatura.

A extensão acompanha automaticamente os redirecionamentos do site durante a reprodução. Se um domínio antigo sair do ar sem redirecionar, abra as configurações da RedeToons e preencha **Domínio do site** com a nova URL; caminhos como `/br` são aceitos.
