# SkyBattle

基于 [MinigameLib](https://github.com/TalexCK/MinigameLib) 的 **MCC Island 空岛大战** 复刻插件，
包含 **单人（Solo）** 与 **四人组（Quads）** 两种模式。

- 支持服务端：**Paper 1.21.11 以及 26.1 – 26.3**（同一个 jar，Java 21+）。
- 依赖：**MinigameLib 0.2.0**、**TAB**；可选 DecentHolograms（全息榜单）。

英文文档见 [README.md](README.md)。

## 玩法

| | 单人 | 四人组 |
|---|---|---|
| 人数 | 8 人，每人一座岛 | 8 队 × 4 人（先填满队伍再开新队） |
| 初始装备 | 石剑、弓、**4 支箭**、铁镐、无限队伍方块、队伍皮革甲、牛排 | 同上，**2 支箭** |
| 战利品 | `loot/solo/<品质>.yml` | `loot/quads/<品质>.yml` |
| 回合 | 5 分钟，圆形边界分阶段收缩 | 同左 |
| 得分 | 击杀 25、每多活过一名敌人 5、名次奖励 | 击杀 20、每多活过一名敌人 2、队伍名次奖励 |

- 五种颜色补给箱：普通 / 优良 / 稀有 / 史诗 / 传说。
- 特殊道具：定时伤害宝珠、剧毒宝珠、迟缓宝珠、净化宝珠、飘浮火花（2.5 秒约 13 格）、
  生命恢复火花、迅捷火花。
- TNT 放下即点燃，苦力怕蛋、击落虚空/TNT/苦力怕/宝珠都会正确记录击杀。
- 边界：圆形，按阶段从四周收缩（可选上下边界）。边界外每秒掉血，掉入虚空立即淘汰。
- 游戏中退出视为淘汰，重新进入以旁观者身份回到对局。
- 时间到时，存活队伍按存活人数、再按得分排名。

所有数值（队伍人数、人数上下限、时长、得分、初始装备）都在 `config.yml -> modes` 中按模式配置。

## 游玩

大厅玩家会得到 **游戏选择指南针**（或输入 `/skb`），选择模式即加入队列；人数达到下限后开始倒计时，
队列满员时倒计时缩短。

游戏内：MCC 风格侧边栏（时间、边界状态、存活人数/队伍、击杀、得分）、四栏 TAB 列表（队伍名单、
实时排名、个人数据）、Boss 栏计时、击杀/淘汰标题、胜利烟花以及带个人名次的结算。

## 命令

| 命令 | 权限 | 说明 |
|---|---|---|
| `/skb` | 所有人 | 打开模式菜单 |
| `/skb join <solo\|quads>` / `/skb leave` | 所有人 | 排队 / 离开队列 |
| `/skb stats [玩家]` | 所有人 | 击杀、胜场、经验 |
| `/skb list` | 管理员 | 地图、进行中的对局、队列（可点击） |
| `/skb start [地图\|模式]` | 管理员 | 用当前世界的玩家立即开局（管理员可单人测试） |
| `/skb forcestart <模式>` | 管理员 | 立即开始某个队列 |
| `/skb stop <id>` / `/skb destroy <id>` | 管理员 | 结束对局 |
| `/skb setup <地图ID> <世界> [solo\|quads]` | 管理员 | 地图编辑器 |
| `/skb spawn` | 管理员 | 设置大厅出生点和返回点 |
| `/skb board <kills\|wins\|experience> <create\|list\|delete> [id]` | 管理员 | 全息榜单 |
| `/skb reload` | 管理员 | 重载配置、地图、战利品和语言 |

权限节点为 `skybattle.command.<子命令>`；`join/leave/menu/stats/help` 默认所有人可用，其余默认 OP。
`skybattle.admin` 拥有全部权限。

## 地图配置

1. 把模板世界放到 `<服务器>/arena/<世界名>`。
2. 执行 `/skb setup <地图ID> <世界名> [solo|quads]`。插件会加载一个临时副本并给你标记木斧
   （左键 = 标记）。每一步都有可点击的 **[完成] [跳过] [撤销] [取消]** 按钮，Boss 栏显示进度，
   已标记的点会发光并显示标签。
3. 步骤：中心（可用脚下方块）→ 边界半径 → 可选矩形边界 → 可选上下边界 → 出生点（四人组：每队
   3x3 平台的四个角；单人：每座岛一个方块；至少 2 个）→ 各品质补给箱 → 边界阶段（可一键默认）。
4. 保存到 `plugins/SkyBattle/arena/<地图ID>.yml`，点击聊天中的按钮执行 reload 即可使用。

也可以手动编辑地图文件，格式见英文文档示例（`mode: solo|quads`、`display-name` 为新增字段，
旧文件默认按 quads 读取）。

## 战利品

`plugins/SkyBattle/loot/<模式>/<品质>.yml`，每个箱子按权重抽取 `rolls` 个不同变体，一个变体可包含多个物品。
物品支持 `material`、`alias`、`amount`、`name`、`lore`、`enchantments`。`config.yml` 中的初始装备使用相同格式。
0.1.0 的 `loot/<品质>.yml` 不再读取，如有修改请复制到 `loot/quads/`。

## 资源包

资源包源文件位于 [`resourcepack/pack`](resourcepack/pack)，构建时自动打包进插件 jar；启动时导出到
`plugins/SkyBattle/skybattle-resourcepack.zip`，可用于 `server.properties` 或 CDN 分发。
把 `resource-pack.enabled` 设为 `true` 可由 MinigameLib 内置 HTTP 服务自动下发（端口在
`plugins/MinigameLib/config.yml` 配置）。不装资源包时道具显示为原版外观。

使用 `python3 resourcepack/tools/generate_pack.py`（需要 Pillow）可重新生成贴图。

## 构建

```bash
# 在 MinigameLib 仓库
mvn install
# 在本仓库
mvn package                 # target/skybattle-0.2.0.jar
mvn -Ppaper-26 compile      # 可选：用 26.x API 检查
```
