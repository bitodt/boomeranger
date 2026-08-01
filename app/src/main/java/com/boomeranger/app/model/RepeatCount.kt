package com.boomeranger.app.model

enum class RepeatCount(val value: Int, val label: String) {
    TWO(2, "2"),
    THREE(3, "3"),
    FOUR(4, "4");

    companion object {
        fun fromValue(value: Int): RepeatCount =
            entries.firstOrNull { it.value == value } ?: THREE
    }
}
