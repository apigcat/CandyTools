# CandyTools v2.3

三星设备的小工具：**旁路充电**、**电池健康与实时数据**、**频段固定（锁频）**、**网络质量**。

> 作者：Candy dad's · GitHub：<https://github.com/apigcat>

## 安装

```bash
adb install -r CandyTools.apk
```

- 包名 `com.candy.tools`，versionCode 14 / versionName 2.3，minSdk 21，targetSdk 33
- 签名固定为工程内 `keystore/candy.jks`（alias `candy`，密码 `android`），重新打包可直接覆盖安装
- v1 + v2 + v3 签名

## 界面

```
CandyTools                        [说明] [日志] [关于]

⚡ 旁路充电 (Bypass Charging)
┌────────────────────────────────────────┐
│           已启用旁路充电 / 未启用旁路充电   │
└────────────────────────────────────────┘
                    已关闭系统自动调节亮度（仅启用时显示）
────────────────────────────────────────────
🔋 电池健康 (Battery Health)
[健康度 100 %]      [循环次数 295 次]
电池日期                              2025-10-24
电池温度                                26.7 °C
电池电压                               4.035 V
电池电流                              -132.8 mA
电池容量                                3542 mAh

📡 频段固定 (Band Locking)
[临时锁频]                [超级锁频]
软锁·软件级·重启恢复        硬锁·基带级·重启保留

中国电信 · LTE · B3 · EARFCN 1850
┌────────────────────────────────────────┐
│               网络质量 优                │
└────────────────────────────────────────┘
                大幅提升游戏体验 告别460ms
```

所有可点区域统一为「描边 + 青绿字」的 ghost 风格，三块板块标题为 16sp 加粗、下方 30×2dp 霓虹短横线。

## 1. ⚡ 旁路充电

单键切换，**按钮不变色**，只换文字：

| 状态 | 文案 | 底层 |
|---|---|---|
| 关闭 | 未启用旁路充电 | `settings put system pass_through 0` |
| 开启 | 已启用旁路充电 | `settings put system pass_through 1` |

亮度联动全自动：开启时把 `screen_brightness_mode` 置 0 并记住原值，关闭时恢复。

写入策略：`WRITE_SETTINGS` → 失败自动 `su -c settings put ...`，每次回读校验。

## 2. 🔋 电池健康

**实时四行（1 Hz）**：一个常驻 `su` shell 循环读 sysfs，进程只起一次：

```sh
/sys/class/power_supply/battery/current_now      # µA  -> mA
/sys/class/power_supply/battery/voltage_now      # µV  -> V
/sys/class/power_supply/battery/temp             # 0.1℃ -> ℃
/sys/class/power_supply/battery/charge_counter   # µAh -> mAh
```

进页面启动、退到后台停止。无 root 时四行显示 `—` 并提示需 root。
**电流在旁路生效（|I| < 150 mA 且旁路开启）时变亮青色**，其余情况跟其他数值一样是主题青绿。

**档案数据**（`dumpsys battery`，进入页面读一次）：

| 展示 | 取值 | 换算 |
|---|---|---|
| 健康度 | `mSavedBatteryAsoc`（缺失用 `mSavedBatteryBsoh`） | `100` → `100 %` |
| 循环次数 | `mSavedBatteryUsage` | `29542 / 100` → `295 次` |
| 电池日期 | `FirstUseDate` | `20251024` → `2025-10-24` |

点「健康度 / 循环次数」可查看全部 `dumpsys battery` 原始键值。

## 3. 📡 频段固定

| 按钮 | 方式 | 数据来源 |
|---|---|---|
| 临时锁频 | 软锁，图形界面勾选 | `com.samsung.android.app.telephonyui/.hiddennetworksetting.MainActivity` |
| 超级锁频 | 硬锁，ServiceMode 数字菜单 | `android_secret_code://27663368378` 广播 |

**实测硬锁路径**（Samsung One UI）：

```
[5] RF TEST
 → [1] BAND SELECTION
   → [1] BAND SELECTION SIM 1-CHC（双卡选对应卡）
     → [3] LTE Band Preference（5G 选 [4] NR5G Band Preference）
       → 勾选目标频段
         → [C] Apply band configuration
```

同页快捷键：`[A]` 清空全部、`[B]` 全选、`[D]` BLOCK SET BY AP。**恢复默认 = [B] → [C]**。改完页面底部 `Selected band element` 会显示当前选择（如 `LTE: 1 3 5`）。

## 4. 📶 网络质量

- 高频：每 2 秒 `dumpsys telephony.registry | grep mSignalStrength`（单行，开销小）
- 低频：每 30 秒 `grep mServiceState`，取运营商 / 制式 / 频段 / EARFCN
- 双卡只取 `mLte=CellSignalStrengthLte` 且 `rsrp` 有效的那一条，避免被副卡「无服务」覆盖

判定：`RSRP ≥ -90` 且 `SNR ≥ 8` → 优；`RSRP ≥ -105` → 中；其余 → 差。

显示：大块只换文字，**只有最后一个字变色**（青=优、黄=中、红=差）；运营商行与电池数值统一用主题青绿。点击大块可看逐项原始值：

```
运营商：中国电信
制式：LTE
频段：B3
频点：EARFCN 1850
RSRP：-92 dBm
RSRQ：-4 dB
SNR：12 dB
信号等级：4/4
判定：优
```

## 说明 / 日志 / 关于

- **说明**：整页可滚动，讲解旁路、电池各项换算、网络质量指标含义、频段知识与两种锁频方法。
- **日志**：弹窗查看运行记录，可选中复制。
- **关于**：作者、GitHub 链接、赞赏码。

赞赏码在 `res/drawable-nodpi/donate.png`，替换方法：把自己的二维码放到 `tools/source_qr.png` 后执行

```bash
python tools/make_donate.py
```

脚本会把近白背景换成 `#EAFAF5`、裁圆角并输出 640×640。

## 权限

| 功能 | 无 root | 有 root |
|---|---|---|
| 读/写旁路、亮度 | 需「修改系统设置」，部分固件仍拒绝 | 可行 |
| 电池实时四行 | 不可用 | 1 Hz |
| 健康度 / 循环 / 电池日期 | 不可用（`dumpsys` 受限） | 可行 |
| 网络质量 / 频段信息 | 不可用 | 可读 |
| 锁频 | 依赖组件是否导出 | 可行 |

应用不申请网络权限，所有操作都在本机完成。

## 构建

无 Gradle，`aapt2 + javac + d8 + zipalign + apksigner`：

```powershell
powershell -ExecutionPolicy Bypass -File build.ps1
powershell -ExecutionPolicy Bypass -File build.ps1 -Sdk "D:\Android\Sdk" -Jdk "C:\Java\jdk17"
```

需要 JDK 17 + `build-tools;34.0.0` + `platforms;android-34`。

> `aapt2` 不认中文路径。工程放在中文目录时先复制到英文路径（如 `C:\rilbuild\candy`）再构建。

## 目录

```
src/com/candy/tools/
    MainActivity.java     旁路 / 电池 / 锁频 / 网络质量 / 日志
    AboutActivity.java    关于页
    HelpActivity.java     说明页
    Shell.java            su 执行器（一次性 + 常驻流）
    SignalMonitor.java    信号解析与轮询
res/layout/               主界面、说明页、关于页
res/values/               strings / colors（赛博配色）/ styles
res/drawable/             卡片、霓虹描边按钮
res/drawable-nodpi/       donate.png
res/mipmap-*/             ic_launcher.png
tools/make_icon.py        图标生成（信号 + 扳手）
tools/make_donate.py      赞赏码合成
build.ps1                 构建脚本
```

## 免责声明

仅用于调试自己的设备。旁路充电、锁频与 ServiceMode 操作涉及系统底层，风险自负。
