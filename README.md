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

可以通过游戏内 setup 生成：

```text
/sb setup <arena> <worldName>
```

该命令会复制并加载服务器目录：

```text
arena/<worldName>
```

随后把执行玩家传送到临时 setup 世界，通过聊天提示和 MinigameLib 的木斧标记器完成：

- 标记 arena 中心点。
- 输入初始边界半径。
- 输入初始边界墙 `x1 x2 z1 z2`，或 `skip`。
- 依次标记 8 队各 4 个出生点。
- 依次标记 common / uncommon / rare / epic / legendary 箱子，输入 `done` 进入下一类。
- 输入边界阶段 `x距离 z距离 延迟秒 收缩秒`，全部完成后输入 `done`。

完成后会保存 `plugins/SkyBattle/arena/<arena>.yml`，传回原世界，并删除临时 setup 世界。

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

## 命令

```text
/sb reload
/sb list
/sb start <arena>
/sb stop <arenaId>
/sb destroy <arenaId>
/sb setup <arena> <worldName>
```

`/sb start <arena>` 会使用 `<arena>` 模板生成随机运行 arenaId，并拉取执行者所在世界的所有玩家直接开局。

## 材质包

材质包不纳入 Git。需要使用者自行补充：

```text
src/main/resources/resourcepacks/skybattle-lootchests.zip
```

构建后插件会通过 MinigameLib 下发该资源包；如果 jar 内没有这个文件，SkyBattle 会自动禁用资源包下发，不影响游戏启动。

服务端会给每级 loot chest 设置稳定 CustomName：

```text
SkyBattle Common Chest
SkyBattle Uncommon Chest
SkyBattle Rare Chest
SkyBattle Epic Chest
SkyBattle Legendary Chest
```

原版客户端不能仅靠普通资源包按已放置箱子的 block entity 名称切换不同箱子外观。实际按名称替换箱子实体模型通常需要 OptiFine Custom Entity Models 或 Entity Texture Features 这类客户端能力。当前资源包先提供稳定信号和占位结构，后续可以补真实 CEM/ETF 规则和贴图。

## 构建

```bash
mvn package
```

插件 jar 会生成在 `target/` 目录下。
