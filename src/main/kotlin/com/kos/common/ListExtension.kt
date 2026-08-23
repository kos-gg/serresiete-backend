package com.kos.common

fun <A, B> List<A>.collect(filter: (A) -> Boolean, map: (A) -> B): List<B> = this.filter(filter).map(map)