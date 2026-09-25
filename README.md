# SkyBattle

A Paper plugin that recreates **MCC Island Sky Battle** with both of its modes,
**Solo** and **Quads**, on top of [MinigameLib](https://github.com/TalexCK/MinigameLib).

- Supported servers: **Paper 1.21.11 and 26.1 – 26.3** (one jar, Java 21+).
- Required plugins: **MinigameLib 0.2.0**, **TAB**. Optional: DecentHolograms (leaderboards).

Chinese documentation: [README.zh_CN.md](README.zh_CN.md).

## Gameplay

| | Solo | Quads |
|---|---|---|
| Players | 8, one per island | 8 teams of 4 (fills teams before opening new ones) |
| Kit | stone sword, bow, **4 arrows**, iron pickaxe, infinite team blocks, team leather armour, steak | same with **2 arrows** |
| Loot | `loot/solo/<tier>.yml` | `loot/quads/<tier>.yml` |
| Round | 5 minutes, round shrinking border | 5 minutes, round shrinking border |
| Score | 25 per kill, 5 per enemy outlived, placement bonus | 20 per kill, 2 per enemy outlived, team placement bonus |

- Colour-coded loot chests: common, uncommon, rare, epic, legendary (vanilla chest / copper chests).
- Special items: Timed Orb of Harming, Orb of Poison, Orb of Slowness, Orb of Cleansing,
  Spark of Levitation (≈13 blocks over 2.5 s), Spark of Regeneration, Spark of Speed.
- TNT ignites when placed, creepers from eggs, kill credit for knock-offs, TNT, creepers and orbs.
- Border: circle that shrinks in stages from the sides (optionally the top/bottom too).
  Standing outside hurts every second; falling into the void eliminates instantly.
- Players who leave mid-game are eliminated; rejoining puts them back as spectators.
- When the time runs out the surviving teams are ranked by players alive, then score.

All numbers (team size, player limits, timer, scores, kit) are configurable per mode in
`config.yml -> modes`.

## Playing

Players get a **game selector compass** in the lobby (or use `/skb`). Picking a mode joins its
queue; the game starts once enough players are waiting (shorter countdown when the queue is full).

In game: MCC-style sidebar (time, border status, players/teams alive, kills, score), a four
column tablist (team roster, live standings, personal stats), boss bar timer, kill/elimination
titles, victory fireworks and an end-of-game summary with your placement.

## Commands

| Command | Who | |
|---|---|---|
| `/skb` | everyone | opens the mode menu |
| `/skb join <solo\|quads>` / `/skb leave` | everyone | queue |
| `/skb stats [player]` | everyone | kills, wins, experience |
| `/skb list` | admin | maps, live games, queues (clickable) |
| `/skb start [map\|mode]` | admin | start now with the players in your world (admins can test alone) |
| `/skb forcestart <mode>` | admin | start a queue now |
| `/skb stop <id>` / `/skb destroy <id>` | admin | end a game |
| `/skb setup <mapId> <world> [solo\|quads]` | admin | map editor (below) |
| `/skb spawn` | admin | set lobby spawn + return point |
| `/skb board <kills\|wins\|experience> <create\|list\|delete> [id]` | admin | hologram leaderboards |
| `/skb reload` | admin | reload config, maps, loot and language |

Permissions: `skybattle.command.<sub>`; `join/leave/menu/stats/help` default to everyone,
everything else to ops. `skybattle.admin` grants all.

## Map setup

1. Put the template world in `<server>/arena/<world>`.
2. Run `/skb setup <mapId> <world> [solo|quads]`. A temporary copy is loaded and you get the
   marker axe (left click = mark). Every prompt has clickable **[Done] [Skip] [Undo] [Cancel]**
   buttons, a boss bar shows the progress and everything you placed glows with a label.
3. Steps: centre (or *block below me*) → border radius → optional rectangle border → optional
   height limits → spawns (quads: the 4 corners of each team's 3x3 platform; solo: one block per
   island; at least 2) → chests per tier → border stages (or *Default stages*).
4. The map is saved to `plugins/SkyBattle/arena/<mapId>.yml`; click the reload button.

Arena files can also be edited by hand:

```yaml
id: pagodas
display-name: "Pagodas"
mode: quads            # solo | quads
template-world: pagodas
center: {x: 0, y: 80, z: 0}
initial-border-radius: 120
# initial-boundary-wall: {x1: -100, x2: 100, z1: -100, z2: 100}   # square border instead of circle
vertical-boundary: {lower-y: -1, upper-y: -1}                      # -1 = disabled
boundary-stages:
  - {x-distance-from-center: 70, z-distance-from-center: 70, delay-seconds: 60, duration-seconds: 45}
team-spawns:
  - color: RED
    spawns: ["10.5,81,10.5", "12.5,81,10.5", "10.5,81,12.5", "12.5,81,12.5"]
commonchest: ["20,80,0"]
legendarychest: ["0,82,0"]
```

## Loot tables

`plugins/SkyBattle/loot/<mode>/<tier>.yml`. A chest picks `rolls` different variants by weight:

```yaml
rolls: 2
variants:
  - weight: 2
    items:
      - material: BOW
        enchantments: {power: 1}
      - material: ARROW
        amount: 4
  - weight: 1
    items:
      - alias: timed_orb_of_harming
```

Items accept `material`, `alias`, `amount`, `name`, `lore`, `enchantments` (Minecraft keys).
The same format is used for the kits in `config.yml`. Loot tables from 0.1.0
(`loot/<tier>.yml`) are no longer read; copy your changes into `loot/quads/`.

## Resource pack

The pack (orb and spark models) lives in [`resourcepack/pack`](resourcepack/pack) and is zipped
into the jar during the build. On start it is exported to
`plugins/SkyBattle/skybattle-resourcepack.zip` for hosting via `server.properties` or a CDN.
Set `resource-pack.enabled: true` to let MinigameLib serve it automatically (configure the port
in `plugins/MinigameLib/config.yml`). Without the pack the items fall back to vanilla looks.

Regenerate the textures with `python3 resourcepack/tools/generate_pack.py` (needs Pillow).

## Build

```bash
# in MinigameLib
mvn install
# here
mvn package                 # target/skybattle-0.2.0.jar
mvn -Ppaper-26 compile      # optional: check against the 26.x API
```
