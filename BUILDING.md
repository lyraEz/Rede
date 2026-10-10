# Manutenção

`main` contém os arquivos de manutenção. `repo` contém os índices e APKs usados pelos aplicativos. Os dois ramos mantêm os mesmos arquivos de publicação.

## RedeToons

A versão atual parte do APK 14.2 da Awerkori. Para reconstruí-la:

```sh
java -jar apktool.jar d -f original-14.2.apk -o decoded
python patch_rede.py decoded
java -jar apktool.jar b -f decoded -o redetoons-unsigned.apk
```

O `-f` no build é necessário para atualizar a versão no manifesto. O nome de versão do APK começa com a versão da biblioteca (`14.`), usada pelo NyanTV para reconhecer a extensão. O script também mantém os métodos de vídeo antigos exigidos por esse aplicativo. Assine o APK com a chave privada da Rede, que não fica no repositório, e confira a assinatura antes de publicar. O certificado SHA-256 é `2e477a744a47fe5a7349816c1a4524dc48b2469610d094f62c1bc2782b94c5a6`.

Para adicionar uma extensão ao catálogo, coloque o APK em `apk/`, o ícone em `icon/`, atualize `extensions.json` e execute:

```sh
python build_indexes.py
```

O gerador mantém os índices modernos e legados. O endereço `store-v2-1000-7.json` continua atualizado para instalações existentes.
