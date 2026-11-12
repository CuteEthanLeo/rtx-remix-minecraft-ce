# RTX Remix for Minecraft Community Edition (Unofficial)

**中文 | [English](#english)**

## 这是什么

RTX Remix for Minecraft 社区版是 NVIDIA 开源项目
[RTX Remix for Minecraft](https://github.com/NVIDIAGameWorks/rtx-remix-minecraft)
（Apache-2.0）的**非官方**衍生版本，在官方 Fabric 1.21.1 mod 的基础上改进光照、天气、水面、夜空，并加入可选的动态模糊。

- **出处**：上游仓库 `NVIDIAGameWorks/rtx-remix-minecraft`，基线提交 `a518eff`（2026-11-03）。基线也记录在
  `src/main/java/com/nvidia/rtxremix/minecraft/community/CommunityEdition.java` 里，启动日志会打印。
- **非官方声明**：本版本与 NVIDIA、Mojang、Microsoft 均无关联，也未获得其认可。NVIDIA、GeForce、RTX 是 NVIDIA
  Corporation 的商标；Minecraft 是 Mojang Synergies AB 的商标。
- **许可证**：与上游相同，Apache License 2.0（见 `LICENSE`、`NOTICE`）。
- **兼容性**：mod id 保持 `rtx-remix-renderer`，可直接替换官方 jar，两者不能同时安装。已放置的铜信标不受影响。

## 改了什么

每一项都能在游戏里单独开关（选项 → RTX Remix 设置 → 社区版 / 画面与曝光 / 材质），方便与官方效果对比。

| 问题 | 改动 | 开关 | 主要文件 |
|---|---|---|---|
| 草地、泥土油亮 | 官方把 LabPBR 光滑度换算成 `(1-s)²`，而 Remix 读取的是感知粗糙度并会再平方一次。改为规范的 `1-s` | 材质 → 精确粗糙度（默认开） | `texture/LabPBRConverter.java` |
| 雨天没氛围、像晴天 | 雨/雷暴 conf：更浓的散射雾和飘动的非均匀雨幕，曝光不再把阴天提亮；阴天天空减光，雨中白天加入柔和环境光 | 社区版 → 阴天天气 | `confs/weather/*.conf`、`community/OvercastSky.java`、`sky/SkyRenderer.java` |
| 地面不湿 | 露天朝上的方块面在雨中按 LabPBR 孔隙度变暗并泛水光，雨停后约 90 秒变干；有遮挡处保持干燥 | 社区版 → 湿润地面 | `community/WetSurfaces.java`、`texture/WetAtlas.java`、地形捕获 |
| 雨天地面没有积水 | 雨越下越久，露天的土路、石头、木板等硬质地面上汇出镜面般的水坑（世界坐标贴花，按湿度逐级扩大），地面变干时慢慢缩小；草地、沙子、雪、苔藓、树叶、耕地不积水 | 社区版 → 积水坑 | `community/Puddles.java`、`texture/PuddleMask.java`、地形捕获 |
| 雨滴落地没有涟漪 | 下雨时露天地面的薄水膜上有动画的雨滴涟漪圈（法线贴图序列帧，随雨出现、雨停消失）；开启时隐藏原版的雨滴溅射粒子 | 社区版 → 雨滴涟漪 | `community/RainRipples.java`、地形捕获 |
| （新功能）体积云 | 写实尺度的体积云（云底约 y=400）：晴天是铺到地平线、底平顶亮的积云，7 km 高空有成片、纤细的丝状卷云，闪电从云里把雷暴云照亮，雷暴时长出 1500 格高、带砧状云顶的积雨云塔，下雨转为阴天。所有云都在天空穹顶里按体积光线步进，与大气共用同一个太阳、月亮和透射 LUT，并按空气透视融进地平线；相机周围几百格内另用同一个云场生成看不见的网格，只负责真实阴影和光柱；远处云挡住太阳时整片地面变暗，星星、银河、流星被云遮住。默认开启；之前手动选过「程序化」云的存档保持原样 | 云 → 体积云 / 体积云精度 / 云量 | `community/clouds/*`、`cloud/VolumetricCloudTiles.java`、`shaders/cloud_sky.comp` |
| 雨天云发棕、夜晚云发白 | 云的反照率与次表面散射随时间和天气变化：雨中风暴灰，夜里暗蓝 | 社区版 → 云层明暗 | `community/CloudTint.java`、`cloud/CloudRenderer.java` |
| 湖水发黑、水面单调 | 水体透射距离 1 m → 3.5 m，按生物群系分 9 种水色；水面单独成页并标记为动态水（双层流动波浪，雨中更粗糙）；岸边泡沫 | 社区版 → 真实水体 / 流动波浪 / 岸边浪花 | `community/WaterLook.java`、`community/ShoreFoam.java`、`terrain/*` |
| 夜晚单薄 | 6000 颗亮度、色温各异的星星，沿银河聚集；程序化银河带（满月时变淡）；月光更强更冷；夜间薄雾让灯光有光晕；夜间天空环境光默认 2.5 → 0（天穹本身已经照亮夜晚，再加一层会把月光下的草地染绿） | 社区版 → 星空 | `community/StarField.java`、`community/NightSky*.java`、`confs/time/*.conf` |
| 晨昏没有体积光 | 太阳高度 2°–20° 时太阳对雾的散射最高放大到 4 倍，天空环境光对雾的散射降到 0.35，光柱和阴影的反差拉开；清晨是贴地、成团飘动的晨雾，黄昏是偏暖的薄霾，都强烈向前散射 | 社区版 → 光照与体积光 → 晨昏光柱 | `community/SunShafts.java`、`confs/time/dawn.conf`、`dusk.conf` |
| 雨天体积光平淡 | 地面仍是阴天光照，但太阳对雾的散射单独补偿回晴天的 20%（雷暴 8%），从云缝中照出斜光柱；雨幕噪声加到 3 个 octave，密度曲线更陡，雨幕之间有清楚的空隙 | 社区版 → 光照与体积光 → 云隙光柱 | `community/SunShafts.java`、`confs/weather/*.conf` |
| （新功能）闪电照亮雨幕 | 每道闪电沿高度放 3 个冷白光源，对雾的散射为 4 倍，照亮周围的雨幕并投下短暂的影子；远处闪电让天空环境光闪一下 | 社区版 → 光照与体积光 → 闪电照亮雨幕 | `community/LightningFlash.java`、`light/LightManager.java` |
| 白天「傻白」 | 白天天空散射 ×16 → ×11，阳光与阴影的明暗比更合理，阴影保持偏蓝，自动曝光把整体亮度提回；Remix 默认的中性灰、各向同性雾换成偏蓝、向前散射的空气透视（新 conf 层 `community/daylight.conf`，优先级 4001.5）；阴影提亮 1.4 → 1.2；日晕增益 10.35 → 4，太阳贴片缩小一半 | 社区版 → 光照与体积光 → 写实白天 | `community/DaylightSky.java`、`sky/AtmosphericSkyGenerator.java`、`shaders/sky_atmosphere.comp` |
| 体积光发糊 | froxel 网格三档：标准 16 px/48 层、高 12 px/64 层、极高 8 px/64 层（默认）；画质预设切换后重新写入 | 社区版 → 光照与体积光 → 体积光精度 | `settings/VolumetricQualitySettings.java` |
| （新功能）动态模糊 | Remix 后处理的运动模糊，四档质量（关/标准/高/电影）、快门和最大模糊长度可调；手和方块描边不模糊；色差和暗角保持关闭 | 画面与曝光 → 镜头效果 | `settings/CameraEffectsSettings.java` |
| （新功能）简体中文 | 全部设置与提示的中文翻译 | 跟随游戏语言 | `lang/zh_cn.json` |
| （实验性）CPU 和 GPU 轮流空等，帧率上不去 | 「顶级优化」：画面内容不变，只改调度、跳过看不见的工作。流水线呈现让 CPU 在 GPU 画第 N 帧时就准备第 N+1 帧（Present 不再在 CPU 上等 GL）；干燥水坑剔除在地面干时把全透明的水坑贴花移出场景；更激进的提交重叠默认关。`/rtx topopt ab` 在游戏里交替开关测提速 | 性能 → 顶级优化（实验性，默认关） | `community/perf/*`、`RemixRenderer.java`、`gl/GlInterop.java` |

另有一项维护性改动：官方 mod 从不覆盖已存在的 `rtx-remix/` conf，所以已安装官方版的玩家拿不到新的默认 conf。社区版在
`rtx-remix-superseded-defaults.txt` 中记录了旧默认 conf 的哈希：**完全未改动**的旧默认会被升级，改过的保持原样
（`SupersededPayloadDefaults.java`）。

### 实时调参

`/rtx ce` 列出当前值；`/rtx ce shafts|rainshafts|skyfill|halo|lightning <数值>` 只在本次运行中修改晨昏光柱峰值、云隙光柱比例、白天天空散射、日晕增益和闪电亮度，方便找到合适的数值后再改成默认值。conf 文件改动后用 `/rtx conf reload` 生效。

### 尚未完成 / 已知限制

- **第三人称玩家影子**：静态审查没有找到压掉影子的代码，需要在游戏里按复现步骤确认（见下方「验证」）后再修。
- 雨滴落在**水面**（湖、河）上的环形涟漪：仍用「雨中加快、加细的双层波浪」近似。露天地面上的积水坑和雨滴涟漪已经加入（见上表「积水坑」「雨滴涟漪」）。

### Minecraft 1.20.1 分支（`mc/1.20.1`）移植状态

这个分支从 1.21.1 经 1.20.6、1.20.4 往回移植到 1.20.1，**目前只保证编译、JVM 测试和 Mixin 应用测试（`MixinTargetsApplyTest`，在 Fabric Loader 下加载每个注入目标）通过，尚未在游戏里验证渲染**。

去掉的只有一项性能策略：

- 集成服务器的区块下发节流和卸载节流（`LocalChunkBatchPacing`、`PacedUnloadQueue`、`ChunkDataSenderMixin`）连同 `rtxremix.chunk.localBatch*`、`localBacklog*`、`localUnload*` 这几个开关和 `Chunk.Paced*`、`Chunk.ApplyUsPerChunk`、`Chunk.UnloadUsPerChunk` 计数器一起删除。1.20.1 没有 `ChunkDataSender`（1.20.2 才加入）：区块一进入或离开玩家视野，`ThreadedAnvilChunkStorage` 就立刻发包，没有按玩家排队、按 tick 发送的地方可以挂。源码在 `RtxRemixMod` 里用注释标出了以后可以接回这项策略的位置。影响是单人游戏进入世界或跨区块边界时，一整排区块会在同一帧到达，这几帧可能比其他版本更卡。

其余功能和性能策略全部保留。和 1.20.4 分支相比行为或实现不同的地方：

- 区块可见性遍历：1.20.1 没有 `ChunkRenderingDataPreparer`，遍历写在 `WorldRenderer` 里，mod 的无分配移植、低优先级线程、粗粒度视锥索引和按帧分片都改挂到 `WorldRenderer`（`WorldRendererVisibilityMixin`，原 `ChunkRenderingDataPreparerMixin`），`rtxremix.vanillaVisibilityWalk`、`rtxremix.logVisibleSet` 两个开关照旧。视锥索引里存的是可见性图的 `ChunkInfo` 节点，因为 1.20.1 的可见列表由它们组成；遇到尚未可构建的相邻区块段时，照 1.20.1 原版的做法安排 500 毫秒后重新做一次完整遍历，而不是记入等待表。性能分析里不再有 `ChunkVisibility.setStorage` 和 `ChunkVisibility.prepare` 两个标签（后者的范围已被 `WorldRenderer.setupTerrain` 覆盖）。
- 区块段构建并发上限改挂在 `ChunkBuilder` 的构造函数上（`ChunkBuilderMixin`，原 `BlockBufferBuilderPoolMixin`），因为 1.20.1 没有 `BlockBufferBuilderPool`。
- 贴图坐标：1.20.1 的 `Sprite.getFrameU/getFrameV` 取 0 到 16 而不是 0 到 1，流体表面和带贴图的实体长方体按这个范围换算。
- 设置页面：标题、页脚用 `GridWidget` 排布（1.20.1 没有 `DirectionalLayoutWidget`），列表是 1.20.1 的旧式条目列表；1.20.1 的页面要自己画背景，在世界里列表及其上下两条遮挡带都不画，能看到设置的效果。
- 铜信标界面：红石控制的勾选框是 1.20.1 的 20 像素样式（比 1.20.4 的大，位置上移 2 像素）；物品栏格子的底图取自箱子界面贴图（1.20.1 没有界面精灵图集）。方块不再声明 `MapCodec`（1.20.2 才有）。
- 铜灯（copper bulb）在 1.20.1 不存在，对应的内置发光方块规则去掉。
- 帧内插值：1.20.1 没有 tick 冻结，`ClientTickDelta` 只剩暂停这一种特殊情况。相机和基准测试的帧起点判断也不再检查「加载完成」，和 1.20.1 的 `GameRenderer.render` 一致。
- 手持火焰：1.20.1 的火把方块把粒子声明为 `ParticleEffect`，只有粒子是简单粒子类型的火把才显示手持火焰（原版火把都是）。
- 第一人称手部捕获用的占位 `BufferBuilder` 不释放（1.20.1 的 `BufferBuilder` 没有释放接口，和原版自己的缓冲一样随进程存活）。
- 开发者基准测试：1.20.1 的世界加载器没有取消回调，取消时改由一个临时页面清掉待执行的自动基准测试。
- 区域存档（`/rtx zone`）用 1.20.1 的 `PersistentStateManager` 接口读写。
- Fabric Loader 最低版本升到 0.16.10：1.20.1 的 Fabric API 0.92.12 要求它。安装器安装的也是这个版本。
- 测试：1.20.1 在引导（bootstrap）之前不能触碰方块或注册表键，而引导需要 Fabric Loader 的访问变换，所以 `ConfLayerDriverTest` 和 `BlockLightRuleManagerTest` 改在 Fabric 测试组里运行；`CopperBeaconDataTest` 用 1.20.1 的配方和战利品表读取器（`RecipeManager.deserialize`、`LootDataType`）解码数据文件。

从 1.21.1 到 1.20.4 一路沿用下来的适配：

- 地形捕获：1.20.1 没有 `SectionBuilder`，方块循环在 `ChunkBuilder.BuiltChunk.RebuildTask.render` 里，捕获 mixin 改名为 `RebuildTaskCaptureMixin`；区块段坐标取自所属 `BuiltChunk` 的原点；空区块段没有渲染区域，直接跳过。区块数据发布的挂钩移到重建任务的完成回调（`RebuildTaskMixin`）。渲染区域快照是二维数组，引用计数按二维遍历。
- 顶点接口是 1.21 之前的旧式（`vertex(...)…next()`）：实体捕获在 `next()` 时结束一个顶点，不再在法线处结束；`fixedColor` 不生效（原版只在描边缓冲上用它）。模型染色以四个浮点数传入，四舍五入还原成 ARGB 字节。
- 相机朝向：1.20.1 的 `Camera.getRotation()` 朝 +Z，和 1.21.1 差绕 Y 轴半圈，粒子公告板、手持火焰和雨雪视图统一经 `CameraFacing` 换算。
- 帧内插值：1.20.1 没有新式 `RenderTickCounter`，由 `ClientTickDelta` 读取（暂停时用暂停瞬间的值）。
- 雨雪捕获包住 `Tessellator.draw()`，批次留在共享的 `Tessellator` 缓冲里直到 Remix 画完或释放；星空顶点取自 `renderStars` 的返回值。
- 设置页面：1.20.1 的 `GameOptionsScreen` 不做任何布局，原版 `OptionListWidget` 也只能放选项，所以标题、页脚和列表由 `RtxOptionListScreen` 搭建，带分组标题和「名称 + 滑块」行的列表是 mod 自己的 `RtxOptionList`。1.20.1 的选项滑块每拖一步就生效、没有待定值，延迟生效（停 600 毫秒或关闭页面时才应用）由 mod 的 `DeferredSliderApply` 实现；拖动过程中原版每一步仍会写一次 `options.txt`。
- 数据包目录用 1.20.1 的复数名（`recipes`、`loot_tables`、`tags/blocks`），配方结果用 `"item"` 字段；铜信标镜片颜色由染料的浮点颜色分量还原。
- Java：1.20.1 运行在 Java 17 上，mod 按 Java 17 编译（构建仍需要 JDK 21），安装器安装和选用的是 Java 17（`packaging/installer.ps1` 的 `$script:JAVA_MAJOR`）。Java 21 才有的接口换成等价写法：`Math.clamp`、`List.getFirst/getLast`、`Math.ceilDiv`、半精度浮点转换（`util/HalfFloat`）。`ReachedChunkSet` 不再覆盖 Java 21 的有序集合方法（Java 17 没有这些方法）。
- 联网与存档：1.20.1 没有 `CustomPayload` 编解码注册，铜信标的设置包和界面打开数据用 `PacketByteBuf` 和 Fabric 按标识符收发的接口；方块实体和区域存档的 NBT 读写不带注册表参数，镜片里的染料按 1.20.1 的物品 NBT 保存（和 1.20.6 的存档格式不同，跨版本搬动世界时镜片里的染料不会保留）。
- 第一人称手部：1.20.1 的手部矩阵栈以相机为根、模型视图矩阵不含相机旋转，`RemixFirstPersonPass` 把它换算成以相机旋转的逆为根，和其他捕获保持同一坐标约定。
- 其余适配：`VertexConsumer` 用矩阵而不是矩阵栈条目的重载、模型四边形不带透明度参数；`MatrixStack.Entry` 没有跳过法线归一化的标记，`MatrixStackEntryAccessor` 删除；`WorldRenderer` 的 `render`、`renderLayer`、`renderClouds`、粒子绘制以及 `GameRenderer` 的 `renderHand`、`renderWorld` 都带 `MatrixStack` 参数；模型视图栈是 `MatrixStack`。

## 安装（本地）

1. 构建：`.\gradlew.bat package -Parch=win64`，得到 `build\distributions\rtx-remix-renderer-0.1.0-ce.1-…-win64.jar`。
2. 安装到已有的官方安装目录（会自动备份被替换的 jar 和 conf）：
   ```powershell
   .\packaging\community-install.ps1 -GameDir 'D:\PCL\.minecraft\versions\RTX' -Jar '<上一步的 jar>'
   ```
3. 恢复官方版：`.\packaging\community-install.ps1 -GameDir '<同上>' -Restore`

## 维护

- **同步上游**：
  ```powershell
  git fetch upstream
  git merge upstream/main
  ```
  合并后把 `CommunityEdition.UPSTREAM_BASE` 和本文的基线提交改成新的上游提交。
- **改了打包的 conf**：把被替换版本的哈希追加到 `src/main/resources/rtx-remix-superseded-defaults.txt`
  （去掉 `\r` 后的 SHA-256，路径相对游戏目录），否则已安装的玩家收不到新默认值。
- **文件头**：新文件只写社区版署名；修改过的上游文件保留 NVIDIA 的 SPDX 行，在其下加一行社区版署名，并注明
  `Modified by RTX Remix for Minecraft Community Edition`（Apache-2.0 第 4(b) 条）。
- 社区版新增的逻辑尽量放在 `com.nvidia.rtxremix.minecraft.community` 包里，上游文件只留少量挂钩，减少合并冲突。

## 验证

- 单元测试：`.\gradlew.bat test`（新增：`CloudTintTest`、`StarFieldTest`、`WaterLookTest`、`WetSurfacesTest`、
  `CameraEffectsSettingsTest`、`SupersededPayloadDefaultsTest`、LabPBR 两种编码；顶级优化：`TopOptimizationTest`、
  `PresentWaitPolicyTest`、`TopOptimizationAbTest`、`PuddleDecalVisibilityTest`、`SubmitOverlapGuardTest`、
  `ClientDoneTimelineTest`、`FrameTimeHistogramTest`、`ChunkManagerShownFlagsTest`）。
- 顶级优化：游戏里执行 `/rtx topopt ab`，约 40 秒后聊天栏给出关/开两组的真实帧率、1% low 和提速倍数。
- 画面：在同一机位分别用官方 jar 和社区版 jar 截图对比。建议用 `/rtx shot mark <名字>` 固定机位，覆盖晴天草地、
  雨天草地、雨天湖面、海滩、夜晚城市。
- 玩家影子复现：`/weather clear` 等 10 秒，`/time set 1000`，F5，旁边放盔甲架并
  `/summon zombie ~2 ~ ~ {NoAI:1b}` 对照；分别在云开/关时看玩家、盔甲架、僵尸有没有影子。

---

<a id="english"></a>

## English

RTX Remix for Minecraft Community Edition is an **unofficial** derivative of NVIDIA's open-source
[RTX Remix for Minecraft](https://github.com/NVIDIAGameWorks/rtx-remix-minecraft) (Apache-2.0), based on upstream
commit `a518eff` (2026-11-03). It is not affiliated with or endorsed by NVIDIA, Mojang, or Microsoft. It keeps the
mod id `rtx-remix-renderer`, so it replaces the official JAR and cannot be installed beside it.

What changed, each switchable in game (Options → RTX Remix Settings → Community Edition, Image & Exposure, Materials):

- **Accurate Roughness** (Materials, on by default): LabPBR smoothness converts to `1 - s`, the perceptual roughness
  Remix samples, instead of upstream's `(1 - s)²`, which Remix squares again and so renders surfaces too glossy.
- **Overcast Weather**: denser, drifting rain haze; no exposure lift in rain; a dimmer overcast dome and a soft
  daytime sky ambient while it rains.
- **Wet Ground**: upward faces open to the sky darken and gain a water sheen in rain by LabPBR porosity, then dry over
  about 90 seconds.
- **Puddles**: as the rain goes on, near-mirror puddles gather on open hard ground such as paths, stone, and planks
  (world-space decals that grow with wetness) and shrink as the ground dries; grass, sand, snow, moss, leaves, and
  farmland never pool.
- **Rain Ripples**: while it rains, animated raindrop rings spread over a thin film of water on open ground and stop
  with the rain; vanilla rain splash particles are hidden while this is on.
- **Volumetric Clouds** (Clouds, on by default): real-scale cloudscapes with a base near y 400: cumulus fields to
  the horizon in fair weather, 1500-block cumulonimbus towers with anvils in thunder, overcast in rain. Distant clouds
  are ray-marched into the sky dome as volumes with the atmosphere's own sun, moon, and transmittance LUT and fade
  into the horizon by aerial perspective; within a few hundred blocks the same cloud field is also meshed, hidden from
  the camera, so near clouds shadow the ground and form light shafts. Thin, fibrous cirrus sheets lie 7 km up, and
  lightning lights the storm clouds from inside. A distant cloud over the sun dims the whole view, and clouds
  hide the stars, the Milky Way, and meteors behind them. Detail (Low/High/Ultra) and Cloud Cover are adjustable; a
  settings file that chose the Procedural style keeps it.
- **Cloud Shading**: clouds turn storm grey in rain and dim blue at night instead of staying white.
- **Realistic Water, Moving Waves, Shore Foam**: longer transmittance with nine biome water colours; water pages
  classified as Remix animated water with two drifting wave layers that roughen in rain; foam strips along shores.
- **Starry Night**: 6000 stars of varied brightness and colour that crowd toward a procedural Milky Way (domain-warped
  star clouds, a great rift and ridged dust filaments, a warm bulge and bluish arms, faint nebulae, and a grain of
  unresolved stars, in a 40-degree meandering band), which a full moon washes out; shooting stars that flare and fade
  along a falling great circle in real time (export time in a Flashback export), about every seven seconds and every 1.6 seconds on a shower night (one
  night in five); cooler moonlight three times upstream's that follows the moon phase (new moon 30%); thin night
  haze; the night sky ambient rect defaults to 0 instead of 2.5, since the dome already lights the night and the rect
  counted that sky twice in its cyan mean colour (which is what turned moonlit grass green); night auto exposure may
  reach EV -4.
- **Motion Blur** (Image & Exposure → Camera Effects): off, Standard, High, Cinematic, or Ultra (8/16/32/48
  samples), with a shutter speed from 1/250 s to 1/24 s (default 1/48 s) and a maximum blur size up to 0.25 of the
  screen. The shutter is a time: Remix's `exposureFraction` is per-frame motion times a share of a frame, so it is
  recomputed every frame from the smoothed frame time and a streak is equally long at any frame rate. Noise sampling
  is on at every step, so streaks are two-sided. The hand and block outline stay sharp.
- **Flashback Exports**: while Flashback exports a replay, which renders far slower than real time, shooting stars,
  the motion-blur frame time, the lightning flash, ground wetness and conf-layer blends follow the export's timeline
  instead of the wall clock, and Remix's own frame time (`rtx.timeDeltaBetweenFrames`, water animation and eye
  adaptation) is pinned to the export frame time, so the video moves as it would in play. Flashback is read by
  reflection and is not required.
- **Glowing Block Lights**: glowstone, sea lanterns, froglights, shroomlights, jack o'lanterns, lit redstone lamps
  and copper bulbs get a rect light on each exposed face (only the carved face for a jack o'lantern); lanterns, soul
  lanterns, and end rods get a sphere or cylinder. Before, they lit the scene only through emissive texels, which
  Remix does not sample as lights. Each is calibrated to a torch's emitted power at its light level and has its own
  intensity control.
- **Auto PBR** (Materials, on by default): for block textures without a LabPBR sidecar, a normal map is derived from
  the albedo's luminance (darker texels sit lower) and roughness and metalness come from the block's name
  (`texture/AutoPbr`): gold, iron, netherite, raw-metal, copper (bare and exposed), anvils, chains, and iron bars are
  metals; diamond, emerald, amethyst, ice, obsidian, quartz, polished stone, concrete, and glazed terracotta are
  glossy dielectrics; wood, soil, wool, and foliage stay rough. Darker texels are slightly rougher. An installed PBR
  pack's sidecars always win.
- **Linear AO**: LabPBR ambient occlusion multiplies the decoded albedo, not its sRGB bytes, which had applied AO
  raised to about 2.2.
- **Foliage energy**: the baseline leaves and grass `_sss` single-scattering albedo is halved on load. At its authored
  green 0.94, light bounced between blades and through leaves almost without loss, so foliage near a torch glowed
  fluorescent green.
- **Light & Haze** (Community Edition):
  - **Golden-Hour Shafts**: between about 2 and 20 degrees of sun elevation the sun scatters up to four times more
    into the haze and the sky ambient less, over a patchy dawn mist and a warm dusk haze, so trees and hills cast
    shafts.
  - **Cloud-Gap Shafts**: in rain the ground stays overcast, but the haze gets back a share of the clear-sky sun, so
    it shines through cloud gaps; rain curtains drift with clearer gaps between them.
  - **Lightning Glow**: bolts light the rain haze around them and throw brief, cold shadows; distant flashes lift
    the sky ambient.
  - **Natural Daylight**: one physical atmosphere (`sky/AtmosphereModel`, the Earth parameters of Hillaire 2020 with
    ozone, Mie absorption, and a multiple-scattering LUT built once) for both the sky dome and the sun and moon
    lights. Upstream lit the sky with empirical multipliers (16 by day, 70 by night) and dimmed the sun with a
    separate airmass formula three times stronger than the shader's air, so the sky gave half the sun's light at noon
    and over four times it at a low sun, and the noon sun was orange. Now the sky is lit by the distant light's own
    irradiance (pi times its radiance), the sky-to-sun ratio is the real one at every hour, the zenith is deep blue
    instead of cyan, the noon sun is near white, and the dome no longer paints a sun disc that was sampled as light a
    second time. The sun is a small round limb-darkened disc with a tight glow, tinted by the same transmittance; a
    blue, forward-scattering aerial perspective (`confs/community/daylight.conf`) replaces Remix's neutral default
    medium. Off restores the upstream sky, sun, and airmass.
  - **Haze Detail**: froxel grid Standard (16 px, 48 slices), High (12 px, 64), or Ultra (8 px, 64, the default).
  - `/rtx ce` tunes the shaft, sky-fill (1 = the physical sky brightness), halo (1 = physical), and lightning values
    for the session.
- **Simplified Chinese** translation of every setting.
- **Top Optimization** (Performance, experimental, off by default): frame-rate techniques that leave the image
  unchanged. Pipelined Present lets Present wait for GL's composite on the GPU, so the CPU builds frame N+1 while the
  GPU traces frame N; it blocks as before whenever a screen or Remix menu is open and for four frames after any resize,
  world change, or re-import. Dry Puddle Cull gives puddle decals their own pages and hides them while their mask is
  empty. Submit Overlap, off by default, skips the render thread's wait for the runtime's queue submission behind a
  per-frame self-check. Misbehaving techniques switch themselves off for the session. `/rtx topopt ab` measures the
  speedup in game, and the performance report logs the real wall-clock frame rate (`community/perf/*`).
- Shipped confs that an install still has in their untouched upstream form are upgraded on extraction; edited ones
  are kept (`SupersededPayloadDefaults`).

Known gaps: the third-person player shadow still needs an in-game reproduction before a fix; raindrop rings on open
water (lakes, rivers) are still approximated by faster, finer wave layers (puddles and ripples on open ground are
implemented, see above).

The `mc/1.20.1` branch is ported back from 1.21.1 by way of 1.20.6 and 1.20.4 and is verified only by the build, the
JVM tests, and `MixinTargetsApplyTest`, which loads every mixin target under Fabric Loader; rendering is not yet
verified in game. One performance policy is dropped: pacing of the integrated server's chunk delivery and unloads
(`LocalChunkBatchPacing`, `PacedUnloadQueue`, `ChunkDataSenderMixin`, the `rtxremix.chunk.localBatch*`,
`localBacklog*`, and `localUnload*` switches, and the `Chunk.Paced*`, `Chunk.ApplyUsPerChunk`, and
`Chunk.UnloadUsPerChunk` counters). 1.20.1 has no `ChunkDataSender`: `ThreadedAnvilChunkStorage` writes a column the
moment it enters or leaves a player's view, so there is no per-player pending set or per-tick sender to hold a batch
in. A source comment in `RtxRemixMod` marks where the policy would re-enter. In single player a whole row of chunks can therefore land in one
frame on a join or a chunk-boundary crossing.

Everything else is kept. What differs from the 1.20.4 branch: the chunk visibility walk lives inside `WorldRenderer`
on 1.20.1, so the allocation-free port, the low-priority executor, the coarse frustum index, and the time-sliced
propagation hook there (`WorldRendererVisibilityMixin`, formerly `ChunkRenderingDataPreparerMixin`), with both
kill switches unchanged; the index holds the graph's `ChunkInfo` nodes, an unbuilt neighbour schedules a full walk
500 ms later as 1.20.1's vanilla walk does, and the `ChunkVisibility.setStorage` and `ChunkVisibility.prepare`
profiler labels are gone. The section-builder cap hooks the `ChunkBuilder` constructor (`ChunkBuilderMixin`).
`Sprite.getFrameU` and `getFrameV` take 0 to 16 on 1.20.1, and fluid surfaces and sprited entity cuboids convert to
that. The settings screens lay their header and footer out with `GridWidget`, use 1.20.1's older entry list, and
draw their own background; over a world the list and its header and footer strips are not drawn. The Copper Beacon
screen uses 1.20.1's 20-pixel checkbox, raised two pixels, and draws slot backgrounds from the chest texture, and the
block declares no `MapCodec`. Copper bulbs do not exist, so their glowing-block rules are removed. `ClientTickDelta`
has no tick-freeze case, and the frame-start check has no finished-loading condition, as in 1.20.1's
`GameRenderer.render`. The held flame shows for torches whose particle is a simple particle type, which every
vanilla torch's is. The first-person pass's placeholder `BufferBuilder` is never released, since 1.20.1 gives a
builder no release. The benchmark's world load reports a cancellation through a temporary screen. Zone storage uses
1.20.1's `PersistentStateManager` calls. The Fabric Loader floor is 0.16.10, which Fabric API 0.92.12 requires.
`ConfLayerDriverTest` and `BlockLightRuleManagerTest` run in the Fabric test suite, because 1.20.1 refuses to touch
a block or a registry key before the bootstrap, which needs Fabric Loader's access changes, and
`CopperBeaconDataTest` decodes with 1.20.1's own readers (`RecipeManager.deserialize`, `LootDataType`).

Carried over from the earlier steps: terrain capture rides `ChunkBuilder.BuiltChunk.RebuildTask.render`
(`RebuildTaskCaptureMixin`, since 1.20.1 has no `SectionBuilder`) and takes the section from the built chunk's
origin; the chunk-data publish hook is the rebuild task's completion callback; entity capture closes a vertex at the
old `next()` call, ignores `fixedColor`, and rounds float model tints back to bytes; `CameraFacing` turns 1.20.1's
+Z-facing camera rotation half a turn about Y for particles, the held flame, and the weather view; `ClientTickDelta`
supplies the partial tick; weather capture wraps `Tessellator.draw()` and leaves each batch in the shared builder
until it is drawn or released; star vertices come from `renderStars`; and the data folders use 1.20.1's plural
names, with the recipe result in its `"item"` form. The game runs on Java 17, so the mod is compiled for Java 17 (the
build still needs JDK 21), the installer installs and selects Java 17 (`$script:JAVA_MAJOR` in
`packaging/installer.ps1`), and Java 21 library calls are written out (`Math.clamp`, `List.getFirst`,
`Math.ceilDiv`, half-float conversion in `util/HalfFloat`; `ReachedChunkSet` no longer overrides the
sequenced-collection methods Java 17 lacks). The settings screens build their own title, footer, and list
(`RtxOptionListScreen`, `RtxOptionList`), because 1.20.1's option list takes only options, and deferred sliders are
debounced by the mod's `DeferredSliderApply`, because 1.20.1's option slider applies on every drag step; vanilla
still writes `options.txt` on each step. The Copper Beacon's settings packet and screen-opening data use
`PacketByteBuf` with Fabric's identifier-based networking, and its NBT has no registry parameter; the lens dye is
saved as 1.20.1 item NBT. `RemixFirstPersonPass` re-roots 1.20.1's camera-rooted hand stack at the inverse camera
rotation. `MatrixStackEntryAccessor` is removed, since 1.20.1's stack entries carry no normalization flag.

Maintenance: merge `upstream/main`, then update `CommunityEdition.UPSTREAM_BASE`; append the hash of every replaced
shipped conf to `rtx-remix-superseded-defaults.txt`; keep NVIDIA's SPDX line on modified files and add the Community
Edition line and modification notice below it.
