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
| 雨天云发棕、夜晚云发白 | 云的反照率与次表面散射随时间和天气变化：雨中风暴灰，夜里暗蓝 | 社区版 → 云层明暗 | `community/CloudTint.java`、`cloud/CloudRenderer.java` |
| 湖水发黑、水面单调 | 水体透射距离 1 m → 3.5 m，按生物群系分 9 种水色；水面单独成页并标记为动态水（双层流动波浪，雨中更粗糙）；岸边泡沫 | 社区版 → 真实水体 / 流动波浪 / 岸边浪花 | `community/WaterLook.java`、`community/ShoreFoam.java`、`terrain/*` |
| 夜晚单薄 | 4000 颗亮度、色温各异的星星，沿银河聚集；程序化银河带（满月时变淡）；月光更强更冷；夜间薄雾让灯光有光晕；夜间环境光默认 2.5 → 1.4 | 社区版 → 星空 | `community/StarField.java`、`community/NightSky*.java`、`confs/time/*.conf` |
| （新功能）动态模糊 | Remix 后处理的运动模糊，四档质量（关/标准/高/电影）、快门和最大模糊长度可调；手和方块描边不模糊；色差和暗角保持关闭 | 画面与曝光 → 镜头效果 | `settings/CameraEffectsSettings.java` |
| （新功能）简体中文 | 全部设置与提示的中文翻译 | 跟随游戏语言 | `lang/zh_cn.json` |

另有一项维护性改动：官方 mod 从不覆盖已存在的 `rtx-remix/` conf，所以已安装官方版的玩家拿不到新的默认 conf。社区版在
`rtx-remix-superseded-defaults.txt` 中记录了旧默认 conf 的哈希：**完全未改动**的旧默认会被升级，改过的保持原样
（`SupersededPayloadDefaults.java`）。

### 尚未完成 / 已知限制

- **第三人称玩家影子**：静态审查没有找到压掉影子的代码，需要在游戏里按复现步骤确认（见下方「验证」）后再修。
- 水洼：图集 UV 拿不到世界坐标，第一版只做整体湿润反光，没有水洼。
- 雨滴落在水面的环形涟漪：第一版用「雨中加快、加细的双层波浪」近似，没有单独的涟漪法线贴图。

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
  `CameraEffectsSettingsTest`、`SupersededPayloadDefaultsTest`、LabPBR 两种编码）。
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
- **Cloud Shading**: clouds turn storm grey in rain and dim blue at night instead of staying white.
- **Realistic Water, Moving Waves, Shore Foam**: longer transmittance with nine biome water colours; water pages
  classified as Remix animated water with two drifting wave layers that roughen in rain; foam strips along shores.
- **Starry Night**: 4000 stars of varied brightness and colour that crowd toward a procedural Milky Way, which a full
  moon washes out; stronger, cooler moonlight; thin night haze; night sky ambient defaults to 1.4 instead of 2.5.
- **Motion Blur** (Image & Exposure → Camera Effects): off, Standard, High, or Cinematic, with shutter and maximum
  blur size; the hand and block outline stay sharp.
- **Simplified Chinese** translation of every setting.
- Shipped confs that an install still has in their untouched upstream form are upgraded on extraction; edited ones
  are kept (`SupersededPayloadDefaults`).

Known gaps: the third-person player shadow still needs an in-game reproduction before a fix; no puddles; rain ripples
are approximated by faster, finer wave layers.

Maintenance: merge `upstream/main`, then update `CommunityEdition.UPSTREAM_BASE`; append the hash of every replaced
shipped conf to `rtx-remix-superseded-defaults.txt`; keep NVIDIA's SPDX line on modified files and add the Community
Edition line and modification notice below it.
