# Plano — Docker, IWADs no git e releases com doom1.wad

## Contexto
O jogo roda localmente (`bin/doom-java`, `.dmg`, fat jar). O usuário quer:
1. rodar via Docker, com Dockerfile em Linux que baixe todas as dependências;
2. IWADs versionados no git;
3. releases públicas já incluindo o `doom1.wad`;
4. execução simples.

**Decisões do usuário:**
- Docker com **dois modos**:
  - **navegador (padrão):** Xvfb + Mesa (OpenGL 3.3 por software) + noVNC em `http://localhost:6080`. Funciona em Mac, Windows e Linux, sem som;
  - **X11 nativo (Linux):** janela na tela do host, com som via PulseAudio.
- Eu preparo tudo (workflows, imagem, scripts) e testo localmente. Depois publico no repositório que o usuário criou (seção 5).

**Estado atual:**
- `.iwads/` e `*.wad` estão no `.gitignore`.
- Não há remote nem a CLI `gh`. Docker 28 está instalado.
- `release.yml` gera `.dmg`/`.msi`/app-image + fat jar, sem IWAD.
- `IwadLocator` (doom-wad) busca, nesta ordem: `-iwad`, `DOOMWADDIR`, `DOOMWADPATH`, diretório atual e diretório de dados do app.
- O LWJGL tem nativos Linux nos profiles do POM pai, e o profile `dist` do doom-app gera o fat jar e a imagem jlink.

## Mudanças

### 1. IWADs no git (novo ADR-023, que altera o ADR-019)
- Remover `.iwads/` e `*.wad` do `.gitignore`, mas manter ignorados os PWADs de teste gerados em `target/`.
- Commitar `.iwads/`: doom1.wad (4 MB, shareware 1.9), freedoom1/2 (27 MB cada) e freedm (21 MB). Cada arquivo fica abaixo de 50 MB, então não precisa de LFS.
- Adicionar `.iwads/README.md` com as licenças: Freedoom é BSD (com o `COPYING.txt` do Freedoom); o doom1.wad shareware só pode ser redistribuído sem modificação.
- IWADs comerciais continuam proibidos.
- Registrar o ADR-023 em `docs/spec/01-decisions.md` e em `docs/contracts.md`.

### 2. O jogo acha o IWAD sozinho
- `IwadLocator`: adicionar ao caminho de busca, depois do diretório atual:
  - `<diretório do app>/iwads`, obtido de `jpackage.app-path`, do jar em execução ou da variável `DOOM_APP_DIR`;
  - `/opt/doom-java/iwads` (o local na imagem Docker).
- Com isso o jogo abre sem `-iwad`: a ordem de nomes já existente prefere doom2 > … > doom1 > freedoom2 > freedoom1 > freedm.
- Teste unitário novo no `IwadLocatorTest`.

### 3. Docker (`docker/` + `Dockerfile` na raiz)
- **Estágio 1 (build):** `eclipse-temurin:25-jdk`. Copia o repositório e roda `./mvnw -B -Pdist -pl doom-app -am package -DskipTests` → fat jar com nativos Linux da arquitetura da build (amd64 ou arm64).
- **Estágio 2 (runtime):** `eclipse-temurin:25-jre` (Ubuntu). O `apt` instala:
  - display e GL: `xvfb`, `x11vnc`, `novnc`, `websockify`, `libgl1-mesa-dri`, `libglx-mesa0`, `libegl1`, `mesa-utils`;
  - libs do GLFW: `libxcursor1`, `libxrandr2`, `libxinerama1`, `libxi6`;
  - áudio: `libasound2t64`, `libasound2-plugins` (ponte ALSA→Pulse), `pulseaudio-utils`.
- Copia o jar para `/opt/doom-java/doom-java.jar` e `.iwads/*.wad` para `/opt/doom-java/iwads/`.
- Usuário não-root.
- **`docker/entrypoint.sh`:**
  - `DOOM_MODE=browser` (padrão): sobe `Xvfb :99` (geometria em `DOOM_GEOMETRY=1280x800`), `x11vnc -nopw -forever -shared`, `websockify --web /usr/share/novnc 6080 localhost:5900` (com redirect para `vnc.html?autoconnect=1&resize=scale`), e o jogo com `LIBGL_ALWAYS_SOFTWARE=1`, `-nosound` e os argumentos extras do `docker run`.
  - `DOOM_MODE=x11`: usa o `DISPLAY` do host, `/dev/dri` se houver, e o socket PulseAudio via `PULSE_SERVER` + `/etc/asound.conf` (ALSA→pulse).
  - Sinal de saída do jogo encerra o container.
- **Execução simples:**
  - Docker puro: `docker run --rm -p 6080:6080 ghcr.io/<owner>/doom-java`, depois abrir http://localhost:6080. Argumentos extras vão para o jogo, por exemplo `-iwad freedoom2.wad -warp 1`.
  - Com compose: `docker compose up`. O `docker-compose.yml` faz a build local, expõe a porta 6080 e monta um volume de saves/config.
  - `bin/doom-docker [--x11] [args…]`: monta o comando certo (no Linux com `--x11`, compartilha o socket X11 e o PulseAudio).
- `.dockerignore`: `target/`, `tools/oracle/work/`, `.claude/`.

### 4. Releases com doom1.wad e imagem pública
- **`release.yml`:**
  - copiar `.iwads/doom1.wad` para o app (`jpackage --input` → `app/iwads/doom1.wad`, que o passo 2 encontra);
  - publicar também um **zip portátil por SO**: imagem jlink + `iwads/doom1.wad` + launcher (`doom-java.sh`, `doom-java.command` ou `doom-java.bat`, com as flags certas), que roda com duplo clique e não precisa de Java instalado;
  - o fat jar vai junto com o doom1.wad num zip.
- **Novo `.github/workflows/docker.yml`:** buildx multi-arch (amd64 + arm64), push para `ghcr.io/${{ github.repository }}` em tags `v*` (`latest` + versão) e no `main` (`edge`).
- **`README.md`:** seção "Quick start" no topo com:
  - Docker (navegador e X11);
  - release portátil;
  - build a partir do código;
  - nota sobre as licenças dos IWADs.

### 5. Publicação no GitHub
O repositório foi criado pelo usuário: https://github.com/luiz-august0/doom-java-remake.

1. Depois que tudo passar localmente: `git remote add origin https://github.com/luiz-august0/doom-java-remake.git` e `git push -u origin main`.
   - O push leva o histórico completo e os ~79 MB de IWADs.
   - Credenciais: se o git pedir login, o usuário roda `! git push -u origin main` no prompt.
2. O CI (`ci.yml`) e o `docker.yml` (tag `edge`) rodam no push. Acompanho pelo GitHub e corrijo o que falhar no Linux/Windows.
3. Com o CI verde: `git tag v1.0.0 && git push origin v1.0.0`. Isso dispara a release pública e a imagem `ghcr.io/luiz-august0/doom-java-remake:1.0.0` e `:latest`.
   - A release terá zips portáteis por SO com `doom1.wad`, `.dmg`/`.msi`, o fat jar + `doom1.wad`.
   - O pacote do GHCR nasce privado. O usuário precisa torná-lo público em *Package settings*, e eu indico o link.

## Execução
Um subagente **Sonnet** implementa os passos 1–4 numa worktree e eu faço o merge. Arquivos principais:
- `Dockerfile`, `docker/entrypoint.sh`, `docker-compose.yml`, `.dockerignore`, `bin/doom-docker`
- `.gitignore`, `.iwads/README.md` + `COPYING` do Freedoom
- `doom-wad/.../IwadLocator.java` + teste
- `doom-app/pom.xml` (jpackage `--input`, zip portátil)
- `.github/workflows/{release,docker}.yml`
- `README.md`, `docs/spec/01-decisions.md`, `docs/contracts.md`

## Verificação
1. `./mvnw -o verify` continua verde (com e sem IWADs).
2. `docker build -t doom-java .` no Mac (arm64) termina sem erro.
3. `docker run --rm -p 6080:6080 doom-java`:
   - `curl` em `localhost:6080/vnc.html` responde 200;
   - capturar a tela virtual (`docker exec … xwd` → PNG) depois de ~10 s e conferir com a ferramenta Read que aparece o TITLEPIC;
   - rodar com `-warp 1 1` e capturar E1M1;
   - abrir `http://localhost:6080` no Chrome via DevTools MCP e tirar screenshot do noVNC conectado.
4. `docker run … -timedemo demo1` em modo navegador imprime FPS (prova o GL por software).
5. O modo X11 não pode ser testado aqui (o host é macOS). Fica documentado, e o script é validado com `bash -n`.
6. Release local: `-Pdist` gera o zip portátil macOS; descompactar e abrir com o launcher sem `-iwad` encontra o `doom1.wad` embutido; o `.dmg` reconstruído abre o jogo sem `-iwad`.
7. Workflows validados por parse do YAML. Só rodam quando o usuário fizer push no GitHub.
