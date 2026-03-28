# 定时嗨 真机测试报告

测试日期：2026-03-27  
测试对象：`com.getupandgetlit.dingshihai`  
测试设备：`Meizu S6 / Android 7.0 / Flyme 8.0.5.0A`  
测试方式：`adb + root shell + uiautomator dump + monkey/input`

## 1. 测试范围

本轮测试覆盖以下内容：

- 构建与安装链路
- 设备权限与 root 能力
- 应用启动与首页基础状态
- Flyme USB 安装限制排查与修复
- UI 自动化可行性验证

本轮未完整覆盖以下业务验收项：

- 创建任务
- 编辑任务
- 删除任务
- 文件选择器完整选择流程
- 全部预定 / 取消预定
- 实际音频播放
- 蓝牙可用/不可用分支
- 开机重置

原因不是代码编译失败，而是当前使用的 `adb input + uiautomator` 方案在该 Flyme 设备上未能稳定进入表单页，无法在本轮中继续完成端到端 UI 驱动。

## 2. 环境与前置检查

### 2.1 本机构建环境

- 已安装 `JDK 17`
- 已配置 Android SDK：
  - `platform 34`
  - `build-tools 34.0.0`
- 已在项目内生成 `gradlew / gradlew.bat`

### 2.2 构建结果

已执行并通过：

- `testDebugUnitTest`
- `assembleDebug`

APK 产物：

- [app-debug.apk](C:/Users/pkwccheng/Desktop/get_up_and_get_lit-main/app/build/outputs/apk/debug/app-debug.apk)

## 3. 真机安装测试

### 3.1 初始结果

首次执行常规安装失败：

- `adb install -r app-debug.apk`
- 返回：`INSTALL_FAILED_USER_RESTRICTED`

后续即使使用 root 执行：

- `su -c pm install -r /data/local/tmp/dingshihai-debug.apk`

仍返回：

- `INSTALL_FAILED_USER_RESTRICTED`

### 3.2 原因定位

在设备 `secure settings` 中发现 Flyme 的 USB 安装白名单项：

- `usb_install_item_switch=switch:1`
- `usb_install_item_com.getupandgetlit.dingshihai=定时嗨:0`

这说明 Flyme 对当前包名启用了单独的 USB 安装拦截。

### 3.3 修复动作

通过 root 修改为允许：

```text
settings put secure usb_install_item_com.getupandgetlit.dingshihai '定时嗨:1'
```

校验结果：

- `usb_install_item_com.getupandgetlit.dingshihai=定时嗨:1`

### 3.4 最终安装结果

再次安装成功：

- `pm install -r /data/local/tmp/dingshihai-debug.apk`
- 返回：`Success`

应用随后被成功拉起：

- `monkey -p com.getupandgetlit.dingshihai -c android.intent.category.LAUNCHER 1`

## 4. 权限与系统能力验证

### 4.1 运行权限

已确认授予：

- `READ_EXTERNAL_STORAGE`
- `WRITE_EXTERNAL_STORAGE`

### 4.2 声明/系统权限

已在包信息中确认存在并授予/注册关键能力：

- `RECEIVE_BOOT_COMPLETED`
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
- `BLUETOOTH`
- `BLUETOOTH_ADMIN`
- `WAKE_LOCK`

### 4.3 Root 能力

已确认可直接通过 adb shell 获取 root：

- `adb shell su -c id`
- 返回 `uid=0(root)`

这满足方案里“Root 温和增强”的设备前提。

## 5. 应用启动后首页验证

通过 `uiautomator dump` 抓取首页层级，验证到以下内容：

- 当前焦点页面：`HomeActivity`
- 文案存在：
  - `创建任务`
  - `全部预定`
  - `暂无任务`
- 首页在无数据时的初始状态正确：
  - 任务列表为空
  - 右上按钮为 `全部预定`
  - 左侧按钮为 `创建任务`

这说明以下基础需求已经在真机上得到确认：

- 应用可正常启动
- 首页能正常渲染
- 空列表状态文案正确
- 按实施包要求显示主要入口按钮

## 6. UI 自动化测试尝试结果

### 6.1 已做动作

- 通过 `adb input tap` 尝试点击首页 `创建任务`
- 点击后再次抓取 `uiautomator dump`

### 6.2 实际结果

- 页面未成功进入 `TaskFormActivity`
- 第二次抓取时返回了：
  - `ERROR: null root node returned by UiTestAutomationBridge.`

同时 dump 内容仍显示首页结构，未证明表单页已打开。

### 6.3 结论

当前这台 Flyme 设备上，基于 `adb input + uiautomator dump` 的无障碍层级驱动并不稳定，至少在本轮中不足以支撑继续完成完整业务 UI 自动化测试。

这不是业务逻辑已失败的证据，而是本轮自动化执行手段受限。

## 7. 代码与单元测试覆盖情况

以下能力已通过代码实现与本地测试/构建确认：

- 任务数据模型、运行时状态模型
- `Room` 持久化
- 任务表单校验器
- 调度时间计算器
- 前台服务、guard alarm、root 白名单尝试逻辑
- 蓝牙检查器
- 播放控制器
- 开机重置接收器
- 首页与任务表单 UI 代码

已通过的单元测试：

- [TaskDraftValidatorTest.kt](C:/Users/pkwccheng/Desktop/get_up_and_get_lit-main/app/src/test/java/com/getupandgetlit/dingshihai/TaskDraftValidatorTest.kt)
- [SchedulePlannerTest.kt](C:/Users/pkwccheng/Desktop/get_up_and_get_lit-main/app/src/test/java/com/getupandgetlit/dingshihai/SchedulePlannerTest.kt)

## 8. 通过 / 阻塞 / 未验证汇总

### 8.1 已通过

- 工程可编译
- 单元测试通过
- APK 可生成
- 设备可 root
- 安装限制已定位并修复
- APK 可安装
- App 可启动
- 首页空状态正确
- 关键权限已到位

### 8.2 已阻塞

- 基于 adb 的 UI 自动化点击未稳定进入创建页
- `uiautomator dump` 在进一步操作时出现 `null root node`

### 8.3 尚未完成验证

- 创建单次播放任务
- 创建间隔循环任务
- 编辑任务
- 删除任务
- 重复时间校验
- 文件选择器只允许 mp3/wav
- 批次启动/取消
- 前台服务与通知
- 定时触发
- 蓝牙不可用状态
- 播放中蓝牙断开停播
- 开机后重置
- 日志目录与落盘内容

## 9. 风险判断

当前项目处于“代码完成并可安装运行，但真机业务功能尚未完成端到端验收”的状态。

主要风险不在构建层，而在：

- Flyme 上 UI 自动化控制稳定性不足
- 文件选择器与系统页面切换无法仅靠当前 adb 驱动可靠验证
- 播放/蓝牙/锁屏调度需要更接近人工操作或更强 UI 自动化工具配合验证

## 10. 建议的下一步

建议按以下顺序继续测试：

1. 采用人工配合方式在设备上完成一次任务创建，我再继续通过 adb 观察数据库、服务、日志与状态变化。
2. 或切换到更强的 UI 自动化方案，例如 `uiautomator2/Appium`，而不是只靠 `adb input tap`。
3. 完成后续重点真机场景：
   - 创建/编辑/删除
   - 全部预定 / 取消预定
   - 锁屏 + `deviceidle force-idle`
   - 蓝牙断开场景
   - 开机重置

## 11. 相关文件

- [README.md](C:/Users/pkwccheng/Desktop/get_up_and_get_lit-main/README.md)
- [TEST_RESULTS.md](C:/Users/pkwccheng/Desktop/get_up_and_get_lit-main/TEST_RESULTS.md)
- [TEST_REPORT.md](C:/Users/pkwccheng/Desktop/get_up_and_get_lit-main/TEST_REPORT.md)
- [device_setup.ps1](C:/Users/pkwccheng/Desktop/get_up_and_get_lit-main/scripts/device_setup.ps1)
