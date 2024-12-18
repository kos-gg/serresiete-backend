package com.kos.common

import kotlinx.serialization.json.*

fun JsonObject.requireString(key: String): String = this[key]?.jsonPrimitive?.content
    ?: throw IllegalArgumentException("Key '$key' not found or not a string.")

fun JsonObject.requireInt(key: String): Int = this[key]?.jsonPrimitive?.int
    ?: throw IllegalArgumentException("Key '$key' not found or not an int.")

fun JsonObject.requireDouble(key: String): Double = this[key]?.jsonPrimitive?.double
    ?: throw IllegalArgumentException("Key '$key' not found or not a double.")

fun JsonObject.requireNestedString(parentKey: String, childKey: String): String {
    val nested = this[parentKey]?.jsonObject
        ?: throw IllegalArgumentException("Parent key '$parentKey' not found or not an object.")
    return nested.requireString(childKey)
}