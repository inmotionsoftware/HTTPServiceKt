package com.inmotionsoftware.httpservice.coder

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import java.net.URI

class URIJsonAdapter {
    @FromJson
    fun fromJson(uri: String?): URI? {
        return uri?.let { URI(it) }
    }

    @ToJson
    fun toJson(value: URI?): String? {
        return value?.toString()
    }
}
