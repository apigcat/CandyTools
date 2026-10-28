# CandyTools v3.6

三星设备的小工具：**旁路充电**、**电池健康**、**频段固定（锁频）**、**网络质量**。

## 安装

```bash
adb install -r CandyTools.apk
```

- 包名 `com.candy.tools`，versionCode 22 / versionName 3.6，minSdk 21，targetSdk 33
- 签名固定为 `keystore/candy.jks`
- 签名方案 v1 + v2 + v3

## 后台与耗电

**不使用常驻 shell**。旧版用 `su -c "while true; ..."` 轮询，父进程一死就会留下 PPID=1 的孤儿进程持续耗电，这是结构性问题。现在：

- 电池：进页面后每秒发一条**一次性**命令读 sysfs，进程读完即退；离开页面停止
- 网络质量：点一次「刷新」跑一次 `dumpsys`，几毫秒结束，进入页面自动刷一次
- 应用启动时执行一次 `pkill -f 'dumpsys telephony.registry'` 与 `pkill -f 'power_supply/battery'`，清理升级用户可能残留的旧孤儿进程

## 界面

```
CandyTools                       [说明] [日志] [关于]

┌ ⚡ 旁路充电 (Bypass Charging) ─────────┐
│  [      未启用旁路充电 / 已启用旁路充电     ]  │
│              已关闭系统自动调节亮度（仅启用时）   │
└────────────────────────────────────────┘

┌ 🔋 电池健康 (Battery Health) ───────────┐
│  [健康度 100 %]   [循环次数 295 次]        │
│  电池日期                        2025-10-24 │
│  电池温度                          26.7 °C │
│  电池电压                         4.035 V │
│  电池电流                        -132.8 mA │
│  电池容量                          3542 mAh │
└────────────────────────────────────────┘

┌ 📡 频段固定 (Band Locking) ─────────────┐
│  [临时锁频]              [超级锁频]        │
│  软锁·软件级·重启恢复     硬锁·基带级·重启保留 │
│  中国电信 · LTE · B3 · EARFCN 1850  [刷新] │
│  信号强度良好，延迟稳定，畅享低延迟体验        │
│  最后更新 18:52:31                        │
└────────────────────────────────────────┘
```

三个板块各自独立成卡片，间距统一 12dp；卡片圆角 24dp，可点区域统一「填充卡片 / 描边按钮 + 主色文字」，点按有水波纹。

## 1. ⚡ 旁路充电

单键切换，按钮不变色，只换文字：

| 状态 | 文案 | 底层 |
|---|---|---|
| 关闭 | 未启用旁路充电 | `settings put system pass_through 0` |
| 开启 | 已启用旁路充电 | `settings put system pass_through 1` |

亮度联动全自动：开启时把 `screen_brightness_mode` 置 0 并记住原值，关闭时恢复。

写入策略：`WRITE_SETTINGS` → 失败自动 `su -c settings put ...`，每次回读校验。

## 2. 🔋 电池健康

实时四行：每秒一条一次性命令读取 sysfs（不常驻）：

```sh
/sys/class/power_supply/battery/current_now      # µA  -> mA
/sys/class/power_supply/battery/voltage_now      # µV  -> V
/sys/class/power_supply/battery/temp             # 0.1℃ -> ℃
/sys/class/power_supply/battery/charge_counter   # µAh -> mAh
```

无 root 时四行显示 `—`。**电流在旁路生效（|I| < 150 mA 且旁路开启）时变亮青色**，其余情况为主题色。

档案数据（`dumpsys battery`，进页面读一次）：

| 展示 | 取值 | 换算 |
|---|---|---|
| 健康度 | `mSavedBatteryAsoc`（缺失用 `mSavedBatteryBsoh`） | `100` → `100 %` |
| 循环次数 | `mSavedBatteryUsage` | `29542 / 100` → `295 次` |
| 电池日期 | `FirstUseDate` | `20251024` → `2025-10-24` |

点「健康度 / 循环次数」可查看全部原始键值。

## 3. 📡 频段固定

| 按钮 | 方式 | 数据来源 |
|---|---|---|
| 临时锁频 | 软锁，图形界面勾选 | `com.samsung.android.app.telephonyui/.hiddennetworksetting.MainActivity` |
| 超级锁频 | 硬锁，ServiceMode 数字菜单 | `android_secret_code://27663368378` 广播 |

**实测硬锁路径**：

```
[5] RF TEST
 → [1] BAND SELECTION
   → [1] BAND SELECTION SIM 1-CHC（双卡选对应卡）
     → [3] LTE Band Preference（5G 选 [4] NR5G Band Preference）
       → 勾选目标频段
         → [C] Apply band configuration
```

同页快捷键：`[A]` 清空、`[B]` 全选、`[D]` BLOCK SET BY AP。**恢复默认 = [B] → [C]**。

常用频段（国内）：

- 电信：B1 城市容量 / B3 均衡主力 / B5 低频穿墙；5G n1、n78
- 联通：B1 城区 / B3 主力 / B8 低频广覆盖；5G n1、n78
- 移动：B3 均衡 / B8 广覆盖 / B34·B39 城市 TDD / B40 室内热点 / B41 大带宽；5G n41、n79、n28

## 4. 📶 网络质量

**手动刷新 + 时间戳**，不做轮询：

- 点「刷新」→ 一次 `dumpsys telephony.registry | grep -E 'mSignalStrength|mServiceState'` → 解析 → 更新 → 记录「最后更新」，进程退出
- 进入页面自动刷一次，之后靠手动；连续点击有防抖

判定：`RSRP ≥ -90` 且 `SNR ≥ 8` → 优；`RSRP ≥ -105` → 中；其余 → 差。

| 质量 | 文案 | 颜色 |
|---|---|---|
| 优 | 信号强度良好，延迟稳定，畅享低延迟体验 | 青绿 |
| 中 | 信号强度一般，延迟存在波动，建议谨慎操作 | 琥珀 |
| 差 | 信号强度不足，高延迟风险，建议调整位置 | 红 |
| 未知 | 点击「刷新」获取当前网络质量 | 灰 |

点文案可看逐项原始值（运营商 / 制式 / 频段 / EARFCN / RSRP / RSRQ / SNR / 等级 / 判定）。

## 主题

- 三档：跟随系统 / 浅色 / 深色，入口在关于页
- 采用 Material 3 配色：浅色背景 `#FEF7FF`、主色 `#6750A4`；深色背景 `#141218`、主色 `#D0BCFF`
- 实现：`ThemeHelper` 在 `attachBaseContext` 里改 `uiMode`，配合 `values` 与 `values-night` 两套颜色；切换后以 `CLEAR_TOP` 重启主界面，三个页面立即换肤

## 检查更新

- 只在**点击「检查更新」时**请求一次，没有任何后台检查或轮询
- 数据源是仓库根目录的 `version.json`，先试 jsDelivr CDN，失败再试 raw.githubusercontent
- 有新版本弹窗显示版本号与更新说明，「去下载」用浏览器打开 Releases 页
- 因为要发请求，manifest 申请了 `INTERNET`；不使用时不产生任何网络流量

`version.json` 格式：

```json
{
  "versionCode": 22,
  "versionName": "3.6",
  "notes": "本次更新内容，一行一条",
  "url": "https://github.com/apigcat/CandyTools/releases/latest"
}
```

## 发版流程

1. 改代码，更新 `AndroidManifest.xml` 与 `build.ps1` 的 `versionCode` / `versionName`
2. 执行 `build.ps1` 打包
3. 把 APK 上传到 GitHub Releases
4. 更新仓库根目录的 `version.json`（`versionCode` 必须比上一版大）并提交

## 关于页

- 作者 / 仓库 / 酷安 三行，颜色与标题一致，均可点击
- 主题切换（跟随系统 / 浅色 / 深色）
- 「请Candy dad’s☕」→ 弹出赞赏码大图，可**保存到相册**（Android 10+ 走 MediaStore `IS_PENDING`，10 以下写 `Pictures/CandyTools` 并申请存储权限），保存成功提示「你的支持是我继续开发的动力❤」
- 检查更新

赞赏码位于 `res/drawable-nodpi/donate.png`，替换方法：把自己的二维码放到 `tools/source_qr.png` 后执行

```bash
python tools/make_donate.py
```

## 权限

| 权限 | 用途 |
|---|---|
| `WRITE_SETTINGS` | 写旁路开关、亮度模式 |
| `INTERNET` | 仅在点击「检查更新」时访问 version.json |
| `WRITE_EXTERNAL_STORAGE`（maxSdkVersion 28） | Android 9 及以下保存赞赏码 |

电池数据、信号数据需要 root（`dumpsys`、sysfs 仅 shell/root 可读）。

## 构建

无 Gradle：

```powershell
powershell -ExecutionPolicy Bypass -File build.ps1
powershell -ExecutionPolicy Bypass -File build.ps1 -Sdk "D:\Android\Sdk" -Jdk "C:\Java\jdk17"
```

需要 JDK 17 + `build-tools;34.0.0` + `platforms;android-34`。

> `aapt2` 不认中文路径，工程在中文目录时先复制到英文路径（如 `C:\rilbuild\candy`）再构建。

## 目录

```
src/com/candy/tools/
    MainActivity.java     旁路 / 电池 / 锁频 / 网络质量 / 日志
    AboutActivity.java    关于页、主题切换、打赏保存、检查更新
    HelpActivity.java     说明页
    Shell.java            一次性 su 执行器
    SignalMonitor.java    信号解析（单次调用）
    ThemeHelper.java      主题模式与 uiMode 包装
    UpdateChecker.java    检查更新（jsDelivr → raw 兜底）
res/layout/               主界面、说明页、关于页
res/values/               浅色配色与样式
res/values-night/         深色配色与样式
res/drawable/             卡片、按钮、chip
res/drawable-nodpi/       donate.png
res/mipmap-*/             图标
tools/                    图标、赞赏码生成与资源补丁脚本
version.json              检查更新的数据源
build.ps1                 构建脚本
```

## 免责声明

仅用于调试自己的设备。旁路充电、锁频与 ServiceMode 操作涉及系统底层，风险自负。
