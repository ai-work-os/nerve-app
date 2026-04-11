package com.nerve.android.transport

import kotlinx.serialization.json.JsonPrimitive

val JsonPrimitive.long: Long
    get() = content.toLong()

val JsonPrimitive.longOrNull: Long?
    get() = content.toLongOrNull()

val JsonPrimitive.boolean: Boolean
    get() = content.toBooleanStrict()

val JsonPrimitive.booleanOrNull: Boolean?
    get() = content.toBooleanStrictOrNull()
