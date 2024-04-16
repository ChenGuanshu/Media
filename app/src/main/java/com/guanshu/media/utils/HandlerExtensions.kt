package com.guanshu.media.utils

import android.os.Handler
import android.os.Looper

fun Handler.postOrRun(runnable: () -> Unit) {
    if (Looper.myLooper() == this.looper) {
        runnable()
    } else {
        post { runnable() }
    }
}