<div align="center">

# SuperVanish · Folia 中文维护版

管理员隐身 · 原生区域调度 · 中文配置与指令 · 受控热加载

![Version](https://img.shields.io/badge/version-6.3.0--6-2563eb)
![Platform](https://img.shields.io/badge/platform-Folia%20%2F%20Shiroha-16a34a)
![Java](https://img.shields.io/badge/Java-25-f59e0b)
![Language](https://img.shields.io/badge/语言-简体中文-8b5cf6)

[下载最新版本](https://github.com/heoduling/SuperVanish/releases/latest) · [中文使用说明](使用说明.md) · [验证记录](docs/6.3.0-6验证记录.md) · [版本记录](CHANGELOG.md)

</div>

基于 [LeonMangler/SuperVanish](https://github.com/LeonMangler/SuperVanish) 6.3.0，源码基线 `a1d6093569062afdac72daec45430a94a3065bab`，独立维护 Folia 适配。保留上游 [MIT LICENSE](LICENSE) 和源码内已有的 MPL-2.0 声明。

## 功能与边界

| 功能 | 说明 |
| --- | --- |
| 隐身与权限 | `/sv` 切换隐身，保留分层可见权限、持久化和可配置的假进退服消息；普通玩家默认没有管理指令权限 |
| 中文使用 | 配置注释、消息、指令帮助和权限描述汉化；升级保留已有值并补齐缺失配置 |
| Folia 调度 | 玩家状态在所属实体上下文处理，文件读写走异步流程 |
| 热加载 | 适配 PlugManX 3.1.0-Beta.2，维护前等待业务任务退出并清理临时状态 |
| 可选发包过滤 | PacketEvents 2.13.0 提供额外玩家列表和命令补全过滤；基础隐身无需数据包插件 |
| 独立维护 | 已移除上游更新检查及提醒；本插件不依赖 ProtocolLib |

已验证目标为 **Shiroha 26.2-DEV-28fe077 / Java 25**，编译 API 为 Folia `26.2.build.5-beta`。其他核心、版本或完整生产插件组合需单独验证。

## 安装和升级

从 Releases 下载 `SuperVanish-6.3.0-6.jar`，放入服务器 `plugins` 目录，保留已有 `plugins/SuperVanish` 配置目录。启动后等待控制台出现 `SuperVanish Folia ready`。

在线替换必须按顺序执行：

1. 让所有在线隐身玩家显形或离线。
2. 执行 `plugman unload SuperVanish`，等待确认卸载完成。
3. 将旧 JAR 移出 `plugins`，放入新版 JAR。
4. 执行 `plugman load SuperVanish-6.3.0-6`，等待初始化完成。

`/sv reload`、配置重建和受控卸载在仍有在线隐身玩家时会拒绝执行，防止维护期间暴露玩家位置。直接覆盖磁盘 JAR 不会更新内存中的旧实例。详细命令、权限、消息开关和维护边界见 [使用说明](使用说明.md)。

## 从源码构建

需要 JDK 25。按 [编译依赖清单](libs/README.md) 与 [测试依赖清单](test-libs/README.md) 将对应 JAR 放到本地 `libs/` 和 `test-libs/`，再运行 Gradle Wrapper。首次运行 Wrapper 需要下载 Gradle 9.6.0。依赖 JAR 由构建者准备，插件成品不内嵌这些依赖。

```powershell
$env:JAVA_HOME = 'D:\java' # 改成自己的 JDK 25 路径
.\gradlew.bat --no-daemon clean check jar
```

产物为 `build/libs/SuperVanish-6.3.0-6.jar`。`check` 运行三个 main 入口回归程序，覆盖汉化迁移、存储与刷怪监听生命周期，包括 200 次并发存储更新和 2,000 次并发状态变更；JUnit 测试用例数量为 0。附带的 `test-libs` 仅用于回归程序，不进入插件运行依赖。

## 验证与发布

6.3.0-6 在没有在线隐身玩家时撤下刷怪笼预事件订阅，避免本插件单独使共享的自然预生成监听表保持非空；首位隐身或保存隐身状态的玩家上线时恢复，最后一位显形或离线时撤下。保留刷怪笼矿车支持、原取消时机、自然刷怪和骷髅马陷阱规则；不修改核心，不新增定时器或配置键。其他插件仍占用共享表或有人隐身时，不承诺消除这项核心对象分配。

2026-11-11 对照使用 Shiroha/mizuki `26.2-DEV-96ca96b` 与 Java 25.0.3。当前构建包含新的刷怪业务与监听登记回归，运行记录与收益条件见 [6.3.0-6 验证记录](docs/6.3.0-6验证记录.md)。

```text
SuperVanish-6.3.0-6.jar
SHA-256: 98EFE8ED1C61BFC417DF62B5782E628A7DBC78B0E1D4FA4E2937161B033F59A9
```

Releases 提供插件、源码包和校验清单。源码包使用同一份依赖清单。
