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
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import com.squareup.moshi.Moshi
import okio.Buffer
import java.util.*

open class JSONEncoder(private val adapters: Array<JSONAdapter>? = null) : Encoder {
    private val moshi: Moshi

    init {
        val builder = Moshi.Builder()
        builder.add(KotlinJsonAdapterFactory())
                .add(Date::class.java, Rfc3339DateJsonAdapter().nullSafe())
                .add(UUID::class.java, UUIDJsonAdapter())
        this.adapters?.forEach { builder.add(it) }
        this.moshi = builder.build()
    }

    @Throws(EncoderException::class)
    override fun <T:Any> encode(value: T): ByteArray? {
        val adapter: JsonAdapter<T> = this.moshi.adapter(value.javaClass)
        val buffer = Buffer()
        adapter.toJson(buffer, value)
        return buffer.readByteArray()
    }

}
