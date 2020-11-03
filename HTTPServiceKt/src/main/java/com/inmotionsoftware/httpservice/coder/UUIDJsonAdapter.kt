/***************************************************************************
 * This source file is part of the HTTPServiceKt open source project.      *
 *                                                                         *
 * Copyright (c) 2020-present, InMotion Software and the project authors   *
 * Licensed under the MIT License                                          *
 *                                                                         *
 * See LICENSE.txt for license information                                 *
 ***************************************************************************/

package com.inmotionsoftware.httpservice.coder

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import java.util.*

class UUIDJsonAdapter: JsonAdapter<UUID>() {
    override fun fromJson(reader: JsonReader): UUID? {
        val string = reader.nextString()
        return UUID.fromString(string)
    }

    override fun toJson(writer: JsonWriter, value: UUID?) {
        value?.let { writer.value(value.toString()) }
    }
}
