# Smooth / Soft Terrain v3 (Fabric 1.21.11)

Filtro de terraformagem aplicado **durante a geração do chunk**, no heightmap (não em blocos soltos).

## Pipeline
```
Heightmap original (grade global a cada 4 blocos, chunk + 48 blocos de margem)
  -> TerrainAnalyzer : 4 escalas gaussianas (micro 3 / local 8 / regional 24 / macro 40 blocos),
                       slope, curvatura, irregularidade
  -> TerrainSmoother : R = Macro + (Reg-Macro) + (Loc-Reg) + (Mic-Loc) + (R-Mic); cada banda é atenuada
                       conforme a necessidade (bandas grandes quase intactas => altura/média/forma preservadas;
                       ruído fino retido em parte = "detail restoration"; picos não descem mais que ~12% da proeminência)
  -> Blend           : Final = lerp(Original, Smooth + detail_keep*(micro), S)   S: 0.15-0.40 (suave) .. 0.70+ (quebrado)
  -> TerrainRemodeler: sobe/desce cada coluna levando a "pele" (grama/dirt/areia) junto
  -> SurfaceValidationPass: DIRT natural no topo da coluna com ar acima -> GRASS_BLOCK (MYCELIUM em mushroom_fields)
```
Por que depois do surface builder (e não no NOISE como na v1): as regras de superfície do vanilla usam uma
estimativa de altura do ruído original; terreno rebaixado ficaria sem grama. Carregando a pele junto, os
terraços novos continuam com a cobertura certa do bioma. Carvers, features, árvores, ores e estruturas ainda
rodam depois e enxergam o terreno final.

Sem emenda entre chunks: os nós da grade são globais e a margem (≥ meia-largura da maior gaussiana + 2 nós)
faz dois chunks vizinhos calcularem valores idênticos nas bordas (verificado: erro 0.0; com margem curta
dá erro > 1 bloco).

## Proteções (só onde há algo a proteger)
- Estruturas: caixa de cada **peça** + 6 blocos de rampa (vilas não bloqueiam mais o terreno vazio ao redor).
- Água: colunas com topo ≤ nível do mar + 2 intocadas; novo topo nunca abaixo de mar + 2.
- Cavernas: o corte para quando restam 3 blocos de teto; só sobe sobre ar; nunca preenche água.
- Biomas: nunca trocados; picos/badlands têm intensidade reduzida (config `biome_factors`).
- Só `minecraft:overworld` / `large_biomes` (config `allowed_generator_settings`).
- Qualquer erro => chunk vanilla.

## Modos (`mode`)
SUBTLE · NATURAL · **STRONG_NATURAL (padrão)** · VERY_SMOOTH · CUSTOM (usa strength/radius/regional_radius/macro_radius/detail_keep) · OFF.
Config antiga (v1) é substituída automaticamente. `debug_visualization: true` imprime por chunk altura média
original/suavizada, diferença média/máxima, força e colunas modificadas.

## Verificação feita (terreno sintético, sem Minecraft) — `./gradlew test`
STRONG_NATURAL, 3 seeds: 58–77% das colunas alteradas ≥1 bloco, rugosidade −28% a −37%, altura média ±0,1,
pico mais alto −3% a −4%, degrau na borda de chunk −20% a −35%, emenda = 0. VERY_SMOOTH chega a −50% de rugosidade.
**Teste A/B no jogo (você precisa rodar):** mesma seed, mundo A com `"mode":"OFF"`, mundo B com STRONG_NATURAL,
voar em Spectator e ligar `debug_visualization`. Se ainda estiver fraco, use VERY_SMOOTH ou CUSTOM (strength 0.85+).

## Pontos a conferir no 1º build (nomes Yarn)
`NoiseChunkGenerator.buildSurface(ChunkRegion, StructureAccessor, NoiseConfig, Chunk)` (descritor do mixin),
`Blender.getBlender(ChunkRegion)`/`getNoBlending()`, `StructureAccessor.getStructureStarts(ChunkPos, Predicate)`,
`StructureStart.getChildren()`, `Chunk.getBiomeForNoiseGen`, `Heightmap.trackUpdate`. `./gradlew genSources` mostra os reais.


---
## v3 — terreno contínuo, andável e sem buracos (modo padrão: SOFT_WORLD)

| Regra pedida | Como o mod faz | Config |
|---|---|---|
| Inclinações suaves / sem quedas de 1-5 blocos | Limitador de slope (erosão térmica) no campo suavizado, 6 iterações; onde o terreno original passa do limite o resultado segue o campo suavizado. Penhascos longos e coerentes toleram ~3x mais | `max_slope` (0.7) |
| Sem poços / depressões súbitas | Colunas bem abaixo da superfície suave ao redor (2-8 blocos) são preenchidas; depressões muito fundas (>~12) são tratadas como abertura intencional | `fill_pits` |
| Terreno sempre apoiado, sem fragmentos flutuantes | Se o topo sólido tem < N blocos e há vazio de AR logo abaixo, o vazio é preenchido de cima p/ baixo (cavernas grandes ficam, com teto mais grosso) | `min_surface_support` (5) |
| Sem buracos de caverna "sem sentido" na superfície | Depois dos carvers: buracos pequenos (<= 36 colunas, sem tocar a borda do chunk) são fechados com terreno + cobertura do vizinho. Bocas grandes e ravinas ficam | `seal_surface_holes`, `max_sealed_hole_area` |
| Ruído de baixa frequência | Bandas finas (micro/ruído) quase removidas; macro/regional preservadas | `mode` |

Modos: SUBTLE · NATURAL · STRONG_NATURAL · VERY_SMOOTH · **SOFT_WORLD** (padrão) · CUSTOM · OFF.
Configs antigas (v1/v2) são substituídas automaticamente.

Medido no terreno sintético (SOFT_WORLD, 3 seeds): degraus >=2 blocos 10-20% -> <2% dos pares de colunas,
degraus >=3 blocos ~4% -> ~0,1%, rugosidade -46% a -62%, altura média +-0,4, pico mais alto -2% a -8%, emenda 0.

Limitações honestas: rios/oceanos/lagos não são remodelados (fundo de rio continua vanilla; margens
suavizam só do lado terra); terreno a < 2 blocos do nível do mar fica intocado; a selagem pós-carver
usa o tamanho do buraco dentro do chunk (buracos que cruzam a borda são mantidos); biomas de pico/badlands
têm intensidade reduzida (`biome_factors`).

Mixin da selagem: `ChunkGenerator.generateFeatures(...)` em HEAD (logo após os carvers). Um TAIL em `carve` causou crash no 1.21.11 (sem RETURN para ancorar).

---
## Como compilar
- **GitHub Actions:** `.github/workflows/build.yml` ja esta incluido (instala o Gradle no runner; nao precisa de `gradlew`).
  O jar fica em Actions -> execucao -> Artifacts.
- **Local:** Java 21 + Gradle instalado -> `gradle build` -> `build/libs/smoothsoftterrain-1.0.0.jar`.
  (Opcional: `gradle wrapper` cria o gradlew.)
- **Testes do algoritmo:** `gradle test`.


---
## v3.3 — terraços como na imagem de referência

A referência mostra uma encosta de **degraus de 1 bloco de altura com patamares de grama de ~4-6 blocos de largura**,
contornos curvos acompanhando o morro e nenhum degrau de 2+ blocos. O mod agora mira exatamente isso:

- `terrace_step` = **1** (degrau de 1 bloco). Nas zonas de terraço o resultado segue o campo suavizado quase por inteiro
  e sem ruído fino, então o arredondamento da altura gera linhas de contorno limpas e patamares planos.
- `terrace_tread_width` = 5: a inclinação alvo nas zonas de terraço é `step / largura` (0,2); o limitador de slope
  (12 iterações, relaxação estável) puxa as encostas para perto disso, sem forçar montanhas íngremes nem cliffs longos.
- Zonas de terraço: encostas suaves (nem planas nem íngremes), longe da costa, em manchas de baixa frequência (o resto
  fica rampa contínua). Desertos/badlands/praias recebem 30% do efeito.
- `terrace_step` maior (2-4) dá patamares mais altos; `terrace_enabled:false` desliga tudo.
- Config antiga substituída automaticamente (versão 5). Só chunks NOVOS são alterados.

Medido (terreno sintético, SOFT_WORLD): degraus de 2+ blocos 10-20% -> ~1%; degraus de 3+ blocos ~0-0,4%;
patamar médio 1,8-2,3 -> 2,9-3,3 blocos; rugosidade -48% a -65%; altura média preservada; emenda entre chunks = 0.
Nota: um patamar de 1 bloco ainda exige um pulo a cada degrau (a própria referência é assim); o mod só os torna
largos, regulares e sem quedas maiores.

## v3.2 — terraços largos e suaves

- Nós de encosta suave (nem plano nem íngreme), longe da costa e dentro de manchas de baixa frequência
  recebem **terraço**: a altura vira patamares planos (múltiplos de `terrace_step`, padrão 4) ligados por rampas curtas.
  O degrau é aplicado **por bloco** (patamares realmente planos) e os contornos são deslocados por ruído de mundo
  (irregulares, larguras variáveis). Trechos fora das manchas ficam como rampa contínua.
- Desertos/badlands/praias recebem só 30% do efeito (dunas suaves, sem "degraus de grama"). Planícies quase planas e
  montanhas íngremes não são terraceadas.
- Config: `terrace_enabled` (todos os modos), `terrace_step`, `terrace_strength` (esses dois só em CUSTOM).
- Medido (terreno sintético, SOFT_WORLD): colunas de encosta em patamar 24% -> 39-49%; degraus de 3+ blocos ~0,1-0,4%;
  rugosidade -46% a -57%; altura média preservada; emenda entre chunks = 0.

**Mundos existentes:** só chunks NOVOS são alterados (chunks já gerados nunca são tocados). Para ver o efeito inteiro, crie um mundo novo.
**Abordagem:** o filtro age no heightmap durante a geração do chunk (logo após o surface builder), não na função de densidade
do NoiseRouter. Reescrever a densidade exige substituir JSONs/density functions do vanilla e não pode ser validado sem rodar o jogo.
