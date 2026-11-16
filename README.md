# TradeVision AI — protótipo Android 0.2

## Implementado nesta etapa
- Pré-visualização da câmera traseira.
- Moldura verde sobre a câmera para selecionar a região do gráfico.
- Arrastar a moldura para posicionar e arrastar o canto inferior direito para redimensionar.
- Captura da imagem e recorte da área selecionada.
- Conversão aproximada das coordenadas da prévia para a foto, considerando o modo `FILL_CENTER`.
- Contador de ciclo de 60 segundos.

O recorte é salvo na pasta privada externa do app (`Android/data/com.tradevisionai/files/`), com nome `tradevision_chart_...jpg`.

## Ainda não implementado
- Correção de perspectiva por quatro pontos.
- Reconhecimento de candles/OCR.
- Cálculo de Price Action, tendência ou indicadores.
- Integração com modelo de IA ou backend.
- Sinais CALL/PUT/AGUARDAR.

Não há qualquer sinal simulado. A tela informa que a captura foi feita, sem fingir que houve análise.

## Como executar
1. Instale o Android Studio.
2. Extraia o ZIP e abra a pasta `TradeVisionAI_Android`.
3. Aguarde a sincronização Gradle.
4. Conecte um celular Android com depuração USB ativada ou use emulador com câmera.
5. Execute o app.

## Como testar o recorte
1. Abra um gráfico M1 no monitor.
2. Aponte o celular de frente para a tela, evitando reflexos.
3. Ajuste a moldura verde para incluir candles, escala de preço e tempo, sem incluir abas ou menus.
4. Toque em **CAPTURAR ÁREA DO GRÁFICO**.
5. O app informa o tamanho do recorte salvo.

## Próxima etapa técnica
A próxima versão deve incluir um fluxo de análise de imagem que valide qualidade (foco, brilho, perspectiva e legibilidade) e só depois envie a imagem recortada a um backend seguro. A chave do provedor de IA deve permanecer no servidor, nunca embutida no APK.

## Limitações e risco
A leitura por câmera tem atraso, reflexo, moiré e distorção de perspectiva. Em M1 e expiração de 1 minuto, isso pode comprometer completamente a leitura. Opções binárias envolvem risco elevado; use apenas em ambiente de demonstração enquanto valida o sistema. O aplicativo não acessa nem controla a IQ Option e não executa operações.
