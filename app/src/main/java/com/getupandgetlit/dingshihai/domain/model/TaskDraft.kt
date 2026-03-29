package com.getupandgetlit.dingshihai.domain.model

data class TaskDraft(
    val id: Long? = null,
    val name: String = "",
    val startHour: Int? = null,
    val startMinute: Int? = null,
    val fileUri: String = "",
    val fileName: String = "",
    val playMode: PlayMode = PlayMode.SINGLE,
    val loopCount: Int? = null,
    val intervalMinSec: Int? = null,
    val intervalMaxSec: Int? = null,
    val maxPlaybackMinutes: Int? = 0,
    val forceBluetoothPlayback: Boolean = true,
)
