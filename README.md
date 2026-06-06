# SkyBattle

SkyBattle is a Paper 1.21.11 plugin that recreates the MCC Island Sky Battle
game mode on top of MinigameLib.

Most reusable game logic is delegated to MinigameLib:

- arena lifecycle, runtime worlds, teleporting, countdowns and win checks
- per-team infinite concrete blocks
- loot chest filling and weighted variant selection
- instant TNT ignition after placement
- throwable and self-cast special item effects

Chinese documentation is available in [README.zh_CN.md](README.zh_CN.md).

## Configuration

Global configuration is stored at:

```text
plugins/SkyBattle/config.yml
```

Each map has one arena template:

```text
plugins/SkyBattle/arena/<arena>.yml
```

An arena can be created in game with:

```text
/skb setup <arena> <worldName>
```

The setup command copies and loads this server directory:

```text
arena/<worldName>
```

The executing player is moved into a temporary setup world. The setup flow uses
chat prompts plus the MinigameLib block marker tool:

- mark the arena center
- enter the initial boundary radius
- enter the initial boundary wall as `x1 x2 z1 z2`, or `skip`
- mark 4 spawn points for each of the 8 teams
- mark common / uncommon / rare / epic / legendary loot chests
- type `done` after each chest tier
- enter boundary stages as `xDistance zDistance lowerY upperY delaySeconds shrinkSeconds`
- old stage format `xDistance zDistance delaySeconds shrinkSeconds` is also accepted
- type `done` after all boundary stages

When setup is complete, the plugin saves:

```text
plugins/SkyBattle/arena/<arena>.yml
```

The player is returned to the original world and the temporary setup world is
deleted.

Vertical boundaries are optional. `-1` disables a side of the vertical boundary:

```yaml
vertical-boundary:
  lower-y: -1.0
  upper-y: -1.0
```

Loot chest points are grouped by MCC Island-style tiers:

```yaml
commonchest:
  - "78,82,0"
uncommonchest:
  - "45,82,0"
rarechest:
  - "28,82,0"
epicchest:
  - "18,82,18"
legendarychest:
  - "0,82,0"
```

Loot tables are stored at:

```text
plugins/SkyBattle/loot/common.yml
plugins/SkyBattle/loot/uncommon.yml
plugins/SkyBattle/loot/rare.yml
plugins/SkyBattle/loot/epic.yml
plugins/SkyBattle/loot/legendary.yml
```

Each chest rolls exactly one variant from `variants`. A variant can contain
multiple items:

```yaml
variants:
  - weight: 1.0
    items:
      - name: Punch I Bow
        material: BOW
        enchantments:
          punch: 1
      - name: TNT
        material: TNT
        amount: 2
```

Enchanted items are built through MinigameLib's `ArenaItemFactory`. Use
Minecraft enchantment keys such as `efficiency`, `knockback` and `quick_charge`.

## Commands

```text
/skb reload
/skb list
/skb start [arena]
/skb stop <arenaId>
/skb destroy <arenaId>
/skb setup <arena> <worldName>
/skb spawn
```

`/skb start [arena]` creates a random runtime arena id and starts the game with
all players in the command sender's current world. If `[arena]` is omitted,
SkyBattle randomly selects one loaded arena template.

## Resource Pack

Resource pack files are intentionally ignored by Git:

```text
src/main/resources/resourcepacks/
src/main/resources/resourcepack-src/
```

Before packaging the plugin, create this file locally:

```text
src/main/resources/resourcepacks/skybattle-items.zip
```

If the jar does not contain this zip, SkyBattle can still start, but custom
orbs and sparks will not render.

`pack.mcmeta` should use:

```json
{
  "pack": {
    "pack_format": 75,
    "description": "SkyBattle Resource"
  }
}
```

The plugin sends the resource pack on player join through MinigameLib. Runtime
delivery can be configured in `config.yml`:

```yaml
resource-pack:
  required: true
  public-url-base: ""
```

If the server is public, set `public-url-base` to a publicly reachable URL
prefix. If it is empty, MinigameLib uses its built-in HTTP server.

### Loot Chest Blocks

SkyBattle maps tiers to chest block types as follows:

- common: `CHEST`
- uncommon: `WAXED_COPPER_CHEST`
- rare: `WAXED_EXPOSED_COPPER_CHEST`
- epic: `WAXED_WEATHERED_COPPER_CHEST`
- legendary: `WAXED_OXIDIZED_COPPER_CHEST`

Loot chests use vanilla Minecraft block textures. Do not include chest entity
texture overrides in the SkyBattle resource pack unless you intentionally want
to replace these vanilla looks.

### Required Orb And Spark Items

Potion orbs and sparks use MinigameLib `ItemDisplay` projectiles. Provide item
models/textures for these material and custom model data pairs:

```text
FIRE_CHARGE   custom_model_data=1001  Timed Orb of Harming
SLIME_BALL    custom_model_data=1002  Quick Timed Orb of Poison
SNOWBALL      custom_model_data=1003  Orb of Cleansing
FEATHER       custom_model_data=1004  Spark of Levitation
BLAZE_POWDER  custom_model_data=1005  Spark of Regeneration
```

Expected resource pack entries:

```text
assets/minecraft/models/item/fire_charge.json
assets/minecraft/models/item/slime_ball.json
assets/minecraft/models/item/snowball.json
assets/minecraft/models/item/feather.json
assets/minecraft/models/item/blaze_powder.json
assets/skybattle/models/item/timed_orb_of_harming.json
assets/skybattle/models/item/quick_timed_orb_of_poison.json
assets/skybattle/models/item/orb_of_cleansing.json
assets/skybattle/models/item/spark_of_levitation.json
assets/skybattle/models/item/spark_of_regeneration.json
assets/skybattle/textures/item/timed_orb_of_harming.png
assets/skybattle/textures/item/quick_timed_orb_of_poison.png
assets/skybattle/textures/item/orb_of_cleansing.png
assets/skybattle/textures/item/spark_of_levitation.png
assets/skybattle/textures/item/spark_of_regeneration.png
```

## Build

```bash
mvn package
```

The plugin jar is written to `target/`.
