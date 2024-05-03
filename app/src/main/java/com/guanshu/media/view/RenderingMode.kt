package com.guanshu.media.view

sealed class RenderingMode {
    data object RenderWhenDirty : RenderingMode()
    data class RenderFixedRate(val fps: Int = 30) : RenderingMode() {
        val delayMs = 1000L / fps
    }
}