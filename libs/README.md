# 编译依赖

把 [SDK-SHA256.json](SDK-SHA256.json) 列出的 23 个 JAR 放入本目录，文件名和 SHA-256 以清单为准。每项可从相应项目的仓库、发布页或匹配的服务器 SDK 中取得。Folia API 使用 `26.2.build.5-beta`，PlugManX 使用 `3.1.0-Beta.2`。`OpenInv.jar` 和 `TrailGUI-37659dda03.jar` 以清单哈希识别。

Gradle 用这些文件编译及运行回归。安装成品时按需安装可选插件，禁止把这些编译 SDK 当作运行插件全部复制过去。
