# 回归测试依赖

adventure-text-minimessage-5.2.0.jar 来自准确隔离核心的 libraries，仅用于 testRuntimeOnly。Bukkit Server/Player 接口的 Java 代理解析方法签名时需要 TagResolver；它不属于插件运行依赖，也不打入插件 JAR。

主编译 SDK 及其版本仍见 ../libs/README.md。校验值见 SHA256.json。

运行 `check` 前，从 Adventure 发布渠道或匹配服务端的 SDK 中取得清单里的 MiniMessage JAR，放到本目录。
