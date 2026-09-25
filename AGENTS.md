# SkyBattle / MinigameLib 开发备忘

## 项目定位

- 本仓库是 SkyBattle 插件（MCC Island Sky Battle 复刻，含 Solo / Quads 两种模式），Java 21，Maven 构建。
- 支持 Paper 1.21.11 与 26.1 - 26.3：默认按 1.21.11 API 编译（最低版本），`-Ppaper-26` 可用 26.x API 检查。不要使用 26.x 才有的 API。
- 玩法公共能力优先走 MinigameLib，不要在 SkyBattle 里重复实现 arena 生命周期、临时世界、队伍、箱子、边界、TAB/计分板等通用逻辑。
- MinigameLib 本地仓库路径通常是 `/Users/alextang/Projects/minigamelib`。
- SkyBattle 依赖 `com.talexck.minigamelib:minigamelib:0.2.0`，scope 是 `provided`，运行时必须安装并加载 MinigameLib 插件。
- 两个仓库会一起开发。跨仓库修改时要先确认 API 边界，再按 MinigameLib -> SkyBattle 的顺序构建验证。

## MinigameLib 项目定位

- MinigameLib 仓库：`/Users/alextang/Projects/minigamelib`。
- Maven 坐标：`com.talexck.minigamelib:minigamelib:0.2.0`。
- 插件入口：`com.talexck.minigamelib.MinigameLibPlugin`。
- 插件描述文件：`src/main/resources/plugin.yml`，不是 `paper-plugin.yml`。
- 运行时强依赖 TAB 插件，`plugin.yml` 中 `depend: TAB`；DecentHolograms 为 softdepend，只通过 `DecentHologramsBridge` 调用。
- 对外入口通过 Bukkit Services 注册：

  ```java
  MinigameLibrary lib = Bukkit.getServicesManager().load(MinigameLibrary.class);
  ArenaService arenas = lib.arenas();
  SetupService setup = lib.setup();
  LobbyService lobby = lib.lobby();
  QueueService queues = lib.queues();
  ```

## MinigameLib 代码分层

- 对外 API 放在 `com.talexck.minigamelib.api.*`，尽量保持稳定、语义清晰。
- 核心实现放在 `com.talexck.minigamelib.core.*`，不要让外部插件依赖 core 包。
- 主要 API：
  - `api.MinigameLibrary`
  - `api.arena.ArenaService`
  - `api.setup.SetupService`
  - `api.lobby.LobbyService`
  - `api.queue.QueueService`（大厅排队 / 倒计时 / action bar 状态）
  - `api.stats.StatsService`
- arena 相关实现已经拆服务，优先在对应服务内改，不要把逻辑重新塞回巨型控制器：
  - `ArenaController`：生命周期编排和服务组装
  - `RuntimeArena`：运行态数据和统计
  - `TeamDistribution`：队伍分配
  - `BoundaryService` / `BoundaryMath`：水平和上下边界、粒子、边界伤害
  - `SpawnCageService` / `SpawnGeometry`：出生笼和 3x3 几何校验
  - `CombatService`：伤害、死亡、击杀归属、队友伤害规则
  - `ItemService`：初始物品、无限方块、TNT 放下即点燃、装备染色
  - `ItemCombatService`：药水球、火花、显示实体投射物
  - `DisplayService` / `TabDisplayService`：聊天、title、bossbar、scoreboard、TAB 展示
  - `PlayerEnvironmentService`：大厅/arena 玩家环境、进服/离服处理
  - `LootService` + `core.chest.*`：loot chest 生成、抽取和摆放
  - `DefaultWorldService` / `WorldController` / `WorldDirectoryRepository`：临时世界复制、加载、卸载、删除
  - `DefaultSetupService`：木斧标记器 setup API
  - `DefaultLobbyService`：大厅出生点、保护、饱食、scoreboard、热键栏物品
  - `DefaultQueueService`：通用匹配队列
  - `ArenaRules`（时长、击杀/存活/名次得分、组队方式、边界形状与伤害、赛后停留）和
    `ArenaPresentation`（游戏名、模式、地图、击杀/淘汰/胜利等反馈文本）挂在 `ArenaSettings` 末尾

## MinigameLib API 原则

- MinigameLib 不允许硬编码 SkyBattle 专属命令、权限、文案、世界名或 `skybattle` 字样。
- 需要玩法差异时，通过 API record/config/listener 从 SkyBattle 传入。
- 新增 API 时优先使用不可变 record，构造函数要给合理默认值，兼容已有调用方。
- `ArenaCreateRequest`、`ArenaSettings`、`ArenaLootChest`、`ArenaItemEntry` 等公开 record 改动后，必须同步 SkyBattle 调用点。
- 附魔解析、药水物品、皮革队伍色装备、无限副手方块等通用能力应放 MinigameLib，SkyBattle 只声明配置和条目。
- 玩家可见通用文本放 MinigameLib 语言文件；玩法专属文本放 SkyBattle 语言文件。

## 构建顺序

1. 如果改了 MinigameLib API 或实现，先在 MinigameLib 仓库执行：

   ```bash
   mvn clean install -DskipTests
   ```

2. 再在本仓库执行：

   ```bash
   mvn clean package -DskipTests
   ```

3. SkyBattle 输出 jar：

   ```text
   target/skybattle-0.2.0.jar
   ```

4. MinigameLib 输出 jar：

   ```text
   /Users/alextang/Projects/minigamelib/target/minigamelib-0.2.0.jar
   ```

## MinigameLib 测试

- MinigameLib 有 JUnit 测试，改动核心算法时优先跑完整测试：

  ```bash
  mvn test
  ```

- 当前测试重点：
  - `BoundaryMathTest`
  - `SpawnGeometryTest`
  - `TeamDistributionTest`
  - `TextRenderTest`
- 如果只是快速打包可用 `mvn clean install -DskipTests`，但改了边界、队伍、TAB 文本排版、出生笼时应跑测试。

## 命令和权限

- 主命令是 `/skb`，`/skybattle` 只是别名。不要再新增 `/sb`。
- 玩家命令（默认所有人）：`/skb`（菜单）、`/skb join <solo|quads>`、`/skb leave`、`/skb menu`、`/skb stats [player]`、`/skb help`。
- 管理命令（默认 OP）：`reload`、`list`、`start [arena|mode]`、`forcestart <mode>`、`stop`、`destroy`、`setup <arena> <world> [solo|quads]`、`setupaction`（setup 聊天按钮用）、`spawn`、`board`。
- `/skb start` 不指定 arena 时，会从已加载 arena 模板里随机选择一个。
- 权限前缀是 `skybattle.command.`，管理员权限是 `skybattle.admin`。
- 单人开局测试只允许拥有 `skybattle.admin` 的执行者。

## 配置和数据布局

- 总配置：`plugins/SkyBattle/config.yml`，默认来自 `src/main/resources/config.yml`。
- 语言文件：`plugins/SkyBattle/lang/zh_cn.yml`，默认来自 `src/main/resources/lang/zh_cn.yml`。
- 语言文件：`zh_cn`（默认）与 `en_us`，两份必须保持相同的 key；缺失 key 会回退到 jar 内默认文件。
- arena 模板配置：`plugins/SkyBattle/arena/<arena>.yml`，`mode: solo|quads`（缺省 quads）、`display-name`。
- 世界模板目录：服务器根目录下的 `arena/<worldName>`。
- loot table：`plugins/SkyBattle/loot/<mode>/<tier>.yml`，`rolls` 为每箱抽取的不同变体数。
- 模式参数（人数、倒计时、时长、得分、初始装备 kit）在 `config.yml -> modes.<mode>`。
- 新增玩家可见文本时，同时写入 zh_cn 与 en_us，不要散落硬编码。

## Setup 注意事项

- `/skb setup <arena> <worldName>` 会复制服务器根目录的 `arena/<worldName>` 作为临时 setup 世界。
- setup 保存完成后写入 `plugins/SkyBattle/arena/<arena>.yml`。
- loot chest 标记时，同一个箱子再次左键表示取消该点；拒绝重复记录。
- 出生点 setup 选择的是脚下方块，最终玩家出生位置需要避免陷入方块。
- 每队出生点应为同一 3x3 区域的四个角，setup 需要校验。

## Loot Chest 规则

- 箱子分级配置字段：
  - `commonchest`
  - `uncommonchest`
  - `rarechest`
  - `epicchest`
  - `legendarychest`
- 每个箱子从对应 loot table 的 `variants` 中按权重抽 `rolls` 个不同变体（默认 1）。
- common/rare 等普通箱子物品允许按规则堆叠；epic/legendary 以及不可堆叠物品要拆开摆放。
- 当前箱子外观不走自定义材质，加载/生成时直接替换成原版方块：
  - common：`CHEST`
  - uncommon：`WAXED_COPPER_CHEST`
  - rare：`WAXED_EXPOSED_COPPER_CHEST`
  - epic：`WAXED_WEATHERED_COPPER_CHEST`
  - legendary：`WAXED_OXIDIZED_COPPER_CHEST`
- 不要再把 chest entity texture 覆盖塞进 SkyBattle 资源包，除非未来明确重新设计箱子材质方案。

## 资源包

- 资源包源文件在仓库根目录 `resourcepack/pack/`（已纳入 Git），贴图由 `resourcepack/tools/generate_pack.py` 生成。
- 构建时 `maven-antrun-plugin` 把它压缩为 jar 内的 `resourcepack/skybattle-resourcepack.zip`。
- 插件启动时导出到 `plugins/SkyBattle/skybattle-resourcepack.zip`，便于 `server-resource-pack` / CDN 分发。
- `config.yml -> resource-pack.enabled: true` 时通过 MinigameLib `ResourcePackService` 自动下发（端口见 MinigameLib config.yml）。
- 物品模型使用 1.21.4+ 的 `assets/skybattle/items/<name>.json` + `item_model` 组件；`pack.mcmeta` 使用 `min_format: 75` / `max_format: 999` 以覆盖 1.21.11 与 26.x。
- 不要把 chest entity texture 覆盖塞进资源包。

## MinigameLib 资源包服务

- MinigameLib 保留 `ResourcePackService` 和 `ArenaResourcePackConfig`，支持外部插件选择启用。
- 资源包必须由玩法插件提供 jar 内资源路径，MinigameLib 只负责提取、计算 SHA-1、内置 HTTP 服务和发送。
- 不要在 MinigameLib 内写死资源包文件名、描述或 SkyBattle 路径。

## TAB 接入注意事项

- MinigameLib 依赖 NEZNAMY/TAB API，运行服必须装 TAB。
- TAB 相关实现集中在：
  - `core.tab.TabFeatureConfigurer`
  - `core.arena.TabDisplayService`
  - `core.lobby.DefaultLobbyService`
- TAB layout 用于游戏内队伍面板、队伍排名、个人统计、header/footer、bossbar、scoreboard。
- 游戏外 lobby TAB 应保持普通玩家列表，不要把 SkyBattle 游戏内四栏 layout 泄漏到大厅。
- TAB 的 playerlist objective/ping 显示容易与 layout 冲突；改 TAB 配置生成逻辑后要观察启动日志里的 TAB WARN。
- MinigameLib 可以自动修正 TAB 配置，但不要覆盖用户无关配置。

## 特殊物品

- 药水球和火花使用 MinigameLib 的 `ItemDisplay` 投射物/显示实体方案。
- 当前 item model（alias -> 材质 / CustomModelData / 模型）：
  - `timed_orb_of_harming`：`FIRE_CHARGE`，1001，`skybattle:timed_orb_of_harming`
  - `orb_of_poison`（兼容旧 alias `quick_timed_orb_of_poison`）：`SLIME_BALL`，1002
  - `orb_of_cleansing`：`SNOWBALL`，1003（清除负面效果）
  - `spark_of_levitation`：`FEATHER`，1004（飘浮 V 2.5 秒 ≈ 13 格）
  - `spark_of_regeneration`：`BLAZE_POWDER`，1005
  - `orb_of_slowness`：`PRISMARINE_CRYSTALS`，1006
  - `spark_of_speed`：`SUGAR`，1007
- 纯雪球不应带恢复等药水效果。
- 药水、TNT、苦力怕应对所有玩家一视同仁；击杀归属只用于计分和死亡消息。
- 队友伤害不计击杀，普通队内 PVP 要阻止；TNT、苦力怕、药水效果按游戏规则处理但不要给队友击杀分。

## 游戏规则要点

- 大厅世界通常是 `world`，大厅出生点由 `/skb spawn` 写入 SkyBattle 配置。
- 大厅内玩家应保持冒险模式、免疫伤害、饱食；游戏内不要强行保持饱食。
- 玩家进入 arena 倒计时阶段为冒险模式，正式开始切生存模式。
- 比赛结束后玩家留在 arena 世界旁观（`ArenaRules.postGameSeconds`，SkyBattle 为 10 秒），再传回大厅并删除临时世界。
- 回合 5 分钟（`time-limit-seconds`），时间到按存活人数、得分决定名次；游戏中退出视为淘汰，重连后作为旁观者回到对局。
- Quads 使用 `ArenaTeamFillMode.FILL`：先填满 4 人队再开新队；Solo 每人一队。
- 玩家掉入虚空应立即判死；上边界只造成持续伤害；大厅掉虚空则传回大厅出生点。
- TNT 放下即点燃；爆炸不破坏 loot chest，不掉落任何方块。
- 玩家挖方块只破坏不掉落，无论使用什么工具。
- 边界不要使用世界边界，使用粒子表示当前边界和收缩目标（只给附近玩家单独发送）；默认圆形（配置了矩形边界墙时为矩形），边界外每秒扣 `border-damage-per-second`。

## MinigameLib 运行规则要点

- runtime world 名称由调用方传入，SkyBattle 当前使用 `skybattle_<arenaId>` 风格。
- 临时世界结束后必须卸载并删除；如果删除失败，优先检查玩家/实体/任务是否仍引用该世界。
- 26.1+ 加载旧格式世界时会迁移目录，`RuntimeWorld.worldPath` 记录实际目录，删除时两处都要清理。
- arena 结束后玩家先在 arena 世界旁观 15 秒，再返回大厅。
- 大厅保护、饱食、虚空传回大厅出生点属于 MinigameLib/lobby 通用逻辑。
- arena 中禁止普通队内 PVP；药水/TNT/苦力怕等效果可命中所有人，但队友击杀不计分。
- 所有死亡和击杀归属最终应进入 RuntimeArena 统计，供 TAB、scoreboard、结算聊天栏复用。
- 最终队伍排名按最终失败/死亡顺序确定；结算聊天栏要显示队伍和队伍内全部玩家。
- 上下边界用 `ArenaVerticalBoundary.DISABLED` 表示关闭，配置写入时通常用 `-1`。
- 边界阶段支持水平距离和上下界限一起收缩。

## 代码边界

- SkyBattle 负责读取自身配置、注册命令、组织 SkyBattle 专属模板和 loot table。
- MinigameLib 能解决的通用机制放 MinigameLib：arena 生命周期、setup 标记器、队伍、临时世界、TAB、边界、箱子生成、死亡/结算等。
- MinigameLib 中不要出现 SkyBattle 专属权限或硬编码 `skybattle` 字样；需要策略时由 SkyBattle 通过配置/API 传入。
- 修改跨仓库 API 时，确保 MinigameLib 先 `mvn clean install -DskipTests`，再构建 SkyBattle。
- 修改 MinigameLib core 行为时，检查是否需要同步 README、API 默认值和 SkyBattle 适配层。
- 修改 SkyBattle loot/item/arena 配置时，确认是否应沉淀为 MinigameLib 通用能力，而不是写玩法内私有 hack。

## 验证习惯

- Java 改动后至少运行：

  ```bash
  mvn clean package -DskipTests
  ```

- 如果改了 MinigameLib，两个仓库都要构建。
- 构建后检查 jar 内不要误包含旧资源包：

  ```bash
  jar tf target/skybattle-0.2.0.jar | rg "resourcepack|lootchests"
  ```

- jar 内应只有 `resourcepack/skybattle-resourcepack.zip`，不应出现旧的 `resourcepacks/` 或 `lootchests`。
