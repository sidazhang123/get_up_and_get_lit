# 定时嗨

面向 `Meizu S6 / Android 7 / Flyme 8.0.5.0A` 的单机定时音频调度 App。项目按“单批次、多任务、一次性预定执行”实现，不支持周期性重复任务、云同步或开机恢复。

## 技术栈

- Kotlin + XML/ViewBinding
- Room + Flow/StateFlow
- Foreground Service + 协程调度
- AlarmManager guard alarm 兜底
- Media3 ExoPlayer
- Root 温和增强：自动尝试加入 `deviceidle` 白名单

## 本地构建

1. 安装 `JDK 17`
2. 保证 Android SDK 含 `platform 34` 和 `build-tools 34.0.0`
3. 运行：

```powershell
$env:GRADLE_USER_HOME="$PWD/.gradle-user-home"
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.18.8-hotspot'
$env:ANDROID_SDK_ROOT='D:\AndroidSDK'
$env:ANDROID_HOME='D:\AndroidSDK'
$env:HTTP_PROXY=''
$env:HTTPS_PROXY=''
$env:ALL_PROXY=''
$env:NO_PROXY=''
.\gradlew.bat testDebugUnitTest assembleDebug
```

生成的调试包路径：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 运行方式

1. 安装 APK 到目标手机
2. 首次启动授予：
   - 外部存储权限
   - 文档选择器读权限
   - Flyme root 权限确认（若弹窗出现）
3. 点击“全部预定”时 App 会自动尝试加入 `deviceidle` 白名单
4. 若 root 白名单失败，App 仍可运行，但长时间锁屏后的准点性只按标准 Android 能力提供

## 关键实现

- `tasks` 表保存任务定义与当前批次的 `scheduledAtEpochMs`
- `runtime_state` 单行表保存批次状态，首页 UI 完全由数据库驱动
- `SchedulerForegroundService` 是唯一调度 owner
- `GuardAlarmReceiver` 只负责兜底唤醒服务，不直接执行业务
- `PlaybackController` 单实例播放，支持抢占
- `BluetoothChecker` 在触发前校验蓝牙输出；播放中掉蓝牙则立即停播并记 log
- `BootCompletedReceiver` 只重置状态，不恢复批次

## 日志

日志目录固定为：

```text
Download/定时嗨/
```

建议关注的事件：

- `task_created`
- `task_updated`
- `task_deleted`
- `reserve_all_clicked`
- `cancel_reserve_clicked`
- `scheduler_started`
- `scheduler_stopped`
- `task_triggered`
- `bluetooth_unavailable`
- `playback_started`
- `playback_finished`
- `playback_failed`
- `boot_reset_all_tasks`

