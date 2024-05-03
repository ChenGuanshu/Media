package com.guanshu.media.opengl

import android.util.Size
import com.guanshu.media.opengl.filters.BaseFilter
import com.guanshu.media.opengl.filters.FlattenFilter
import com.guanshu.media.opengl.filters.FlattenWithImageFilter
import com.guanshu.media.opengl.filters.SingleTextureFilter
import com.guanshu.media.opengl.filters.TextureWithImageFilter
import com.guanshu.media.opengl.filters.TwoOesTextureFilter
import com.guanshu.media.utils.Logger

private const val TAG = "TextureRender"

val filterIdMap = hashMapOf(
    1 to SingleTextureFilter::class.java,
    2 to TextureWithImageFilter::class.java,
    3 to FlattenFilter::class.java,
    4 to FlattenWithImageFilter::class.java,
    5 to TwoOesTextureFilter::class.java,
)

class TextureData(
    val textureId: Int,
    val matrix: FloatArray,
    var resolution: Size,
)

class TextureRender(
    private val filterId: Int = 1
) {
    private lateinit var filter: BaseFilter

    private var init = false


    /**
     * Initializes GL state.  Call this after the EGL surface has been created and made current.
     */
    fun init() {
        if (init) return
        init = true
        Logger.i(TAG, "init $this")

        filter = (filterIdMap[filterId] ?: filterIdMap[1])!!.newInstance()
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