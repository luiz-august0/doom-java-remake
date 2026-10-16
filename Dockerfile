# doom-java in a container. Two modes, chosen with DOOM_MODE (see docker/entrypoint.sh):
#   browser (default)  Xvfb + Mesa software OpenGL + x11vnc + noVNC on http://localhost:6080, no sound
#   x11                the host's X server and PulseAudio (Linux hosts)
#
#   docker build -t doom-java .
#   docker run --rm -p 6080:6080 doom-java [game options]

# ---- Stage 1: build the fat jar (LWJGL natives of the architecture this image is built for) ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY . .
# No jpackage installer and no portable zips are needed here, only the fat jar.
RUN ./mvnw -B -ntp -Pdist -pl doom-app -am package -DskipTests -Dexec.skip=true -Ddist.portable.skip=true \
 && cp doom-app/target/doom-app-*-all.jar /doom-java.jar

# ---- Stage 2: runtime ----
FROM eclipse-temurin:25-jre
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update \
 && apt-get install -y --no-install-recommends \
      xvfb x11vnc novnc websockify \
      libgl1-mesa-dri libglx-mesa0 libegl1 libgl1 mesa-utils \
      libxcursor1 libxrandr2 libxinerama1 libxi6 libxxf86vm1 \
      libasound2t64 libasound2-plugins pulseaudio-utils \
 && rm -rf /var/lib/apt/lists/* \
 && printf '<!doctype html><meta charset="utf-8"><title>doom-java</title>\n<meta http-equiv="refresh" content="0; url=vnc.html?autoconnect=1&resize=scale">\n' \
      > /usr/share/novnc/index.html

# The game, its free IWADs (found by IwadLocator through /opt/doom-java/iwads) and the entrypoint.
COPY --from=build /doom-java.jar /opt/doom-java/doom-java.jar
COPY .iwads/*.wad /opt/doom-java/iwads/
COPY .iwads/README.md .iwads/COPYING.txt /opt/doom-java/iwads/
COPY docker/entrypoint.sh /usr/local/bin/doom-entrypoint
RUN chmod 755 /usr/local/bin/doom-entrypoint \
 && useradd --create-home --uid 10001 --shell /bin/bash doom \
 && mkdir -p /home/doom/.config/doom-java /tmp/.X11-unix \
 && chown -R doom:doom /home/doom \
 && chmod 1777 /tmp/.X11-unix

USER doom
WORKDIR /home/doom
VOLUME /home/doom/.config/doom-java
EXPOSE 6080
ENTRYPOINT ["doom-entrypoint"]
