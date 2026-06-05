# SkyBattle

基于 Paper 1.21.11、MinigameLib 的 MCC Island Sky Battle 复刻插件。

玩法核心尽量交给 MinigameLib：

- Arena 生命周期、运行世界、传送、倒计时、胜利判定。
- 无限队伍混凝土方块。
- Loot chest 生成和按权重抽取。
- TNT 放下即点燃。
- 投掷/自用特殊道具效果。

## 配置

总配置位于：

```text
plugins/SkyBattle/config.yml
```

每张地图一个 arena 配置：

```text
plugins/SkyBattle/arena/<arenaId>.yml
```

箱子坐标按 MCC Island 分为五级：

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

Loot table 位于：

```text
plugins/SkyBattle/loot/common.yml
plugins/SkyBattle/loot/uncommon.yml
plugins/SkyBattle/loot/rare.yml
plugins/SkyBattle/loot/epic.yml
plugins/SkyBattle/loot/legendary.yml
```

每个箱子会从 `variants` 中抽中一个变体。一个变体可以包含多件物品：

```yaml
variants:
  - weight: 1.0
    items:
      - name: 冲击 I 弓
        material: BOW
        enchantments:
          punch: 1
      - name: TNT
        material: TNT
        amount: 2
```

附魔物品通过 MinigameLib 的 `ArenaItemFactory` 构造，配置中使用 Minecraft
附魔 key，例如 `efficiency`、`knockback`、`quick_charge`。

## 构建

```bash
mvn package
```

插件 jar 会生成在 `target/` 目录下。
