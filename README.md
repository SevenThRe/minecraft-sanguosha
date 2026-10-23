# Minecraft-SanGuoSha / 三国杀

Play San Guo Sha with friends or AI in Minecraft Java Edition. The public beta
supports six-player and eight-player identity matches and 2v2 team matches,
with room lobbies, hero skills, and a paper-and-ink card interface.

在 Minecraft 中放置三国杀牌桌，与朋友开房对战，或添加 AI 补齐座位。
提供军六、军八身份模式与 2v2；当前可用标准、EX、军争内容包，完整逐鹿牌堆尚未开放。

## Installation / 安装

Minecraft Java **1.21.1**, **Java 21**. Choose the matching **Fabric**, **Forge**,
or **NeoForge** file and install the same mod version and loader on both client
and server. **Balm** is required for all loaders; Fabric also requires **Fabric API**.

从创造物品栏“三国杀”页取出牌桌，放置后右键打开大厅。朋友进入同一个
Minecraft 服务器后加入房间；单人可创建房间并添加 AI。

## Building / 构建

Use JDK 21 and the included Gradle wrapper:

```powershell
.\validation\gradlew.bat -p validation :fabric:build :forge:build :neoforge:build
```

```sh
bash validation/gradlew -p validation :fabric:build :forge:build :neoforge:build
```

Artifacts are written to `validation/<loader>/build/libs/`. See [BUILD.txt](BUILD.txt).

## License / 许可

Project-authored source code and build scripts are **GPL-3.0-only**.
See [LICENSE](LICENSE), [LICENSING.md](LICENSING.md), and
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Artwork, audio, fonts, and
third-party components retain their own terms and are not automatically
relicensed under the code license.

源码仓库：https://github.com/SevenThRe/minecraft-sanguosha

This is an unofficial community project and a public test release. Finish a
match before stopping the server: some skill and judgment resolutions cannot
yet be resumed after a server restart.