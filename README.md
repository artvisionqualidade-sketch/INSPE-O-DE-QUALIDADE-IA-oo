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


## Melhorias de câmera e enquadramento

- A câmera oferece captura de foto para ensinar exemplos conformes e não conformes e gravação multiângulo/360° para treinamento e inspeção.
- O controle FLASH liga/desliga a lanterna quando o aparelho disponibiliza flash.
- A grade pode ser ativada/desativada e a ferramenta MEDIR permite calibrar uma escala com referência física conhecida.
- Foi acrescentado um retângulo verde de contorno estimado, calculado por contraste entre o produto e os cantos do fundo. É uma heurística de enquadramento, não um detector de objetos treinado e não garante que toda a peça esteja segmentada corretamente.
- A inspeção ao vivo e a análise de vídeo ficam em ATENÇÃO quando o contorno não é identificado com consistência. Isso reduz aprovações automáticas em capturas inadequadas, mas pode exigir nova captura com fundo contrastante e boa iluminação.
- As dimensões mostradas são estimativas dependentes de calibração, distância, perspectiva e plano da peça. Para cotas industriais críticas, confirme com instrumento calibrado.

O APK não foi compilado nem validado em aparelho nesta entrega. Execute o GitHub Actions do repositório para verificar a compilação e teste com amostras conhecidas antes de uso produtivo.

## Atualização: ensino de não conformidade e resultados simplificados

- No detalhe do produto, use **Criar padrão de NÃO CONFORMIDADE** para cadastrar um defeito, por exemplo Rebarba, Risco ou Trinca.
- Abra o defeito cadastrado para ensinar com foto da câmera, imagem da galeria/Google Fotos e gravação 360°.
- Grave a peça com defeito dando uma volta completa e mostrando diferentes ângulos; os quadros do vídeo são adicionados aos exemplos desse defeito.
- Os resultados foram simplificados para **“Produto não conforme — defeito identificado: Rebarba.”**, **“Produto conforme — sem avarias detectadas.”** ou **“Resultado inconclusivo — avaliar a peça.”**
- A classificação depende das amostras reais cadastradas e deve ser validada antes de uso industrial. A mensagem “conforme” significa que os defeitos ensinados não foram reconhecidos, não que seja impossível haver defeitos não cadastrados.
