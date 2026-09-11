# Contribuindo com o JLoads

Que bom que você quer ajudar! O JLoads é um projeto aberto: issues, sugestões, forks e pull requests são bem-vindos.

## Antes de começar

- **Bugs e ideias:** abra uma [issue](../../issues) usando os modelos disponíveis. Para mudanças grandes, converse
  numa issue antes de escrever código — assim ninguém perde tempo.
- **Uso responsável:** o JLoads existe para baixar conteúdo que a pessoa tem direito de baixar. Não são aceitas
  contribuições que contornem DRM, login, cookies de contas, paywalls, verificação anti-robô ou outras restrições de
  acesso.

## Preparando o ambiente

Requisitos: **Java 21+**, **Node.js 24.15+**, **yt-dlp**, **FFmpeg** e **Deno ou Node** (runtime JavaScript usado
pelo yt-dlp). Veja as instruções de instalação por sistema no [README](README.md#instalando-as-dependências).

```bash
# Backend — http://localhost:8080
cd backend
./mvnw spring-boot:run

# Frontend — http://localhost:4200 (encaminha /api e /ws para o backend)
cd frontend
npm install
npm start
```

### Desenvolvendo sem acessar o YouTube

Os testes e o modo de desenvolvimento usam um **yt-dlp simulado**, então dá para trabalhar na interface sem depender
do YouTube (e sem cair na verificação anti-robô):

```bash
cd backend
YTDLP_EXECUTABLE=fake-yt-dlp ./mvnw spring-boot:test-run \
  -Dspring-boot.run.main-class=com.jloads.TestJLoadsApplication
```

IDs de vídeo simulados: `okvideo0001` (sucesso), `slowvideo01` (lento), `forbidden01` (erro 403),
`hugevideo01` (arquivo grande demais), `unavailable`, `livestream1`. Exemplo: `https://youtu.be/slowvideo01`.

## Testes

```bash
cd backend && ./mvnw test
cd frontend && npm test -- --watch=false
```

Os testes automatizados **nunca** acessam o YouTube. Todo pull request roda os testes no GitHub Actions.

## Padrões do código

- **Backend:** camadas `controller → service → model → infraestrutura`. Controllers não têm regra de negócio.
  Toda opção passada ao yt-dlp sai do `YtDlpCommandBuilder`, a partir de uma lista fixa — nunca de texto do usuário.
- **Frontend:** componentes standalone, signals e `ChangeDetectionStrategy.OnPush`. Estilos globais em
  `src/styles.css` usando as variáveis de tema.
- **Mensagens ao usuário** em português, amigáveis e sem detalhes técnicos.
- Siga o estilo do arquivo que você está editando: nomes, comentários e organização.

Detalhes da arquitetura, API e decisões de segurança estão em [docs/ARQUITETURA.md](docs/ARQUITETURA.md).

## Enviando um pull request

1. Faça um fork e crie uma branch a partir da `main` (`feat/cortar-trecho`, `fix/progresso-audio`…).
2. Faça commits pequenos e com mensagens claras.
3. Garanta que os testes passam e adicione testes para o que mudou.
4. Abra o pull request descrevendo o que muda, por que e como você testou.

## Ideias para contribuir

- Cortar um trecho (início e fim) antes de baixar
- Capa e tags (título, artista) nos arquivos MP3
- Histórico permanente com SQLite
- Tradução da interface (inglês, espanhol)
- Suporte a playlists de conteúdo próprio
- Empacotamento como aplicativo desktop

## Publicando uma versão (mantenedores)

```bash
git tag v1.1.0
git push origin v1.1.0
```

O workflow **Release** roda os testes, gera o pacote (`scripts/build-release.sh`) e publica os arquivos na página de
Releases.
