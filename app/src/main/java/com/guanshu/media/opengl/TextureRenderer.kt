package com.guanshu.media.opengl

import android.graphics.Bitmap
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Log
import android.util.Size
import com.guanshu.media.opengl.filters.FilterConstants
import com.guanshu.media.opengl.filters.RenderGraph
import com.guanshu.media.opengl.filters.SingleImageTextureFilter
import com.guanshu.media.opengl.filters.SingleTextureFilter
import com.guanshu.media.opengl.filters.TwoOesTextureFilter2
import com.guanshu.media.utils.Logger

private const val TAG = "TextureRender"

class TextureData(
    val textureId: Int,
    var matrix: FloatArray,
    var resolution: Size,
    var textureType: Int = GLES11Ext.GL_TEXTURE_EXTERNAL_OES
) {
    fun bind(slot: Int = 0) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + slot)
        GLES20.glBindTexture(textureType, textureId)
    }

    fun unbind() = GLES20.glBindTexture(textureType, 0)

    override fun toString(): String {
        return "{id=$textureId, resolution=$resolution, type=$textureType}"
    }
}

class TextureRender(renderGraph: RenderGraph) : Renderer(renderGraph) {

    private val fbo by lazy { newFbo() }
    private val fboTextures = ArrayList<TextureData>()
    private var testBitmap: Bitmap? = null

    private fun acquireFboTexture(resolution: Size): TextureData {
        val iterator = fboTextures.iterator()
        while (iterator.hasNext()) {
            val texture = iterator.next()
            if (texture.resolution == resolution) {
                iterator.remove()
//                Logger.v(TAG,"get cache $texture, $resolution")
                return texture
            }
        }

        val texture = newTexture(
            GLES20.GL_TEXTURE_2D,
            resolution.width,
            resolution.height
        )
//        Logger.v(TAG,"new $texture, $resolution")
        val floatArray = FloatArray(16)
        Matrix.setIdentityM(floatArray, 0)
        return TextureData(
            texture,
            floatArray,
            resolution,
            GLES20.GL_TEXTURE_2D,
        )
    }

    private fun returnFboTexture(textureData: TextureData) {
        if (textureData.textureType == GLES20.GL_TEXTURE_2D) {
//            Logger.v(TAG,"return to cache $textureData")
            fboTextures.add(textureData)
        }
    }

    override fun drawFrame(
        textureDatas: List<TextureData>,
        viewResolution: Size,
    ) {
        checkGlError("onDrawFrame start")

//        Logger.v(TAG,"draw")

        val nextTextureData = arrayListOf<TextureData>()
        if (renderGraph.filtersMap.values.isNotEmpty()) {
            textureDatas.forEachIndexed { index, textureData ->
                val filters = renderGraph.filtersMap[index]
                GLES20.glViewport(0, 0, textureData.resolution.width, textureData.resolution.height)
                checkGlError("onDrawFrame: glViewport:${textureData.resolution}")


                var input = textureData
                var output = acquireFboTexture(textureData.resolution)
                bindFbo(fbo, output.textureId)
                checkGlError("onDrawFrame: bindFbo")

                // assume there's N filters
                // the rendering process will be
                // input -> fbo+texture1
                // texture 1 -> fbo+texture2
                // texture 2 -> fbo+texture1
                // ..
                // texture 1 or 2 -> surface
                filters?.forEachIndexed { filterIndex, filter ->

//                    Logger.v(TAG,"draw from $input, to $output")
                    filter.render(listOf(input), input.resolution)

                    checkGlError("onDrawFrame: render $filterIndex, $filter")

                    returnFboTexture(input)
                    if (filterIndex != filters.lastIndex) {
                        input = output
                        output = acquireFboTexture(input.resolution)

                        unbindFbo()
                        bindFbo(fbo, output.textureId)
                    }
                }

                unbindFbo()
                Matrix.setIdentityM(output.matrix, 0)
                nextTextureData.add(output)

            }

        } else {
            nextTextureData.addAll(textureDatas)
        }

//        Logger.v(TAG,"draw from $nextTextureData to surface")

        GLES20.glViewport(0, 0, viewResolution.width, viewResolution.height)
        if (renderGraph.outputFilter != null) {
            renderGraph.outputFilter!!.render(nextTextureData, viewResolution)
        } else if (nextTextureData.size == 1) {
            if (nextTextureData[0].textureType == GLES20.GL_TEXTURE_2D) {
                renderGraph.outputFilter = SingleImageTextureFilter()
            } else {
                renderGraph.outputFilter = SingleTextureFilter()
            }
            renderGraph.outputFilter!!.init()
            renderGraph.outputFilter!!.render(nextTextureData, viewResolution)
            Logger.d(TAG, "drawFrame: lazy init ${renderGraph.outputFilter}")
        } else {
            // TODO it should be image texture
            // TODO support multiple input
            renderGraph.outputFilter = TwoOesTextureFilter2()
            renderGraph.outputFilter!!.init()
            renderGraph.outputFilter!!.render(nextTextureData, viewResolution)
            Logger.d(TAG, "drawFrame: lazy init ${renderGraph.outputFilter}")
        }
        nextTextureData.forEach { returnFboTexture(it) }

        GLES20.glFlush()
        checkGlError("onDrawFrame end")
    }
}