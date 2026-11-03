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

> **Atenção para a versão 1000-14:** a chave de assinatura foi renovada de novo. Esse procedimento é necessário somente nesta troca de chave:
>
> 1. Desinstale a RedeToons 1000-13 ou anterior.
> 2. Remova a loja Rede do aplicativo e adicione o endereço novamente. O aplicativo guarda a chave da loja no momento em que ela é adicionada e não aceita a troca ao atualizar a lista; sem esse passo, a extensão instalada não é reconhecida e o botão **Instalar** continua aparecendo.
> 3. Atualize a lista de extensões e instale a RedeToons. Se ela aparecer como não confiável, toque em **Confiar**.
> 4. Os títulos salvos na biblioteca pela versão 1000-13 ou anterior podem não abrir. Nesse caso, busque o título de novo ou use **Migrar** para a própria RedeToons.

## Extensões

| Extensão | Versão | Site |
| --- | --- | --- |
| [RedeToons](https://raw.githubusercontent.com/lyraEz/Rede/repo/apk/aniyomi-pt.redetoons-v1000-17.apk) | 1000-17 | [redetoons.watch](https://redetoons.watch/br) |

A versão 1000-14 foi reescrita para o site novo da RedeToons. As versões anteriores, baseadas no APK da [Awerkori](https://github.com/Awerkori/anime-extensoes), estão preservadas nas tags [1000-7](https://github.com/lyraEz/Rede/tree/redetoons-v1000-7), [1000-8](https://github.com/lyraEz/Rede/tree/redetoons-v1000-8), [1000-9](https://github.com/lyraEz/Rede/tree/redetoons-v1000-9), [1000-10](https://github.com/lyraEz/Rede/tree/redetoons-v1000-10), [1000-11](https://github.com/lyraEz/Rede/tree/redetoons-v1000-11) e [1000-12](https://github.com/lyraEz/Rede/tree/redetoons-v1000-12).

Quem já usa o endereço da loja com `store-v2-1000-7.json` pode mantê-lo; ele também recebe as versões novas.

### Login para assistir

O site só libera os vídeos para contas logadas. Coloque o e-mail (ou usuário) e a senha da sua conta RedeToons nas configurações da extensão (**Navegar › Extensões › RedeToons ›** engrenagem). A extensão entra na conta e faz a verificação de segurança do site sozinha, sem abrir o WebView. A senha fica salva só no aparelho.

Se o site pedir para clicar em "não sou um robô", a extensão não consegue concluir sozinha e mostra uma mensagem. Nesse caso, abra o título no WebView, faça a verificação uma vez e tente de novo.

Se o domínio mudar e o antigo não redirecionar, abra as configurações da RedeToons e preencha **Domínio do site** com a nova URL; caminhos como `/br` são aceitos.
