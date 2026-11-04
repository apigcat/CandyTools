# CandyTools v12.0

## 本软件完全开源 
## 测试机型三星S25U 

1. **使用声明**：本软件仅供大家随意下载和使用，完全免费。
2. **版权限制**：严禁任何形式的盗卖与二次修改，请尊重开发者的劳动成果。
3. **开发心声**：开发这款工具属实不易，特别是系统底层数据的精准抓取、各项指标的闭环逻辑，以及整体 UI 的美化打磨，都耗费了大量心血和排坑时间。
4. **关于打赏**：软件没有任何强制收费，如果你喜欢本作品，欢迎随意打赏支持，感谢理解与支持。

> 作者：Candy dad's · 仓库：<https://github.com/apigcat/CandyTools> · 酷安：<https://www.coolapk.com/u/3988811>

---

## 一、软件定位

CandyTools 是一款面向三星设备的系统工具，把散落在设置深处、ServiceMode、AppOps 里的能力集中到一个界面里。全部功能无需联网、无广告、无后台常驻服务，基于 root 一次性执行命令。

- 包名 `com.candy.tools`
- versionCode 119 / versionName 12.0
- minSdk 21 / targetSdk 33
- 签名固定 `keystore/candy.jks`（alias `candy`，密码 `android`），可直接覆盖安装

## 二、主界面四项核心功能

### 1. 旁路充电

- 单键切换 `settings put system pass_through 0/1`，按键不变色，只改文字
- 自绘电池条 `BatteryBarView`：未插电按真实电量填充；普通充电做增长动画；旁路充电固定长度并带流光
- 亮度联动：开启时记住原亮度并切到手动模式，关闭时恢复

### 2. 电池健康

每秒读取 sysfs，实时显示：

| 指标 | 来源 |
|---|---|
| 健康度 / 循环次数 / 电池日期 | `dumpsys battery` 档案数据 |
| 温度 / 电压 / 电流 / 容量 | `/sys/class/power_supply/battery/*` |

### 3. 频段固定

| 按钮 | 副标题 | 行为 |
|---|---|---|
| 临时锁频 | 单网频锁 | 打开隐藏的 BandSelection，一次只能锁一种网络模式或该模式下的部分频段，重启恢复 |
| 超级锁频 | 多网频锁 | 进入 ServiceMode，可同时锁多个频段或多种模式，重启保留 |

### 4. 网络质量

手动刷新并记录时间戳，按 RSRP 与 SNR 判定优、中、差。

## 三、工具箱

**长按标题里的 `Tools` 直接进入**，无中间弹窗。

### 权限管理

| 板块 | appop | 作用 |
|---|---|---|
| 剪贴板权限 | `READ_CLIPBOARD` | 读取剪贴板 |
| 唤醒锁权限 | `WAKE_LOCK` | 保持唤醒 |
| 网络唤醒锁 | `RUN_ANY_IN_BACKGROUND` | 限制后台活动，减少后台联网与网络唤醒 |
| 备份 | — | 三个 appop 的禁用名单导出到同一个 XML |
| 恢复 | — | 分三段列表，各自全选、只执行禁用 |

页面交互统一：用户/系统页签、搜索、筛选（默认/禁用/允许）、全选、批量执行、二次确认；状态按颜色区分，确认弹窗区分「已禁用 X 项 / 新增 Y 项」。

### 卸载冰冻

| 板块 | 作用 |
|---|---|
| 卸载 | 仅系统应用，`pm uninstall --user 0`，可恢复 |
| 冰冻 | 全部/用户/系统三段，`pm disable-user --user 0` |
| 备份 | 导出全部冰冻与卸载列表为 XML |
| 恢复 | 冰冻列表 / 卸载列表两段，各自全选执行 |

系统应用按 APK 所在分区判定（`/system`、`/system_ext`、`/product`、`/vendor`、`/odm`、`/apex`、`/prism`、`/opt`），商店更新过的预装应用不会漏算。恢复弹窗按现状着色，红色为已到位、绿色为待变更。

### 系统监控

- **CPU / 内存**：双圆环，用户（绿）与系统（紫）分别显示占比，带上升动画
- **网络连接**：IPv4 / IPv6 双圆环显示连接个数，点开为选项卡列表
  - 按进程分块，块内标注「入站 / 出站」，显示 `本端 → 对端`
  - 只保留 `ESTAB`，入站按本端端口是否在监听列表判断
  - 每块可「终止」，系统 UID 灰显、不提供按钮
- 无自动轮询，进入页面或点「刷新」才采样，退出即停

### Hosts 屏蔽

- 编辑 hosts 后点「写入模块」，写入 `/data/adb/modules/candytools_hosts/`
- 重启后由 KernelSU / Magisk 开机挂载，长期生效
- 支持备份、恢复、移除模块
- 不提供运行时 bind，避免「重启失效」的误解

```text
/data/adb/modules/candytools_hosts/
├── module.prop
└── system/etc/hosts
```

## 四、其他能力

- **主题**：跟随系统 / 浅色 / 深色，`values` 与 `values-night` 双配色
- **关于页**：作者、仓库、酷安入口，赞赏码弹窗与保存
- **检查更新**：点击时请求一次 `version.json`，支持 jsDelivr 与 GitHub 双源
- **无广告、无统计、无后台服务**：所有 root 操作均为一次性 `su -c`

## 五、版本演进摘要

| 版本 | 关键变化 |
|---|---|
| 4.0 | 主功能成型：旁路、电池健康、锁频、网络质量、电池条、彩蛋入口 |
| 5.0 | 小工具初版：剪贴板权限、网络；清理无用字符串与死方法 |
| 6.4 | 卸载与冰冻拆分为四板块，系统应用按分区判定，数量与备份对齐 |
| 6.12 | 加入 NFC 测试能力（后续因兼容性移除） |
| 6.15 | 权限管理合并为一个大模块，新增权限备份/恢复与筛选 |
| 7.0 | 删除 NFC，新增 Hosts 屏蔽，工具页改名 CandyTools |
| 8.x | 系统监控上线：CPU、内存、网络连接，双圆环与选项卡 |
| 9.x | 权限页改版：全选、筛选、批量执行、状态配色；连接区分入站出站 |
| 10.x | 大扫除：删除无效权限模块、清理死代码、备份接入网络唤醒锁 |
| 11.x | 尝试耗电统计（batterystats 解析、容量档位、系统 UID 名称） |
| 12.0 | 移除耗电统计，系统监控收敛为 CPU / 内存 / 网络连接三块 |

## 六、权限

| 权限 | 用途 |
|---|---|
| `WRITE_SETTINGS` | 旁路开关、亮度模式 |
| `INTERNET` | 仅在「检查更新」时访问 version.json |
| `QUERY_ALL_PACKAGES` | 权限与卸载冰冻页面需要列出全部应用 |
| `WRITE_EXTERNAL_STORAGE`（max 28） | Android 9 及以下保存赞赏码 |

电池、appops、`pm`、`ss`、`ps` 等操作需要 root。

## 七、构建

```powershell
# aapt2 不认中文路径，先镜像到英文目录
robocopy . C:\rilbuild\candy /MIR /NFL /NDL /NJH /NJS /NP | Out-Null
powershell -ExecutionPolicy Bypass -File C:\rilbuild\candy\build.ps1
```

需要 JDK 17、`build-tools;34.0.0`、`platforms;android-34`。

改版本号要同步四处：`AndroidManifest.xml`、`build.ps1`、`res/values/strings.xml`、`version.json`。

## 八、目录

```text
src/com/candy/tools/
    MainActivity.java            旁路 / 电池 / 锁频 / 网络质量 / 工具箱入口
    BatteryBarView.java          横向电池条（自绘）
    ToolsActivity.java           工具箱列表
    PermissionHubActivity.java   权限管理入口
    ClipboardActivity.java       权限页基类
    WakeLockActivity.java        唤醒锁权限
    NetworkWakeActivity.java     网络唤醒锁
    PermBackupActivity.java      权限备份
    PermRestoreActivity.java     权限恢复
    AppControlActivity.java      卸载冰冻入口
    AppOpsListActivity.java      卸载 / 冰冻列表
    BackupActivity.java          卸载冰冻备份
    RestoreActivity.java         卸载冰冻恢复
    SystemMonitorActivity.java   系统监控（CPU / 内存 / 网络连接）
    HostsActivity.java           Hosts 屏蔽
    NetworkActivity.java         网络质量
    AboutActivity.java           关于 / 主题 / 打赏 / 更新
    HelpActivity.java            说明页
    Shell.java                   一次性 su 执行器
    SignalMonitor.java           信号解析
    ThemeHelper.java             主题模式
    UpdateChecker.java           检查更新
res/values/                      浅色配色与字符串
res/values-night/                深色配色
assets/                          预留目录（构建参数 -A 需要）
version.json                     更新数据源
build.ps1                        构建脚本
keystore/candy.jks               签名
```

## 九、免责声明

本软件仅用于调试自己的设备。旁路充电、锁频、卸载冻结与 Hosts 操作涉及系统底层，可能导致无服务、功能异常甚至需要刷机恢复，风险自负。使用前请确认已了解对应恢复方式。
