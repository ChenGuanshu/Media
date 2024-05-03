package com.guanshu.media.utils

import android.util.Log

private const val PREFIX = "QWER"

object Logger {
    fun i(tag: String, message: String) {
        Log.i("$PREFIX-$tag", message)
    }

    fun d(tag: String, message: String) {
        Log.d("$PREFIX-$tag", message)
    }

    fun v(tag: String, message: String) {
        Log.v("$PREFIX-$tag", message)
    }

    fun w(tag: String, message: String) {
        Log.w("$PREFIX-$tag", message)
    }

    fun e(tag: String, message: String) {
        Log.e("$PREFIX-$tag", message)
    }

    fun e(tag: String, message: String, throwable: Throwable) {
        Log.e("$PREFIX-$tag", message, throwable)
    }
}