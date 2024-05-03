package com.guanshu.media.opengl

import android.opengl.Matrix
import android.util.Size
import com.guanshu.media.utils.DefaultSize

fun updateTransformMatrix(
    matrix: FloatArray,
    mediaResolution: Size,
    screenResolution: Size,
) {
    if (mediaResolution == DefaultSize || screenResolution == DefaultSize) {
        return
    }
    val mediaAspectRatio = mediaResolution.width.toFloat() / mediaResolution.height
    val viewAspectRatio = screenResolution.width.toFloat() / screenResolution.height

    var scaleX = 1f
    var scaleY = 1f
    if (mediaAspectRatio > viewAspectRatio) {
        // 视频比view更宽,x填满整个屏幕,y需要缩放，
        val expectedHeight =
            screenResolution.width.toFloat() / mediaResolution.width * mediaResolution.height
        // 视频高度被默认拉伸填充了view，需要缩放
        scaleY = expectedHeight / screenResolution.height
    } else {
        val expectedWidth =
            screenResolution.height.toFloat() / mediaResolution.height * mediaResolution.width
        scaleX = expectedWidth / screenResolution.width
    }

    Matrix.scaleM(matrix, 0, scaleX, scaleY, 1f)
}