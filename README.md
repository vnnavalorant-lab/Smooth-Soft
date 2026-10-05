# Smooth / Soft Terrain (Fabric 1.21.11)

Suaviza seletivamente o relevo **durante a geração do chunk** (etapa NOISE), antes de
surface builder, carvers, features e estruturas dependentes de terreno.

## Como funciona
1. Mixin em `NoiseChunkGenerator.populateNoise(...)` (RETURN) → `WorldGenerationHook`.
2. `HeightmapSampler` pergunta ao próprio gerador a altura vanilla de nós a cada 4 blocos
   (grade **global**, com cache). Isso não depende de chunks vizinhos já existirem, e dois chunks
   vizinhos calculam exatamente os mesmos valores na borda → sem emenda entre chunks.
3. `TerrainAnalyzer` (3x3 / 5x5 / janela `radius`): picos isolados (mediana), densidade de ruído,
   penhascos coerentes, picos largos proeminentes, ravinas.
4. `TerrainSmoother` calcula alvo e peso por nó (0–10% / 10–30% / 30–50% em NATURAL), interpola
   bilinearmente por coluna e sobe/desce no máximo `max_height_change` blocos.
5. Proteções: `StructureProtection` (structure starts do chunk + 8 vizinhos, margem de 12 blocos,
   vale p/ estruturas de mods), `BiomeProtection` (só reduz intensidade, nunca troca bioma),
   `TerrainProtection` (cavernas, aquíferos, água), nível do mar (+2) intocado.
6. Qualquer exceção ou dúvida → chunk fica 100% vanilla.

Só mundos com `minecraft:overworld` / `minecraft:large_biomes` são tocados
(`allowed_generator_settings`); Nether, End e dimensões custom ficam intactos.

## Config: `config/smoothsoftterrain.json`
`level`: OFF | SUBTLE | NATURAL (padrão) | STRONG | CUSTOM. `strength` e `radius` só valem em CUSTOM
(NATURAL = 0.35 / 5). Demais chaves: `preserve_*`, `chunk_blending`, `only_new_chunks`,
`max_height_change`, `modded_biome_factor`, `biome_factors`.

## Build
Use o wrapper Gradle do projeto-exemplo oficial do Fabric (Gradle compatível com Loom 1.14), Java 21:
`./gradlew build` → `build/libs/smoothsoftterrain-1.0.0.jar`

## Limitações
- Amostragem a cada 4 blocos: picos mais finos que ~4 blocos são suavizados pelo peso, não detectados individualmente.
- Features e superfície rodam depois, então grama/árvores/ores acompanham o novo relevo.
- Outros mods que substituem `populateNoise` (ex.: C2ME) podem exigir ajuste do mixin.
