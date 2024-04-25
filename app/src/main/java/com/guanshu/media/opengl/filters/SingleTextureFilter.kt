package com.guanshu.media.opengl.filters

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Log
import android.util.Size
import com.guanshu.media.opengl.FLOAT_SIZE_BYTES
import com.guanshu.media.opengl.checkGlError
import com.guanshu.media.opengl.getAtrribLocation
import com.guanshu.media.opengl.getUniformLocation
import com.guanshu.media.utils.DefaultSize
import java.nio.ByteBuffer
import java.nio.ByteOrder

// mat4: 4x4 矩阵
// vec4: 向量4
// aPosition 顶点坐标 -> uMVPMatrix 旋转拉伸
// aTextureCoord 纹理顶点 -> uSTMatrix 旋转拉伸
// varying vTextureCoord: 意味着vTextureCoord 会被光栅化插值
const val VERTEX_SHADER = """
                uniform mat4 uMVPMatrix;
                uniform mat4 uSTMatrix;
                attribute vec4 aPosition;
                attribute vec4 aTextureCoord;
                varying vec2 vTextureCoord;
                void main() {
                  gl_Position = uMVPMatrix * aPosition;
                  vTextureCoord = (uSTMatrix * aTextureCoord).xy;
                }
                """

// vTextureCoord 是插值过的片元坐标, texture2D是一个vec4:rgba
// gl_FragColor可以看成是对应 gl_Position的颜色计算
const val FRAGMENT_SHADER = """
                #extension GL_OES_EGL_image_external : require
                precision mediump float;
                varying vec2 vTextureCoord;
                uniform samplerExternalOES sTexture;
                void main() {
                    gl_FragColor = texture2D(sTexture, vTextureCoord);
                }
                """

// 顶点坐标和纹理坐标
private val verticesData = floatArrayOf(
    // X, Y, Z, U, V
    -1.0f, -1.0f, 0f, 0f, 0f,
    1.0f, -1.0f, 0f, 1f, 0f,
    -1.0f, 1.0f, 0f, 0f, 1f,
    1.0f, 1.0f, 0f, 1f, 1f,
)

private const val TAG = "SingleTextureFilter"

/**
 * 渲染texture
 */
class SingleTextureFilter : BaseFilter(
    VERTEX_SHADER,
    FRAGMENT_SHADER,
) {

    private val vertexVbos = IntArray(1)
    private val mvpMatrix = FloatArray(16)
    private var mvpMatrixHandle = 0
    private var stMatrixHandle = 0
    private var aPositionHandle = 0
    private var aTextureHandle = 0


    override fun init() {
        super.init()
        Log.i(TAG, "call init")
        aPositionHandle = program.getAtrribLocation("aPosition")
        mvpMatrixHandle = program.getUniformLocation("uMVPMatrix")
        aTextureHandle = program.getAtrribLocation("aTextureCoord")
        stMatrixHandle = program.getUniformLocation("uSTMatrix")

        val vertices = ByteBuffer.allocateDirect(verticesData.size * FLOAT_SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        vertices.put(verticesData).position(0)

        GLES20.glGenBuffers(1, vertexVbos, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexVbos[0])
        GLES20.glBufferData(
            GLES20.GL_ARRAY_BUFFER,
            verticesData.size * FLOAT_SIZE_BYTES,
            vertices,
            GLES20.GL_STATIC_DRAW
        )
        GLES20.glVertexAttribPointer(
            aPositionHandle,
            3,
            GLES20.GL_FLOAT,
            false,
            5 * FLOAT_SIZE_BYTES,
            0,
        )
        GLES20.glVertexAttribPointer(
            aTextureHandle,
            2,
            GLES20.GL_FLOAT,
            false,
            5 * FLOAT_SIZE_BYTES,
            3 * FLOAT_SIZE_BYTES
        )
        GLES20.glEnableVertexAttribArray(aPositionHandle)
        GLES20.glEnableVertexAttribArray(aTextureHandle)

//        数据传输完就解绑
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    override fun render(
        textureId: Int,
        textMatrix: FloatArray,
        mediaResolution: Size,
        screenResolution: Size,
    ) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)
        checkGlError("glUseProgram")
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexVbos[0])

        Matrix.setIdentityM(mvpMatrix, 0)
        adjustTransformMatrix(mvpMatrix, mediaResolution, screenResolution)

        // 缩放纹理，会导致纹理坐标 >1 的使用 clamp_to_edge mode，出现像素重复
        // 建议缩放顶点坐标
//        Matrix.scaleM(stMatrix, 0, 2f, 2f, 1f)

        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(stMatrixHandle, 1, false, textMatrix, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        checkGlError("glDrawArrays")

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    /**
     * TODO 这个可以优化成只做一次
     */
    private fun adjustTransformMatrix(
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

//        Matrix.scaleM(matrix, 0, scaleX, scaleY, 1f)
        Matrix.scaleM(matrix, 0, scaleX * 0.8f, scaleY * 0.8f, 1f)
    }
}