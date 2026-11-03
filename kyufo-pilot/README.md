# AERIS Pilot — controle Android para drones da família KY UFO

**Código de controle real, não simulado; compatibilidade física não garantida.** Aplicativo Java Android com varredura Wi-Fi real, solicitação de conexão pelo Android, vínculo de sockets à rede Wi-Fi escolhida, transmissão UDP 20–25 Hz, heartbeat, joystick de quatro eixos, resposta UDP, parada de emergência repetida, câmera RTSP via LibVLC, avisos sonoros e narração TTS.

## Protocolos implementados

- **KY UFO / RTSP**: gateway 192.168.1.1; UDP 7099, heartbeat 01 01; controle em pacote com prefixo 03, 66 14, quatro eixos, flags/XOR, 99; stream rtsp://192.168.1.1:7070/webcam; troca de câmera 06 01 / 06 02.
- **FLOW-WIFI**: gateway 192.168.169.1; UDP 8800; frame 88 bytes EF 02 + sequência + frame 66 14 + XOR; heartbeat adicional 7099. Comando 01 para decolar/pousar e 02 para emergência documentados com testes de hardware. **O vídeo proprietário fragmentado 8800 não foi implementado.**
- Variantes diferentes, como TC, Jieli e FLOW/HD com diferentes firmwares, podem NÃO funcionar. É necessário identificar SSID/gateway/modelo e comparar capturas do app oficial; nunca testar com hélices antes da validação em bancada.

## Segurança, limitações e transparência

- **Piloto assistido NÃO é uma IA treinada ou voo autônomo.** O modo experimental narra ações, neutraliza joysticks e verifica a chegada de respostas reais. Ele não reconhece obstáculos, não navega, não garante altitude e não decola sozinho. Uma IA verdadeira de voo seguro precisaria de sensores, visão computacional validada, modelo compatível e testes.
- Um ACK UDP confirma recebimento de dados do dispositivo, **não** confirma que um comando de movimento foi executado. A telemetria exibida relata somente os dados realmente observados.
- O botão de parada envia flags compatíveis com perfis documentados. Perda de Wi-Fi impede garantir que o drone recebeu o comando e não substitui um controle físico; se acionada em voo, a parada pode causar queda.
- Para redes protegidas, inserir a senha real. Android 10+ exige confirmação de conexão ao Wi-Fi local; a varredura requer localização ativada.
- O botão decolar/pousar só é habilitado para FLOW-WIFI, pois é um comando documentado no perfil. O KY 7099 não usa o mesmo comando de decolagem de forma uniforme.
- É preciso um voo de bancada, sem hélices, comparando comandos com o app original antes de permitir decolagem real.

## Compilação no GitHub

GitHub Actions compila com JDK 17, Android Gradle Plugin 8.7.3, compileSdk 35, Gradle 8.9, LibVLC 3.6.5. O workflow está em .github/workflows/aeris-drone-apk.yml e publica o APK diretamente em **Releases**, evitando ZIP de artefatos.

## Referências técnicas independentes

- https://buzzly.harislab.tech/t/reverse-engineering-the-ky-ufo-drone-protocol-full-wifi-control-camera-rtsp/285
- https://github.com/popolony2k/maritaca-e88-controller/blob/main/CLAUDE.md
- https://github.com/hakimjanov/pyDroneWire
- https://github.com/OpenIPC/decoder/issues/196
- https://central.sonatype.com/artifact/org.videolan.android/libvlc-all
