/***************************************************************************
 * This source file is part of the HTTPServiceKt open source project.      *
 *                                                                         *
 * Copyright (c) 2020-present, InMotion Software and the project authors   *
 * Licensed under the MIT License                                          *
 *                                                                         *
 * See LICENSE.txt for license information                                 *
 ***************************************************************************/

package com.inmotionsoftware.httpservice.coder

import com.squareup.moshi.*
import java.util.*

class UUIDJsonAdapter {
    @FromJson
    fun fromJson(reader: JsonReader): UUID? {
        val string = reader.nextString()
        return UUID.fromString(string)
    }

    @ToJson
    fun toJson(writer: JsonWriter, value: UUID?) {
        value?.let { writer.value(value.toString()) }
    }
}
