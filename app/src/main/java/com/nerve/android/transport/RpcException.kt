package com.nerve.android.transport

sealed class RpcException(message: String) : Exception(message) {
    class TransportDisconnected : RpcException("transport disconnected")

    class RequestTimeout(val method: String, val requestId: Long) :
        RpcException("request timeout")

    class ServerError(val code: Int, override val message: String) :
        RpcException(message)
}
