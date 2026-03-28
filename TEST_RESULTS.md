# 基础自测结果

执行日期：2026-03-27

## 已完成

- `testDebugUnitTest`
  - `TaskDraftValidatorTest`
  - `SchedulePlannerTest`
- `assembleDebug`
- 设备连接确认：`Meizu S6 / Android 7.0 / Flyme 8.0.5.0A`
- `adb shell su -c id` 确认 shell 可直接获得 root

## 计划内但需真机继续回归

- 创建/编辑/删除任务完整交互
- 文档选择器持久化读权限
- “全部预定 / 取消预定”整链路
- 蓝牙不可用前置拦截
- 播放中蓝牙断开立即停播
- 长时间锁屏 + `deviceidle force-idle` 准点触发
- 开机后状态重置

## 当前交付物

- Android 工程源码
- 调试 APK 构建产物
- `scripts/device_setup.ps1`
- `README.md`
- 本文件
