/***************************************************************************
 * This source file is part of the HTTPServiceKt open source project.      *
 *                                                                         *
 * Copyright (c) 2020-present, InMotion Software and the project authors   *
 * Licensed under the MIT License                                          *
 *                                                                         *
 * See LICENSE.txt for license information                                 *
 ***************************************************************************/

package com.inmotionsoftware.httpservice.coder

class EncoderException : Throwable {
    constructor(): super()
    constructor(message: String): super(message = message)
    constructor(cause: Throwable): super(cause = cause)
    constructor(message: String, cause: Throwable): super(message = message, cause = cause)
}

/**
 * A type that can encode values into a native format for external representation.
 */
interface Encoder {

    @Throws(EncoderException::class)
    fun <T : Any> encode(value: T): ByteArray?

}
