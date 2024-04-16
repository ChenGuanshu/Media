package com.guanshu.media.view

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceView

private const val TAG = "Camera2SurfaceView"

class Camera2SurfaceView : SurfaceView {

    private var updatedWidth = -1
    private var updatedHeight = -1

    constructor(context: Context) : super(context, null)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr,
        0,
    )

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(
        context,
        attrs,
        defStyleAttr,
        defStyleRes,
    )

    fun updateSize(width: Int, height: Int) {
        Log.d(TAG, "updateSize: $width*$height")
        post {
            updatedWidth = width
            updatedHeight = height
            requestLayout()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = MeasureSpec.getSize(widthMeasureSpec);
        val height = MeasureSpec.getSize(heightMeasureSpec);
        if (0 == updatedWidth || 0 == updatedHeight) {
            setMeasuredDimension(width, height);
        } else {
            if (width < height * updatedWidth / updatedHeight) {
                setMeasuredDimension(width, width * updatedHeight / updatedWidth);
            } else {
                setMeasuredDimension(height * updatedWidth / updatedHeight, height);
            }
        }
    }
}