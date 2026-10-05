# Smooth / Soft Terrain v2 (Fabric 1.21.11)

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
