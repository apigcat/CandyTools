# CandyTools v12.0(已更新至14.5)

## 使用声明

1. **使用声明**：本软件仅供大家随意下载和使用，完全免费。
2. **版权限制**：严禁任何形式的盗卖与二次修改，请尊重开发者的劳动成果。
3. **开发心声**：开发这款工具属实不易，特别是系统底层数据的精准抓取、各项指标的闭环逻辑，以及整体 UI 的美化打磨，都耗费了大量心血和排坑时间。
4. **关于打赏**：软件没有任何强制收费，如果你喜欢本作品，欢迎随意打赏支持，感谢理解与支持。


> 酷安：<https://www.coolapk.com/u/3988811>

---
## 一、软件定位

CandyTools 是一款面向三星设备的系统工具，把散落在设置深处、ServiceMode、AppOps 里的能力集中到一个界面里。全部功能无需联网、无广告、无后台常驻服务，基于 root 一次性执行命令。

- 包名 `com.candy.tools`
- versionCode 166 / versionName 14.5
- minSdk 21 / targetSdk 33
- 官方 APK 使用固定签名，**仓库不含签名文件**。自行构建请生成自己的密钥，签名不同，安装前需先卸载官方版

## 二、主界面四项核心功能

### 1. 充电状态

- 单键切换 `settings put system pass_through 0/1`，**不直接写内核节点**（写 `normal_mode_bypass` 会导致死机重启）
- 切换采用「真空隔离法」，规避三星 45W 充电协议死锁（`IBUSUCP` 欠流保护），详见 [BUG_REPORT.md](BUG_REPORT.md)
- 电池条下方居中显示充电状态与容量：**未充电** / **普通充电中** / **旁路充电中** `· 3949 mAh`
- 自绘电池条 `BatteryBarView`：未插电按真实电量填充；普通充电做生长动画；旁路充电固定长度 + 流光

#### 开启旁路

```bash
pass_through 1          # 先进旁路（电池断开）
sleep 1.5
adaptive_fast_charging 0  # 旁路态下降功率，此时不触发协商
super_fast_charging 0
```

先切旁路再关快充。旁路态下修改开关不会触发 PD 协商，45W 旁路的间歇性断流随之消除。

#### 切回普通充电
*********************{这里之所以这么绕圈 是因为45W对三星S25U存在断流BUG,实验得出数据是加速充电开关切换旁路充电触发}

```bash
adaptive_fast_charging 1   # 旁路态预置快充（系统暂不协商）
super_fast_charging 1
sleep 1
pass_through 0             # 退出旁路（协议卡在低功率，不断流）
sleep 1.5
dumpsys battery unplug     # 断电，冻结协议
sleep 4
dumpsys battery reset      # 复位，一次性完成 45W 握手
```

关键：**快充状态在协议冻结前已确立**，复位后 PMIC 首次协商即为目标功率，全程不存在「先 5V 再升压」的切换动作。

整串命令在同一条 `su -c` 内串行执行，`sleep` 由 root shell 执行，避免多次 `Shell.su()` 带来的时序抖动。

### 2. 电池健康

每秒读取，实时显示：

| 指标 | 来源 |
|---|---|
| 健康度 / 循环次数 / 电池日期 | `dumpsys battery` 档案数据 |
| 温度 / 电压 / 电流 / 容量 | `BatteryManager` 系统 API（不调用 su，避免插线后反复 fork shell 造成断流） |

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
| 冰冻 | 全部 / 用户 / 系统三段，`pm disable-user --user 0` |
| 备份 | 导出全部冰冻与卸载列表为 XML |
| 恢复 | 冰冻列表 / 卸载列表两段，各自全选执行 |

系统应用按 APK 所在分区判定（`/system`、`/system_ext`、`/product`、`/vendor`、`/odm`、`/apex`、`/prism`、`/opt`），商店更新过的预装应用不会漏算。

**冰冻页三态显示**（读 `dumpsys package` 的 `enabled` 值）：

| enabled | 状态 | 颜色 |
|---|---|---|
| 3 | 已冰冻 | 红色 |
| 4 | 已睡眠 | 蓝色 |
| 其他 | 未管理 | 绿色 |

- 筛选：默认 / 已冻 / 未冻 / 睡眠（睡眠仅在用户程序页签可见，**只读**）
- 动作：解冻、冰冻；睡眠筛选下隐藏动作按钮
- 首屏先渲染列表，睡眠状态在后台补扫，不会卡顿

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
| 12.1 | 电池数据改用 BatteryManager，取消每秒 su 轮询，修复插线断流 |
| 12.2 | 尝试直写内核节点 `normal_mode_bypass`（**导致死机，已回滚**） |
| 12.3 ~ 12.5 | 回滚节点写入，尝试纯 Settings 通道（受框架限制不可用） |
| 12.6 | 恢复 settings + su 写入方案，只保留轮询修复 |
| 12.7 | 清理未使用常量 |
| 12.8 ~ 12.13 | 冰冻页新增睡眠三态、备份加入睡眠、睡眠筛选与动作、页签限制 |
| 12.14 ~ 12.16 | 标题改「充电状态」，快充联动，切换后模拟拔插复位充电链路 |
| 12.17 | 切换进度提示；移除睡眠执行与睡眠备份 |
| 12.18 ~ 12.19 | 进度改为电池上方终端文字进度，去掉图形进度条 |
| 12.20 ~ 12.21 | 提示文案修正，进度改为匀速推进 |
| 12.22 | 清理未使用的字符串、颜色、样式 |
| 12.23 | 充电状态文字移到电池正下方居中 |
| 12.24 ~ 12.27 | 备份页冰冻计数与冰冻页对齐，剔除睡眠与已卸载残留 |
| 12.28 | 移除睡眠选项卡及全部相关代码，冰冻状态改用单条 dumpsys 过滤 |
| 12.29 ~ 12.31 | 备份数据源调整与回滚，恢复 appops 与 pm list 稳定方案 |
| 13.0 | 最终清理：删除全部未使用字段，确认无残留代码 |
| 14.0 | 旁路模块回归纯 pass_through；容量并入健康度行；修复冰冻判定漏掉 GMS |
| 14.1 | 引入真空隔离法：开关快充与触发协商分离 |
| 14.2 | 切换命令合并为单条 su -c；冰冻判定排除深度睡眠；容量仅在状态文字 |
| 14.3 | 修正顺序：先退出旁路再断电复位 |
| 14.4 | 修复状态文字未显示容量 |
| 14.5 | 清理累计残留：未使用字段、失效的 settings 写入通道、废弃布局区块 |

## 14.5 更新内容

### 充电切换（核心修复）

- 采用真空隔离法规避三星 45W 协议死锁：开关快充与触发 PD 协商彻底分离
- 开启旁路：先进旁路再关快充，消除 45W 旁路间歇性断流
- 关闭旁路：旁路态预置快充 → 退出旁路 → 断电 → 复位，一次性完成 45W 握手
- 实测结果：**全程不断流，恢复超级快充 2.0**

### 冰冻判定修复

- `pm list packages -d` 会把深度睡眠（`enabled=4`）和已卸载残留算进来
- 现按三步过滤：`pm -d` 为主 → 剔除 `enabled=4` → `retainAll(已安装)`
- 修复 GMS 等通过 `disabledComponents` 禁用的应用被漏掉的问题
- 冰冻页、备份页、恢复页三处判定统一

### 界面

- 电池条下方居中显示：`普通充电中 · 3949 mAh`
- 删除电池条内部的状态文字，空间让给百分比与动画
- 电池健康第一行：健康度 + 容量并列

### 清理

- 删除未使用字段：`SWITCH_EST_MS`、`progressTicker`、`switchBox`、`switchText`、`NODE_OFF_MS`、`NODE_ON_MS`、`prefs`
- 删除失效方法：`writeSystemSetting`、`canWriteSettings`、`readSystemSetting`、`promptPermission`、`WriteResult`
- 删除废弃布局：`switch_status_box` 进度区块
- 全工程扫描：无未使用的私有方法或字段

## 六、权限

| 权限 | 用途 |
|---|---|
| `WRITE_SETTINGS` | 旁路开关、快充开关 |
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

构建脚本会自动创建空的 `assets/` 目录（aapt2 的 `-A` 参数要求目录存在）。

### 签名

签名口令不写入仓库，通过环境变量传入：

```powershell
$env:CANDY_KS_PASS  = "你的 keystore 密码"
$env:CANDY_KEY_PASS = "你的 key 密码"
$env:CANDY_KS       = "D:\path\to\your.jks"   # 可选，默认 keystore\candy.jks
$env:CANDY_KS_ALIAS = "your_alias"               # 可选，默认 candy
```

未设置口令时脚本只输出未签名 APK，不影响构建流程。

自行生成密钥：

```powershell
keytool -genkeypair -keystore my.jks -alias candy -keyalg RSA -keysize 2048 `
    -validity 10000 -dname "CN=Your Name,O=YourOrg,C=CN"
```

## 八、目录

```text
src/com/candy/tools/
    MainActivity.java            充电状态 / 电池 / 锁频 / 网络质量 / 工具箱入口
    BatteryBarView.java          横向电池条与切换进度（自绘）
    ToolsActivity.java           工具箱列表
    PermissionHubActivity.java   权限管理入口
    ClipboardActivity.java       权限页基类
    WakeLockActivity.java        唤醒锁权限
    NetworkWakeActivity.java     网络唤醒锁
    PermBackupActivity.java      权限备份
    PermRestoreActivity.java     权限恢复
    AppControlActivity.java      卸载冰冻入口
    AppOpsListActivity.java      卸载 / 冰冻列表（三态）
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
keystore/                        签名（仓库不包含）
```

## 九、免责声明

本软件仅用于调试自己的设备。旁路充电、锁频、卸载冻结与 Hosts 操作涉及系统底层，可能导致无服务、功能异常甚至需要刷机恢复，风险自负。使用前请确认已了解对应恢复方式。



-----------------以下是展示图片，主题是暗色 可以切换浅色------------------




<img width="239" height="500" alt="2" src="https://github.com/user-attachments/assets/15abcf83-fe05-4f1c-b100-7c97c1b21b4c" />


<img width="239" height="500" alt="3" src="https://github.com/user-attachments/assets/a46bb601-77fc-46b2-b2d4-3a148aaae2e5" />


<img width="239" height="500" alt="4" src="https://github.com/user-attachments/assets/8ead2699-e657-44fc-bf10-a30b2877131b" />


<img width="239" height="500" alt="5" src="https://github.com/user-attachments/assets/570fa3eb-97b4-4a04-899c-6bd95dff0778" />


<img width="239" height="500" alt="6" src="https://github.com/user-attachments/assets/861a2f6a-a0c3-4e3e-9cc8-43747dce9303" />


<img width="239" height="500" alt="7" src="https://github.com/user-attachments/assets/c2ecfba3-7487-4fe0-a631-84b1eb89d6b4" />


<img width="239" height="500" alt="1" src="https://github.com/user-attachments/assets/0b3e795a-3b0c-492f-8913-84125578fec1" />


