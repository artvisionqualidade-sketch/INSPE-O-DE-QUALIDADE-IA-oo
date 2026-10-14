# Inspeção com Inteligência Artificial — projeto Android

## Recursos incluídos nesta versão do projeto

- Captura de fotos de padrão aprovado e de exemplos de defeitos.
- Treinamento por vídeo 360° do padrão conforme e de cada defeito configurado.
- Controles da câmera para ligar/desligar o flash quando o aparelho disponibiliza flash.
- Grade visual na prévia da câmera para orientar o enquadramento e dividir a superfície em regiões de referência.
- Ferramenta manual de medição aproximada, calibrada com uma dimensão física conhecida.
- Inspeção por foto, vídeo 360° e análise ao vivo.
- Regra conservadora: se faltarem referências do padrão ou exemplos para algum defeito cadastrado, o resultado será **ATENÇÃO**, e não aprovação automática.

## Como gerar o APK pelo GitHub

1. Envie o conteúdo desta pasta para o repositório GitHub, preservando `.github/workflows/android.yml`.
2. Abra **Actions** e execute **Gerar APK INSPETOR IA**; também é acionado ao enviar alterações para a branch `main`.
3. Quando terminar com sucesso, abra a execução e baixe o artefato `Inspecao-com-Inteligencia-Artificial-APK`.

## Limites técnicos importantes

A grade é uma sobreposição visual de referência; ela não representa, por si só, segmentação automática do produto nem localização garantida de rebarbas. O classificador atual compara características visuais simples com exemplos cadastrados, não é um modelo industrial treinado/validado. A medição em milímetros é uma estimativa manual que exige referência conhecida no mesmo plano e distância; dimensões críticas devem ser confirmadas com instrumentos calibrados. Valide com um conjunto real de peças boas e defeituosas antes de usar para decisões de produção.
