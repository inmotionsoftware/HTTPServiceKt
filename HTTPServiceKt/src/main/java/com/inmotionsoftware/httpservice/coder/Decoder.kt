/***************************************************************************
 * This source file is part of the HTTPServiceKt open source project.      *
 *                                                                         *
 * Copyright (c) 2020-present, InMotion Software and the project authors   *
 * Licensed under the MIT License                                          *
 *                                                                         *
 * See LICENSE.txt for license information                                 *
 ***************************************************************************/

package com.inmotionsoftware.httpservice.coder

import java.lang.reflect.Type

class DecoderException : Throwable {
    constructor(): super()
    constructor(message: String): super(message = message)
    constructor(cause: Throwable): super(cause = cause)
    constructor(message: String, cause: Throwable): super(message = message, cause = cause)
}

/**
 * A type that can decode values from a native format into in-memory representations.
 */
interface Decoder {

    @Throws(DecoderException::class)
    fun <T> decode(type: Class<T>, value: ByteArray): T?

    @Throws(DecoderException::class)
    fun <T> decode(type: Type, value: ByteArray): T?

}
