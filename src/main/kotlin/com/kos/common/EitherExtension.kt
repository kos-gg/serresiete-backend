package com.kos.common

import arrow.core.Either

fun <A, B> List<Either<A, B>>.split(): Pair<List<A>, List<B>> {
    val (lefts, rights) = this.partition { it.isLeft() }
    val leftList = lefts.map { it as Either.Left<A> }.map { it.value }
    val rightList = rights.map { it as Either.Right<B> }.map { it.value }
    return Pair(leftList, rightList)
}

fun <A, B> Either<A, B>.getLeftOrNull(): A? = this.swap().getOrNull()

fun <A, B> Either<A, B>.getOrThrow(exception: Throwable): B =
    when (this) {
        is Either.Right -> this.value
        is Either.Left -> throw exception
    }

fun <A : Throwable, B> Either<A, B>.getOrThrow(): B = when (this) {
    is Either.Right -> this.value
    is Either.Left -> throw this.value
}