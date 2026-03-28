# 定时嗨 AI Agent 实施包 v1

版本：v1.0（按当前代码实现覆盖更新）  
适用范围：`Meizu S6 / Android 7 / Flyme` 单机侧载运行  
定位：本文档不是 PRD，而是与当前工程实现保持一致的实施说明与保活说明。

---

## 0. 当前实现结论

当前工程已经落地为原生 Android App，核心技术栈如下：

- 语言：Kotlin
- UI：XML + ViewBinding + RecyclerView + SwipeRefreshLayout
- 本地存储：Room
- 播放器：Media3 ExoPlayer
- 调度：`AlarmManager.setExactAndAllowWhileIdle + RTC_WAKEUP`
- 后台执行：前台服务 + 广播拉起
- 目标设备：固定为魅族魅蓝 S6，Android 7，系统 root 已可用

当前实现**不是**“服务常驻 + 协程长时间 `delay()` 等待下一任务”，而是：

- 点击“全部预定”时计算本批次全部目标触发时刻并写入数据库
- 只为“下一条待触发任务”注册一个 `exact alarm`
- 两个任务之间，服务会主动退出，不再全程常驻
- 到点后由 `RTC_WAKEUP` alarm 拉起 `GuardAlarmReceiver -> SchedulerForegroundService`
- 服务只在“当前任务处理/播放期间”短时或阶段性持有 wakelock

这版实现的目标是：

- 提高锁屏 / Doze / 厂商 ROM 下的到点触发概率
- 降低“整个预定期间都明显耗电”的问题
- 通过 root 做温和增强，而不是改系统 idle 常量或做整机常驻守护

---

## 1. 产品边界

### 1.1 做什么

定时嗨用于配置若干本地音频播放任务，并在用户点击“全部预定”后，按一轮批次依次执行。

核心能力：

- 创建 / 编辑 / 删除任务
- 手动发起一轮批次执行
- 手动取消当前批次
- 在锁屏场景下尽量稳定到点拉起并播放
- 蓝牙不可用时终止该任务并记录日志
- 新任务到点时可抢占当前正在播放的旧任务

### 1.2 不做什么

- 不做每日重复、每周重复、日历型提醒
- 不做云同步、多设备同步、服务端调度
- 不做在线音频
- 不做开机自动恢复上一轮批次
- 不做错过任务补偿

---

## 2. 当前数据模型

### 2.1 tasks 表

当前 `tasks` 表字段如下：

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Long | 主键，自增 |
| name | String? | 任务名称，可空 |
| startHour | Int | 开始小时，0-23 |
| startMinute | Int | 开始分钟，0-59 |
| fileUri | String | 本地音频 Uri |
| fileName | String | 展示用文件名 |
| playMode | String | `single` / `interval` |
| loopCount | Int? | 间隔循环模式下的循环次数 |
| intervalMinSec | Int? | 间隔最小秒数 |
| intervalMaxSec | Int? | 间隔最大秒数 |
| maxPlaybackMinutes | Int | 单轮音频最多播放分钟数，`0` 表示不限制 |
| status | String | `未触发` / `已触发` / `蓝牙未连接` |
| scheduledAtEpochMs | Long? | 当前批次内的目标触发时间 |
| createdAt | Long | 创建时间 |
| updatedAt | Long | 更新时间 |

### 2.2 runtime_state 表

当前存在单行表 `runtime_state`，作为全局批次状态来源：

| 字段 | 类型 | 说明 |
|---|---|---|
| id | Int | 固定为 1 |
| batchActive | Boolean | 当前是否存在活动批次 |
| batchId | String? | 当前批次标识 |
| currentPlayingTaskId | Long? | 当前正在播放的任务 id |
| lastServiceHeartbeatAt | Long | 服务心跳时间 |
| updatedAt | Long | 更新时间 |

### 2.3 数据库版本

当前 Room 数据库版本为 `2`。

已存在 migration：

- `1 -> 2`：给 `tasks` 表增加 `maxPlaybackMinutes INTEGER NOT NULL DEFAULT 0`

---

## 3. 任务参数与校验

### 3.1 基础参数

- 任务名称：非必填，最长 10 个字符
- 任务开始时间：必填，`HH:MM`
- 播放文件：必填，仅支持 `mp3 / wav`
- 播放模式：`单次播放` / `间隔循环播放`

### 3.2 间隔循环参数

当播放模式为 `间隔循环播放` 时显示并要求填写：

- 循环次数：`1-99`
- 每次循环间隔最小值：`1-9999` 秒
- 每次循环间隔最大值：`1-9999` 秒，且必须 `>= 最小值`

### 3.3 音频最多播放分钟数

当前已实现任务参数：

- 字段名：`maxPlaybackMinutes`
- UI 位置：创建页 / 编辑页中，“任务开始时间”下面
- 类型：非负整数
- 规则：
  - `0`：不限制，音频自然播放结束
  - 正整数：单轮播放最长只允许该分钟数

适用范围：

- 对“单次播放”生效
- 对“间隔循环播放”的每一轮都生效

具体行为：

- 若音频自身时长短于上限，则自然播完
- 若音频自身时长长于上限，则到时主动停止当前轮播放
- 间隔循环模式下，某一轮若因上限被截断，会立即进入该轮结束后的间隔等待

### 3.4 其他校验

- `HH:MM` 在任务之间必须唯一
- 编辑时允许保留自己原本的 `HH:MM`
- `single` 模式下，`loopCount / intervalMinSec / intervalMaxSec` 为空
- `interval` 模式下，上述三个字段必须完整合法

---

## 4. 批次模型

### 4.1 基本规则

产品仍然是“一次点击，执行一轮”的批次模型：

1. 用户点击“全部预定”后，生成一轮批次
2. 批次内每个任务最多执行一次
3. 批次内所有任务都不再是“未触发”后，本轮自动结束
4. 若要再来一轮，必须再次点击“全部预定”
5. 点击“取消预定”后，本轮立即停止
6. 手机重启后，不恢复上一轮批次

### 4.2 任务目标触发时刻

输入时间是 `HH:MM`，实际目标触发时点按 `HH:MM:00` 处理：

- 若启动批次时该时间点当天尚未到，则目标时刻为当天 `HH:MM:00`
- 若该时间点当天已过，则目标时刻为次日 `HH:MM:00`

这里的“次日”仅用于完成本轮批次中的一次执行，不代表任务变成每天重复。

---

## 5. 当前调度实现

### 5.1 调度主结构

当前调度链路是：

`HomeActivity -> SchedulerForegroundService.reserveAll -> TaskRepository.startBatch -> AlarmCoordinator.scheduleGuardAlarm -> GuardAlarmReceiver -> SchedulerForegroundService.guardAlarm`

关键点：

- 调度采用 `AlarmManager.setExactAndAllowWhileIdle(...)`
- alarm 类型使用 `AlarmManager.RTC_WAKEUP`
- 当前只注册“下一条待触发任务”的 alarm
- 服务不再依赖长时间 `delay()` 等待下一任务

### 5.2 reserve all 时的顺序

点击“全部预定”后，按以下顺序执行：

1. 停止当前播放中的任务
2. 取消旧 guard alarm
3. 通过 root 尝试把 app 加入 `deviceidle whitelist`
4. 计算本批次全部任务的 `scheduledAtEpochMs`
5. 写入新 `batchId`
6. 把所有任务状态统一重置为 `未触发`
7. 记录 `scheduler_started`
8. 只注册下一条待触发任务的 exact alarm
9. 若当前没有正在播放的任务，则服务主动退出，等待下次 alarm 唤醒

### 5.3 到点触发时的顺序

guard alarm 到点后：

1. `GuardAlarmReceiver` 收到广播
2. 拉起 `SchedulerForegroundService`
3. 服务立刻进入前台
4. 服务先获取一个短时 CPU wakelock
5. 通过 root 保护当前进程 `oom_score_adj = -1000`
6. 校验 `batchId` 是否仍匹配当前活动批次
7. 若任务仍是 `未触发`，则进入触发流程
8. 任务触发成功后，立刻再为“下一条待触发任务”注册 exact alarm

### 5.4 同时到点冲突处理

虽然前端已禁止相同 `HH:MM`，实现层仍保留保护逻辑：

- 若存在多个目标时间完全相同的未触发任务
- 按 `updatedAt` 降序只取一条作为主任务执行
- 其余记录日志并跳过

---

## 6. 当前保活与唤醒策略

### 6.1 exact alarm

当前保活主链路首先依赖：

- `setExactAndAllowWhileIdle`
- `RTC_WAKEUP`

含义：

- 到点后由系统唤醒设备并派发广播
- 不依赖 app 自己全程常驻或全程持有 CPU 锁

### 6.2 前台服务策略

当前实现不是“整个预定期间服务常驻”。

而是：

- 服务在以下场景启动：
  - 点击“全部预定”
  - exact alarm 到点
  - 需要 reconcile 批次状态
- 服务在以下场景主动退出：
  - 当前没有正在播放的任务时
  - 即使批次仍 active，只要下一条 alarm 已经注册完成，也会退出

因此：

- 两个任务之间，前台通知通常不会一直保留
- 两个任务之间，服务也不会一直常驻

### 6.3 CPU wakelock 策略

当前已从“批次全程持有 `PARTIAL_WAKE_LOCK`”改为阶段性持有：

1. `handoff wakelock`
   - 场景：alarm 拉起服务后
   - 类型：`PARTIAL_WAKE_LOCK`
   - 时长：`2 分钟`
   - 用途：保证任务触发、蓝牙检查、播放器启动这段代码能稳定执行

2. `active task wakelock`
   - 场景：当前任务开始进入播放流程后
   - 类型：`PARTIAL_WAKE_LOCK`
   - 时长：`10 小时`
   - 注意：只在“当前确有活动任务”期间持有

3. 释放时机
   - 当前播放结束 / 失败 / 停止 / 蓝牙断开后
   - 若没有正在播放的任务，服务会释放 wakelock 并退出

结果：

- 两个任务之间不再持续保持 CPU 完全唤醒
- 锁屏待机期间的基础耗电显著低于早期全程持锁方案

### 6.4 root 温和增强

当前 root 相关增强包括：

- `su -c id`：检测 root
- `dumpsys deviceidle whitelist +包名`：加入 Doze 白名单
- `echo -1000 > /proc/<pid>/oom_score_adj`：降低被系统回收概率
- `input keyevent 224`：唤醒设备
- `wm dismiss-keyguard`：尝试去掉锁屏
- `settings put system screen_brightness_mode 0`
- `settings put system screen_brightness 1`

原则：

- 只对本 app 做白名单、进程保护、亮屏和亮度处理
- 不改全局 Doze 参数
- 不做整机常驻守护

---

## 7. 当前播放链路

### 7.1 播放器选择

当前使用 Media3 ExoPlayer 单实例。

已启用的关键能力：

- `USAGE_MEDIA`
- `STREAM_MUSIC`
- `setHandleAudioBecomingNoisy(true)`
- `setWakeMode(C.WAKE_MODE_LOCAL)`

说明：

- 真实音频输出走媒体流，优先保证蓝牙 A2DP 路由
- 不再用 `USAGE_ALARM` 作为最终音频输出流

### 7.2 播放前步骤

任务进入播放前，按以下顺序：

1. 停止旧播放
2. 做蓝牙可用性检查
3. 做蓝牙路由准备
4. 通过 root 执行 `input keyevent 224 + wm dismiss-keyguard`
5. 申请音频焦点
6. `prepare() + playWhenReady = true + play()`

### 7.3 蓝牙检查与路由

当前蓝牙播放约束如下：

- 任务触发前必须先通过蓝牙可用性检查
- 若蓝牙音频不可用：
  - 任务状态置为 `蓝牙未连接`
  - 记录 `bluetooth_unavailable`
  - 当前任务结束，不重试
- 播放中若蓝牙断开：
  - 停止当前播放
  - 记录 `playback_failed`，结果为 `bluetooth_lost`
  - 不自动回退到手机扬声器继续播

### 7.4 亮屏与亮度

当前亮屏行为是：

- 播放真正开始后，服务持有一个 `15 秒` 的 `SCREEN_DIM_WAKE_LOCK`
- 同时通过 root 把系统亮度压到最低 `1/255`
- 播放结束或服务退出后，尝试恢复原亮度和亮度模式

这意味着：

- 不会全程常亮
- 只在起播窗口短时 dim 亮屏
- 亮屏期间亮度会被压到最低，降低刺眼程度

---

## 8. 单次播放与间隔循环

### 8.1 单次播放

- 播放一次选中的音频
- 不拉起 Activity
- 不切换到前台页面

### 8.2 间隔循环播放

规则如下：

1. 第一轮立即播放
2. 总轮数等于用户填写的 `loopCount`
3. 每轮结束后，从 `[intervalMinSec, intervalMaxSec]` 中随机抽取一个整数秒
4. 等待该秒数后开始下一轮
5. 达到总轮数后结束任务

### 8.3 与 `maxPlaybackMinutes` 的关系

无论单次还是间隔循环，`maxPlaybackMinutes` 都作用于“单轮播放”：

- `0`：该轮自然结束
- 正整数：到达上限即主动停止该轮

间隔模式下：

- 每轮都重新应用同一个上限
- 某轮被截断后，直接进入间隔等待

---

## 9. UI 当前实现

### 9.1 首页

当前首页包含：

- 左上：`创建任务`
- 右上：
  - 未预定时：`全部预定`
  - 批次执行中：`取消预定`
- 中部：任务列表
- 支持下拉刷新

### 9.2 列表项

列表项固定显示三行：

1. `任务名称 + HH:MM`
2. `执行状态：xxx`
3. `播放安排：单次播放 / 间隔循环播放`

### 9.3 侧滑交互

当前侧滑不是“滑一下直接执行”，而是“滑出按钮再点击”：

- 右往左滑：露出红色 `删除`
- 左往右滑：露出绿色 `编辑`
- 点击 `删除` 后才弹二次确认
- 点击 `编辑` 后才进入编辑页
- 批次执行中，侧滑被禁用

### 9.4 创建页 / 编辑页

创建页和编辑页共用同一套表单，编辑页会回显当前任务内容。

新增参数 `音频最多播放分钟数（0=不限制）` 已放在：

- “任务开始时间”下面

当前回显规则：

- 字段值为 `0` 时也会直接回显 `0`
- 不再把 `0` 隐藏为空白

---

## 10. 日志与观测

### 10.1 落盘目录

日志固定目录：

`/sdcard/Download/定时嗨/`

### 10.2 文件命名

当前文件名格式：

`定时嗨_yyyyMMdd_HHmmss.log`

### 10.3 当前主要日志事件

至少包括以下事件：

- `task_created`
- `task_updated`
- `task_deleted`
- `reserve_all_clicked`
- `cancel_reserve_clicked`
- `scheduler_started`
- `scheduler_stopped`
- `next_alarm_scheduled`
- `task_triggered`
- `bluetooth_unavailable`
- `bluetooth_route_prepared`
- `root_process_protected`
- `root_playback_window_prepared`
- `root_wakeup_sent`
- `root_brightness_dimmed`
- `root_brightness_restored`
- `playback_started`
- `playback_round_finished`
- `playback_finished`
- `playback_failed`
- `playback_start_timeout`
- `boot_reset_all_tasks`

### 10.4 播放完成原因

当前日志已区分：

- 自然结束：`reason=natural`
- 因上限截断结束：`reason=truncated_by_limit`

单轮日志：

- `playback_round_finished`

任务整体结束日志：

- `playback_finished`

---

## 11. 开机与异常处理

### 11.1 开机

手机开机后：

1. 不恢复上一轮批次
2. 不补触发错过任务
3. 不自动重新预定
4. 停止残留调度
5. 所有任务状态统一重置为 `未触发`

### 11.2 文件失效

若 Uri 失效、文件被删、权限丢失：

- 当前任务立刻终止
- 记录日志
- 不自动重试

### 11.3 播放异常

若播放器启动失败、播放中异常、蓝牙断开：

- 当前任务终止
- 记录日志
- 不新增长期前台任务状态
- 不影响后续任务继续按调度执行

---

## 12. 当前实施取舍

### 12.1 为什么不用“全程常驻 + 全程 CPU 锁”

早期方案里，预定期间会持续持有 `PARTIAL_WAKE_LOCK`，导致：

- 锁屏待机期间耗电明显增加
- 手机系统耗电统计里，app 会长期占主要耗电来源

当前实现已改为：

- exact alarm 做主触发
- 任务间隙停止服务
- 任务间隙释放 CPU 锁
- 只在真正处理任务时短时持锁

### 12.2 为什么仍保留 root

因为目标设备是 Flyme / Android 7，单靠标准能力在长时间锁屏下不够稳。

root 的意义是：

- 提高 Doze 下拉起概率
- 降低被 ROM 回收的概率
- 到点时主动唤醒设备
- 让亮屏窗口更可控、更暗

但仍坚持：

- 不改系统全局策略
- 不做暴力保活

---

## 13. 当前关键类

建议按以下理解工程结构：

- `TaskRepository`
  - 任务 CRUD
  - runtime_state 读写
  - 批次启动 / 停止

- `BatchScheduler`
  - 计算本轮 `scheduledAtEpochMs`
  - 解决同时到点冲突

- `AlarmCoordinator`
  - 注册 / 取消下一条 exact alarm
  - immediate revive alarm

- `SchedulerForegroundService`
  - 批次 owner
  - 任务触发 owner
  - 保活、亮屏、wakelock、前台通知 owner

- `GuardAlarmReceiver`
  - alarm 广播入口

- `PlaybackController`
  - 单次 / 间隔循环播放
  - 音频上限截断
  - 播放事件分发

- `BluetoothChecker`
  - 蓝牙可用性检查
  - 路由准备

- `RootOps`
  - root 检测
  - Doze 白名单
  - 进程保护
  - 唤醒设备
  - 亮度压低 / 恢复

---

## 14. 验收重点

当前版本最值得重点验证的场景：

1. 点击“全部预定”后，两个任务之间服务是否退出、耗电是否下降
2. 锁屏长待机后，exact alarm 是否仍能按时拉起任务
3. root 唤醒后，蓝牙播放是否稳定走蓝牙设备
4. `maxPlaybackMinutes`
   - `0` 时是否自然播完
   - 正整数时是否按上限截断
   - 间隔模式下每轮是否都生效
5. 日志中是否正确区分：
   - `natural`
   - `truncated_by_limit`
6. 开机后是否只做重置，不做恢复

---

## 15. 结论

当前实现已经从最初的“前台服务常驻 + 长时间等待”转成了“exact alarm 驱动 + 服务按需启动 + root 温和增强”的方案。

保活核心现状可以简化为：

- 任务间隙：不常驻、不持 CPU 锁、只保留下一条 alarm
- 任务到点：alarm 唤醒系统并拉起服务
- 起播前：root 唤醒设备、准备蓝牙路由、短时 CPU 锁护航
- 播放时：任务级 CPU 锁 + ExoPlayer 本地 wake mode
- 起播后：短时 dim 亮屏，最低亮度

这就是当前代码实际采用的实施方式与保活细节。
