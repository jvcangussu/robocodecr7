# CR7 - Robocode Advanced Robot

O **CR7** é um robô desenvolvido para o simulador de tanques **Robocode**. Ele foi projetado para enfrentar adversários de alto nível, utilizando algoritmos avançados de inteligência artificial e geometria computacional para otimizar o combate e a sobrevivência na arena.

## 🚀 Tecnologias e Técnicas Utilizadas

### 1. Movimentação: Wave Surfing
O robô utiliza **Wave Surfing**, atualmente considerada a técnica de movimentação mais eficaz no Robocode.
* **Detecção de Ondas:** Monitora as quedas de energia do adversário para detectar disparos e criar "ondas" virtuais.
* **Previsão de Posição:** Simula futuras posições, incluindo *Wall Smoothing* para evitar colisões, para calcular o risco de cada caminho.
* **Estatísticas de Perigo:** Aprende o padrão de tiro do inimigo durante a batalha, evitando os ângulos onde o adversário costuma acertar.

### 2. Sistema de Mira: GuessFactor Targeting (GFT)
A mira é baseada em dados estatísticos que mapeiam a probabilidade de onde o inimigo estará no futuro.
* **Segmentação Multidimensional:** O aprendizado é refinado através de diversos estados, incluindo:
    * Distância e Velocidade Lateral.
    * Aceleração do alvo.
    * Proximidade de paredes.
    * Tempo desde a última mudança de velocidade.
* **Virtual Waves:** O robô gera ondas de simulação a cada turno, permitindo que o sistema de mira aprenda continuamente, mesmo enquanto a arma está resfriando.

### 3. Gerenciamento de Energia
* Ajuste dinâmico da potência do tiro com base na distância do alvo e na energia restante de ambos os robôs, garantindo longevidade em duelos extensos.

## 🛠️ Estrutura do Código

- `CR7.java`: Classe principal contendo o loop de execução e tratadores de eventos.
- `GunWave`: Classe interna para gerenciar o aprendizado da mira e ondas virtuais.
- `EnemyWave`: Classe interna para rastrear os tiros disparados pelo oponente e realizar o surf.

---
*Desenvolvido como um projeto em Sistemas Inteligentes*
