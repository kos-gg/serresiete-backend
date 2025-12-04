package com.kos

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

fun assertTrue(boolean: Boolean?) = when (boolean) {
    null -> fail()
    else -> assertTrue(boolean)
}

fun assertFalse(boolean: Boolean?) =  when (boolean) {
    null -> fail()
    else -> assertFalse(boolean)
}