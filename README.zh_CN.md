# SkyBattle

SkyBattle 是基于 Paper 1.21.11 和 MinigameLib 的 MCC Island Sky Battle
复刻插件。

可复用玩法逻辑尽量交给 MinigameLib：

- Arena 生命周期、运行世界、传送、倒计时、胜利判定
- 队伍无限混凝土方块
- Loot chest 生成和按权重抽取变体
- TNT 放下即点燃
- 投掷/自用特殊道具效果

英文文档见 [README.md](README.md)。

## 配置

总配置位于：

```text
plugins/SkyBattle/config.yml
```

每张地图一个 arena 模板：

```text
plugins/SkyBattle/arena/<arena>.yml
```

可以通过游戏内 setup 生成：

```text
/skb setup <arena> <worldName>
```

该命令会复制并加载服务器目录：

```text
arena/<worldName>
```

随后把执行玩家传送到临时 setup 世界，通过聊天提示和 MinigameLib 方块标记器完成：

- 标记 arena 中心点
- 输入初始边界半径
- 输入初始边界墙 `x1 x2 z1 z2`，或 `skip`
- 依次标记 8 队各 4 个出生点
- 依次标记 common / uncommon / rare / epic / legendary 箱子
- 每类箱子标完输入 `done`
- 输入边界阶段 `x距离 z距离 下边界Y 上边界Y 延迟秒 收缩秒`
- 旧格式 `x距离 z距离 延迟秒 收缩秒` 也可以使用
- 所有边界阶段输入完成后输入 `done`

完成后会保存：

```text
plugins/SkyBattle/arena/<arena>.yml
```

玩家会被传回原世界，临时 setup 世界会被删除。

上下边界可选。`-1` 表示不启用对应上下边界：

```yaml
vertical-boundary:
  lower-y: -1.0
  upper-y: -1.0
```

箱子坐标按 MCC Island 风格分为五级：

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

每个箱子会从 `variants` 中抽取一个变体。一个变体可以包含多件物品：

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

附魔物品通过 MinigameLib 的 `ArenaItemFactory` 构造。配置中使用 Minecraft
附魔 key，例如 `efficiency`、`knockback`、`quick_charge`。

## 命令

```text
/skb reload
/skb list
/skb start [arena]
/skb stop <arenaId>
/skb destroy <arenaId>
/skb setup <arena> <worldName>
/skb spawn
```

`/skb start [arena]` 会生成随机运行 arenaId，并拉取执行者当前世界的所有玩家直接开局。
如果省略 `[arena]`，SkyBattle 会从已加载的 arena 模板里随机选择一个。

## 材质包

资源包文件刻意不纳入 Git：

```text
src/main/resources/resourcepacks/
src/main/resources/resourcepack-src/
```

构建插件前，请在本地制作并放入：

```text
src/main/resources/resourcepacks/skybattle-items.zip
```

如果 jar 内没有这个 zip，SkyBattle 仍可启动，但药水球和火花不会显示为自定义外观。

`pack.mcmeta` 应使用：

```json
{
  "pack": {
    "pack_format": 75,
    "description": "SkyBattle Resource"
  }
}
```

插件会通过 MinigameLib 在玩家进服时下发资源包。下发配置位于 `config.yml`：

```yaml
resource-pack:
  required: true
  public-url-base: ""
```

如果服务器面向公网玩家，建议把 `public-url-base` 设置成公网可访问的 URL 前缀。
留空时 MinigameLib 会使用内置 HTTP 服务。

### Loot Chest 方块

SkyBattle 的箱子等级和方块对应关系：

- common：`CHEST`
- uncommon：`WAXED_COPPER_CHEST`
- rare：`WAXED_EXPOSED_COPPER_CHEST`
- epic：`WAXED_WEATHERED_COPPER_CHEST`
- legendary：`WAXED_OXIDIZED_COPPER_CHEST`

Loot chest 现在使用原版 Minecraft 方块材质。除非你明确想替换这些原版外观，
否则资源包里不要再放 chest entity texture 覆盖文件。

### 必需药水球和火花物品

药水球和火花投射物使用 MinigameLib 的 `ItemDisplay` 显示实体。请为这些
物品类型和 CustomModelData 提供模型/贴图：

```text
FIRE_CHARGE   custom_model_data=1001  瞬间伤害球
SLIME_BALL    custom_model_data=1002  快速中毒球
SNOWBALL      custom_model_data=1003  净化宝珠
FEATHER       custom_model_data=1004  飘浮火花
BLAZE_POWDER  custom_model_data=1005  生命恢复火花
```

期望的资源包条目：

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

## 构建

```bash
mvn package
```

插件 jar 会生成在 `target/` 目录下。
