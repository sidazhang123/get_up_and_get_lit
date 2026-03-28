package com.getupandgetlit.dingshihai.domain.model

enum class PlayMode(val value: String) {
    SINGLE("single"),
    INTERVAL("interval");

    companion object {
        fun fromValue(value: String): PlayMode {
            return entries.firstOrNull { it.value == value } ?: SINGLE
        }
    }
}

