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

fun <T> ArrayList<T>.addAll(vararg t: T) {
    t.forEach { this.add(it) }
}

/**
 * each 4 float represents
 *     val centerX: Float,
 *     val centerY: Float,
 *     val width: Float,
 *     val height: Float,
 */
fun buildVertexArray(vararg f: Float): FloatArray {
    if (f.size % 4 != 0) throw IllegalArgumentException("f.size is ${f.size}")

    val trunk = f.toList().chunked(4)
    val list = arrayListOf<Float>()
    trunk.forEach { vertex ->
        val centerX = vertex[0]
        val centerY = vertex[1]
        val halfW = vertex[2] / 2
        val halfH = vertex[3] / 2
        list.addAll(
            // X, Y, Z, U, V
            centerX - halfW, centerY - halfH, 0f, 0f, 0f,
            centerX + halfW, centerY - halfH, 0f, 1f, 0f,
            centerX - halfW, centerY + halfH, 0f, 0f, 1f,
            centerX + halfW, centerY + halfH, 0f, 1f, 1f,
        )
    }
    return list.toFloatArray()
}

/**
 * assume each 5 float represent a vertex
 * each 4 vertexes represents a square
 */
fun buildIndexArray(vertexArray: FloatArray): IntArray {
    if (vertexArray.size % 20 != 0) throw IllegalArgumentException("vertexArray.size is ${vertexArray.size}")

    var start = 0
    val num = vertexArray.size / 20
    val arrayList = ArrayList<Int>(num * 6)
    for (i in 0..<num) {
        arrayList.addAll(start, start + 1, start + 2, start + 1, start + 2, start + 3)
        start += 4
    }
    return arrayList.toIntArray()
}