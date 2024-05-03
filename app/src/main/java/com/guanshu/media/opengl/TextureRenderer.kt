package com.guanshu.media.opengl

import android.util.Log
import android.util.Size
import com.guanshu.media.opengl.filters.FlattenFilter
import com.guanshu.media.opengl.filters.FlattenWithImageFilter
import com.guanshu.media.opengl.filters.SingleTextureFilter
import com.guanshu.media.opengl.filters.TextureWithImageFilter
import com.guanshu.media.opengl.filters.TwoOesTextureFilter

private const val TAG = "TextureRender"

val filterIdMap = hashMapOf(
    1 to SingleTextureFilter(),
    2 to TextureWithImageFilter(),
    3 to FlattenFilter(),
    4 to FlattenWithImageFilter(),
    5 to TwoOesTextureFilter(),
)

class TextureData(
    val textureId: Int,
    val matrix: FloatArray,
    var resolution: Size,
)

class TextureRender {

    var filterId = 1
    private val filter get() = checkNotNull(filterIdMap[filterId] ?: filterIdMap[1])

    private var init = false


    /**
     * Initializes GL state.  Call this after the EGL surface has been created and made current.
     */
    fun init() {
        if (init) return
        init = true
        Log.i(TAG, "init $this")

        filter.init()
    }

    fun drawFrame(
        textureData: List<TextureData>,
        viewResolution: Size,
    ) {
        checkGlError("onDrawFrame start")
        filter.render(textureData, viewResolution)
    }
}