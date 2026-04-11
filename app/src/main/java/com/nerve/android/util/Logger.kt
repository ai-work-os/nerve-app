package com.nerve.android.util

object Logger {
    fun d(tag: String, message: String) {
        println("D/$tag $message")
    }

    fun w(tag: String, message: String) {
        println("W/$tag $message")
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        println("E/$tag $message")
        throwable?.printStackTrace()
    }
}
