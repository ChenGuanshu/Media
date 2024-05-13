package com.guanshu.media.opengl.abstraction

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Size
import com.guanshu.media.opengl.checkGlError
import com.guanshu.media.opengl.newTexture

// TODO
abstract class Texture(
    val textureId: Int,
    val textureType: Int,
    val resolution: Size,
    // The matrix to transform the texture in a correct direction
    val matrix: FloatArray = FloatArray(16),
) {
    fun bind(slot: Int = 0) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + slot)
        GLES20.glBindTexture(textureType, textureId)
    }

    fun unbind() = GLES20.glBindTexture(textureType, 0)
}

class ExternalTexture(
    textureId: Int,
    resolution: Size,
) : Texture(textureId, GLES11Ext.GL_TEXTURE_EXTERNAL_OES, resolution)

// TODO flip the texture
class Sampler2DTexture(
    textureId: Int,
    resolution: Size,
) : Texture(textureId, GLES20.GL_TEXTURE_2D, resolution) {

    companion object {
        fun fromBitmap(bitmap: Bitmap): Sampler2DTexture {
            val textures = IntArray(1)
            newTexture(textures, GLES20.GL_TEXTURE_2D)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            checkGlError("texImage2D")

            val texture = Sampler2DTexture(
                textures[0],
                Size(bitmap.width, bitmap.height)
            )
            bitmap.recycle()
            return texture
        }

        fun fromFilePath(filePath: String): Sampler2DTexture {
            val stream = this::class.java.getResourceAsStream("/res/drawable/pikachu.png")
            val bitmap = BitmapFactory.decodeStream(stream)
            return fromBitmap(bitmap)
        }
    }
}