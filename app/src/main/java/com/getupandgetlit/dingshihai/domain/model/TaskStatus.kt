package com.getupandgetlit.dingshihai.domain.model

enum class TaskStatus(val value: String) {
    UNTRIGGERED("未触发"),
    TRIGGERED("已触发"),
    BLUETOOTH_UNAVAILABLE("蓝牙未连接");

    companion object {
        fun fromValue(value: String): TaskStatus {
            return entries.firstOrNull { it.value == value } ?: UNTRIGGERED
        }
    }
}

