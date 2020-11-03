/***************************************************************************
 * This source file is part of the HTTPServiceKt open source project.      *
 *                                                                         *
 * Copyright (c) 2020-present, InMotion Software and the project authors   *
 * Licensed under the MIT License                                          *
 *                                                                         *
 * See LICENSE.txt for license information                                 *
 ***************************************************************************/

package com.inmotionsoftware.httpservice

import android.util.Log
import com.inmotionsoftware.httpservice.cache.CacheCriteria
import com.inmotionsoftware.httpservice.cache.CachePolicy
import com.inmotionsoftware.httpservice.cache.CacheStore
import com.inmotionsoftware.httpservice.coder.Decoder
import com.inmotionsoftware.httpservice.coder.Encoder
import com.inmotionsoftware.httpservice.coder.JSONDecoder
import com.inmotionsoftware.httpservice.coder.JSONEncoder
import com.inmotionsoftware.promisekt.*
import com.inmotionsoftware.httpservice.concurrent.DispatchExecutor
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.*
import java.lang.reflect.Type
import java.net.URL
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

typealias Headers = Map<String, String>
typealias DecoderRegistry = Map<String, Decoder>
typealias EncoderRegistry = Map<String, Encoder>
typealias TimeInterval = Long

open class HTTPService(val config: HTTPService.Config) {

    sealed class Error : Throwable {
        constructor(): super()
        constructor(message: String): super(message = message)
        constructor(cause: Throwable): super(cause = cause)
        constructor(message: String, cause: Throwable): super(message = message, cause = cause)

        class Generic(message: String = ""): Error(message)
        class UnRecognizedEncoding(message: String): Error(message)
        class Response(val statusCode: StatusCode, val responseMessage: String, val body: ByteArray?, val mimeType: String): Error()
    }

    enum class StatusCode(val code: Int) {
        Error                            (code = 0)
        , Continue                       (code = 100)
        , SwitchingProtocol              (code = 101)
        , Ok                             (code = 200)
        , Created                        (code = 201)
        , Accepted                       (code = 202)
        , NonAuthoritativeInformation    (code = 203)
        , NoContent                      (code = 204)
        , ResetContent                   (code = 205)
        , PartialContent                 (code = 206)
        , MultipleChoice                 (code = 300)
        , MovePermanently                (code = 301)
        , Found                          (code = 302)
        , SeeOther                       (code = 303)
        , NotModified                    (code = 304)
        , UseProxy                       (code = 305)
        , Unused                         (code = 306)
        , TemporaryRedirect              (code = 307)
        , PermanentRedirect              (code = 308)
        , BadRequest                     (code = 400)
        , Unauthorized                   (code = 401)
        , PaymentRequired                (code = 402)
        , Forbidden                      (code = 403)
        , NotFound                       (code = 404)
        , MethodNotAllowed               (code = 405)
        , NotAcceptable                  (code = 406)
        , ProxyAuthenticationRequired    (code = 407)
        , RequestTimeout                 (code = 408)
        , Conflict                       (code = 409)
        , Gone                           (code = 410)
        , LengthRequired                 (code = 411)
        , PreconditionFailed             (code = 412)
        , PayloadTooLarge                (code = 413)
        , UriTooLong                     (code = 414)
        , UnsupportedMediaType           (code = 415)
        , RequestedRangeNotSatisfiable   (code = 416)
        , ExpectationFailed              (code = 417)
        , IAmATeapot                     (code = 418)
        , MisdirectedRequest             (code = 421)
        , UpgradeRequired                (code = 426)
        , PreconditionRequired           (code = 428)
        , TooManyRequests                (code = 429)
        , RequestHeaderFieldsTooLarge    (code = 431)
        , UnavailableForLegalReasons     (code = 451)
        , InternalServerError            (code = 500)
        , NotImplemented                 (code = 501)
        , BadGateway                     (code = 502)
        , ServiceUnavailable             (code = 503)
        , GatewayTimeout                 (code = 504)
        , HttpVersionNotSupported        (code = 505)
        , VariantAlsoNegotiates          (code = 506)
        , VariantAlsoNegotiatesNotProper (code = 507)
        , NetworkAuthenticationRequired  (code = 511)
        ;

        companion object {
            fun withValue(value: Int): StatusCode =
                when(value) {
                    100 -> Continue
                    101 -> SwitchingProtocol
                    200 -> Ok
                    201 -> Created
                    202 -> Accepted
                    203 -> NonAuthoritativeInformation
                    204 -> NoContent
                    205 -> ResetContent
                    206 -> PartialContent
                    300 -> MultipleChoice
                    301 -> MovePermanently
                    302 -> Found
                    303 -> SeeOther
                    304 -> NotModified
                    305 -> UseProxy
                    306 -> Unused
                    307 -> TemporaryRedirect
                    308 -> PermanentRedirect
                    400 -> BadRequest
                    401 -> Unauthorized
                    402 -> PaymentRequired
                    403 -> Forbidden
                    404 -> NotFound
                    405 -> MethodNotAllowed
                    406 -> NotAcceptable
                    407 -> ProxyAuthenticationRequired
                    408 -> RequestTimeout
                    409 -> Conflict
                    410 -> Gone
                    411 -> LengthRequired
                    412 -> PreconditionFailed
                    413 -> PayloadTooLarge
                    414 -> UriTooLong
                    415 -> UnsupportedMediaType
                    416 -> RequestedRangeNotSatisfiable
                    417 -> ExpectationFailed
                    418 -> IAmATeapot
                    421 -> MisdirectedRequest
                    426 -> UpgradeRequired
                    428 -> PreconditionRequired
                    429 -> TooManyRequests
                    431 -> RequestHeaderFieldsTooLarge
                    451 -> UnavailableForLegalReasons
                    500 -> InternalServerError
                    501 -> NotImplemented
                    502 -> BadGateway
                    503 -> ServiceUnavailable
                    504 -> GatewayTimeout
                    505 -> HttpVersionNotSupported
                    506 -> VariantAlsoNegotiates
                    507 -> VariantAlsoNegotiatesNotProper
                    511 -> NetworkAuthenticationRequired
                    else -> Error
                }
        }

        fun isOk(): Boolean =
            when(this) {
                Ok
                , Created
                , Accepted
                , NonAuthoritativeInformation
                , NoContent
                , ResetContent
                , PartialContent -> {
                    true
                }
                else -> false
            }
    }

    enum class MimeType(val value: String) {
        Aac (value = "audio/aac") // AAC audio file
        , Abw (value = "application/x-abiword") // AbiWord document
//      , Arc ("application/octet-stream") // Archive document (multiple files embedded)
        , Avi (value = "video/x-msvideo") // AVI: Audio Video Interleave
        , Azw (value = "application/vnd.amazon.ebook") // Amazon Kindle eBook format
        , Bin (value = "application/octet-stream") // Any kind of binary data
        , Bz (value = "application/x-bzip") // BZip archive
        , Bz2 (value = "application/x-bzip2") // BZip2 archive
        , Csh (value = "application/x-csh") // C-Shell script
        , Css (value = "text/css") // Cascading Style Sheets (CSS)
        , Csv (value = "text/csv") // Comma-separated values (CSV)
        , Doc (value = "application/msword") // Microsoft Word
        , Eot (value = "application/vnd.ms-fontobject") // MS Embedded OpenType fonts
        , Epub (value = "application/epub+zip") // Electronic publication (EPUB)
        , Gif (value = "image/gif") // Graphics Interchange Format (GIF)
        , Html (value = "text/html") // HyperText Markup Language (HTML)
        , Ico (value = "image/x-icon") // Icon format
        , Ics (value = "text/calendar") // iCalendar format
        , Jar (value = "application/java-archive") // Java Archive (JAR)
        , Jpg (value = "image/jpeg") // JPEG images
        , Js (value = "application/javascript") // JavaScript (ECMAScript)
        , Json (value = "application/json") // JSON format
        , Midi (value = "audio/midi") // Musical Instrument Digital Interface (MIDI)
        , Mpeg (value = "video/mpeg") // MPEG Video
        , Mpkg (value = "application/vnd.apple.installer+xml") // Apple Installer Package
        , Odp (value = "application/vnd.oasis.opendocument.presentation") // OpenDocument presentation document
        , Ods (value = "application/vnd.oasis.opendocument.spreadsheet") // OpenDocument spreadsheet document
        , Odt (value = "application/vnd.oasis.opendocument.text") // OpenDocument text document
        , Oga (value = "audio/ogg") // OGG audio
        , Ogv (value = "video/ogg") // OGG video
        , Ogx (value = "application/ogg") // OGG
        , Otf (value = "font/otf") // OpenType font
        , Png (value = "image/png") // Portable Network Graphics
        , Pdf (value = "application/pdf") // Adobe Portable Document Format (PDF)
        , Ppt (value = "application/vnd.ms-powerpoint") // Microsoft PowerPoint
        , Rar (value = "application/x-rar-compressed") // RAR archive
        , Rtf (value = "application/rtf") // Rich Text Format (RTF)
        , Sh (value = "application/x-sh") // Bourne shell script
        , Svg (value = "image/svg+xml") // Scalable Vector Graphics (SVG)
        , Swf (value = "application/x-shockwave-flash") // Small web format (SWF) or Adobe Flash document
        , Tar (value = "application/x-tar") // Tape Archive (TAR)
        , Text (value = "text/plain") // Plain text
        , Tiff (value = "image/tiff") // Tagged Image File Format (TIFF)
        , Ts (value = "application/typescript") // Typescript file
        , Ttf (value = "font/ttf") // TrueType Font
        , Vsd (value = "application/vnd.visio") // Microsoft Visio
        , Wav (value = "audio/x-wav") // Waveform Audio Format
        , Weba (value = "audio/webm") // WEBM audio
        , Webm (value = "video/webm") // WEBM video
        , Webp (value = "image/webp") // WEBP image
        , Woff (value = "font/woff") // Web Open Font Format (WOFF)
        , Woff2 (value = "font/woff2") // Web Open Font Format (WOFF)
        , Xhtml (value = "application/xhtml+xml") // XHTML
        , Xls (value = "application/vnd.ms-excel") // Microsoft Excel
        , Xlsx (value = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        , Xml (value = "application/xml") // XML
        , Xul (value = "application/vnd.mozilla.xul+xml") // XUL
        , Zip (value = "application/zip") // ZIP archive
        , Multipart (value = "multipart/form-data")
        , xWWWFormUrlencoded (value = "application/x-www-form-urlencoded")
    }

    enum class Method(val value: String) {
        Get(value = "GET")
        , Post(value = "POST")
        , Patch(value = "PATCH")
        , Put(value = "PUT")
        , Head(value = "HEAD")
        , Delete(value = "DELETE")
        , Options(value = "OPTIONS")
        , Connect(value = "CONNECT")
    }

    data class Config(
        var baseUrl: URL?
        , var headers: Headers = emptyMap()
        , var requestTimeoutInterval: TimeInterval = 30
        , var isAlwaysTrustHost: Boolean = false
        , var enableHttpLogging: Boolean = false
        , var responseExecutor: Executor? = null
        , var decoders: DecoderRegistry = mapOf(
            "application/json" to JSONDecoder()
        )
        , var encoders: EncoderRegistry = mapOf(
            "application/json" to JSONEncoder()
        )
        ,  var cacheStore: CacheStore? = null
    )

    sealed class UploadBody<T:Any> {
        class Empty(val headers: Headers? = null) : UploadBody<Unit>()
        class Json<T:Any>(val value: T, val headers: Headers? = null) : UploadBody<T>()
        class JsonCustom<T:Any>(val value: T, val headers: Headers? = null, val mimeType : String = MimeType.Json.value) : UploadBody<T>()
        class FormUrlEncoded(val value: QueryParameters, val headers: Headers? = null) : UploadBody<QueryParameters>()
        class Binary(val value: ByteArray, val headers: Headers? = null) : UploadBody<ByteArray>()
        class MultipartFormBody(val parameters: QueryParameters, val headers: Headers? = null) : UploadBody<Unit>()

        internal fun requestBody(encoder: (String) -> Encoder?): RequestBody? =
                when(this) {
                    is UploadBody.Empty -> byteArrayOf().toRequestBody()
                    is Json -> {
                        val aEncoder = encoder(this.mimeType()) ?: JSONEncoder()
                        aEncoder.encode(value = this.value)?.toRequestBody(contentType = this.mimeType().toMediaTypeOrNull())
                    }
                    is JsonCustom -> {
                        val aEncoder = encoder(this.mimeType()) ?: JSONEncoder()
                        aEncoder.encode(value = this.value)?.toRequestBody(contentType = this.mimeType().toMediaTypeOrNull())
                    }
                    is FormUrlEncoded -> {
                        val builder = FormBody.Builder()
                        for ((name, values) in this.value.fields) {
                            values.forEach { value ->
                                builder.add(name, value)
                            }
                        }
                        builder.build()
                    }
                    is Binary -> this.value.toRequestBody(contentType = this.mimeType().toMediaTypeOrNull())
                    is MultipartFormBody -> {
                        val builder = MultipartBody.Builder()
                            .setType(MultipartBody.FORM)

                        for ((name, values) in this.parameters.fields) {
                            values.forEach { value ->
                                if (name == "image") {
                                    val file = File(value).asRequestBody(MimeType.Jpg.value.toMediaTypeOrNull())
                                    builder.addFormDataPart("file", name, file)
                                } else {
                                    builder.addFormDataPart(name, value)
                                }
                            }
                        }
                        builder.build()
                    }
                }

        private fun mimeType(): String =
                when(this) {
                    is Empty -> ""
                    is Json -> MimeType.Json.value
                    is JsonCustom -> this.mimeType
                    is FormUrlEncoded -> MimeType.xWWWFormUrlencoded.value
                    is Binary -> MimeType.Bin.value
                    is MultipartFormBody-> MimeType.Multipart.value
                }

        internal fun headers(): Headers? =
                when(this) {
                    is Empty -> this.headers
                    is Json -> this.headers
                    is JsonCustom -> this.headers
                    is FormUrlEncoded -> this.headers
                    is Binary -> this.headers
                    is MultipartFormBody -> this.headers
                }
    }

    data class Result(val mimeType: String, val body: ByteArray)

    //
    // Cache
    //

    private data class CacheValue(val mimeType: String, val body: ByteArray): Serializable

    private fun CacheValue.toByteArray(): ByteArray? {
        var byteOut: ByteArrayOutputStream? = null
        var out: ObjectOutputStream? = null

        return try {
            byteOut = ByteArrayOutputStream()
            out = ObjectOutputStream(byteOut)
            out.writeObject(this)
            byteOut.toByteArray()
        } catch(ignore: Exception) {
            null
        } finally {
            out?.close()
            byteOut?.close()
        }
    }

    private fun ByteArray.cacheValue(): CacheValue? {
        var byteIn: ByteArrayInputStream? = null
        var `in`: ObjectInputStream? = null

        return try {
            byteIn = ByteArrayInputStream(this)
            `in` = ObjectInputStream(byteIn)
            `in`.readObject() as CacheValue?
        } catch (ignore: Exception) {
            null
        }
        finally {
            `in`?.close()
            byteIn?.close()
        }
    }

    private fun Promise<ByteArray?>.cacheValue(): Promise<CacheValue?>
            = this.map(on = DispatchExecutor.global) { it?.cacheValue() }

    //
    // Session
    //

    private class Session(private val config: Config) {

        val client: OkHttpClient

        init {
            val builder = OkHttpClient.Builder()

            if (config.enableHttpLogging) {
                val logger = HttpLoggingInterceptor(HttpLoggingInterceptor.Logger {
                    Log.d("HttpService", it)
                })
                logger.level = HttpLoggingInterceptor.Level.BODY
                builder.addInterceptor(logger)
            }

            val specs = ArrayList<ConnectionSpec>()
            if (config.isAlwaysTrustHost) {
                specs.add(ConnectionSpec.CLEARTEXT)
            }

            val tlsSpec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                    .tlsVersions(TlsVersion.TLS_1_2)
                    .build()

            specs.add(tlsSpec)
            builder.connectionSpecs(specs)

            builder.connectTimeout(config.requestTimeoutInterval, TimeUnit.SECONDS)
                    .readTimeout(config.requestTimeoutInterval, TimeUnit.SECONDS)

            this.client = builder.build()
        }
    }

    //
    // HTTPService
    //

    private val session: HTTPService.Session

    init {
        this.session = HTTPService.Session(this.config)
    }

    internal fun <T:Any> upload(method: Method, route: String, query: QueryParameters? = null, body: UploadBody<T>): Promise<Result?> {
        val urlBuilder = this.resolveRoute(route = route).addQuery(query = query)
        val requestBody = body.requestBody(encoder = this::encoder)
        val request = Request.Builder()
                .url(urlBuilder.build())
                .method(method.value, requestBody)
                .headers(this.headers(additionalHeaders = body.headers()))
                .build()

        return this.request(request = request)
    }

    internal fun download(method: Method, url: HttpUrl): Promise<Result?> {
        val request = Request.Builder()
                .url(url)
                .method(method.value, null)
                .headers(this.headers())
                .build()

        return this.request(request = request)
    }

    internal fun download(method: Method, route: String, query: QueryParameters? = null, cacheCriteria: CacheCriteria? = null): Promise<Result?> {
        val url = this.resolveRoute(route).addQuery(query = query).build()
        val cacheable = (cacheCriteria != null && this.config.cacheStore != null)
        val cacheKey = this.cacheKey(url = url, cacheCriteria = cacheCriteria)

        val cachedData: Promise<CacheValue?> =
                if (cacheable) {
                    when(cacheCriteria?.policy) {
                        CachePolicy.useAge ->
                            this.config.cacheStore?.get(key = cacheKey, maxAge = cacheCriteria.age)?.cacheValue()
                        CachePolicy.returnCacheElseLoad ->
                            this.config.cacheStore?.get(key = cacheKey)?.cacheValue()
                        else -> null
                    }
                } else {
                    null
                } ?: Promise.value<CacheValue?>(value = null)

        return cachedData.thenMap { cacheValue ->
                if (cacheValue != null) {
                    return@thenMap Promise.value<Result?>(Result(mimeType = cacheValue.mimeType, body = cacheValue.body))
                } else {
                    return@thenMap this.download(method = method, url = url).map { result ->
                        result?.body?.let {
                            if (cacheable) {
                                CacheValue(mimeType = result.mimeType, body = it).toByteArray()?.let { bytes ->
                                    this.config.cacheStore?.put(key = cacheKey, value = bytes)
                                }
                            }
                        }
                        return@map result
                    }
                }
            }
            .recover { error ->
                if (!cacheable) throw error
                return@recover (
                    when(cacheCriteria?.policy) {
                        CachePolicy.reloadReturnCacheIfError -> {
                            this.config.cacheStore?.get(key = cacheKey)?.cacheValue()
                        }
                        CachePolicy.useAgeReturnCacheIfError,
                        CachePolicy.reloadReturnCacheWithAgeCheckIfError -> {
                            this.config.cacheStore?.get(key = cacheKey, maxAge = cacheCriteria.age)?.cacheValue()
                        }
                        else -> null
                    }
                    ?: Promise.value<CacheValue?>(value = null)
                ).map {
                    if (it == null) throw error
                    return@map Result(mimeType = it.mimeType, body = it.body)
                }
            }
    }

    //
    // Private Methods
    //

    private fun request(request: Request): Promise<Result?> {
        return Promise.value(Unit).thenMap(on = DispatchExecutor.global) {
            var body: ByteArray? = null
            var mimeType = ""
            val response = this.session.client.newCall(request).execute()
            response.body?.let {resp ->
                body = resp.bytes()
                mimeType = resp.contentType().toString()
            }

            if (!response.isSuccessful) {
                throw response.serviceError(body = body, mimeType = mimeType) ?: HTTPService.Error.Generic()
            }
            val result = if (body == null) null else Result(mimeType = mimeType, body = body!!)
            return@thenMap Promise.value(result)
        }
    }

    private fun resolveRoute(route: String): HttpUrl.Builder {
        val baseUrl = this.config.baseUrl ?: return URL(route).toHttpUrlOrNull()?.newBuilder() ?: HttpUrl.Builder()
        val urlBuilder = baseUrl.toHttpUrlOrNull()?.newBuilder()
        if (route.isNotEmpty()) urlBuilder?.addPathSegments(if (route.firstOrNull() == '/') route.drop(1) else route )
        return urlBuilder ?: HttpUrl.Builder()
    }

    private fun headers(additionalHeaders: Headers? = null): okhttp3.Headers {
        val builder = okhttp3.Headers.Builder()
        val headers = HashMap<String, String>().apply {
            putAll(this@HTTPService.config.headers)
            additionalHeaders?.also {
                putAll(it)
            }
        }
        for ((name, value) in headers) {
            builder.add(name, value)
        }
        return builder.build()
    }

    private fun cacheKey(url: HttpUrl, cacheCriteria: CacheCriteria?): String {
        cacheCriteria?.cacheKey?.let { if (it.isNotEmpty()) return it }
        return url.toString()
    }

}

//
// Promise Extension
//

private fun <RT> Promise<HTTPService.Result?>.decode(on: Executor?, type: Class<RT>, decoder: (mimeType: String) -> Decoder?): Promise<RT> {
    return this.map(on = on){ result ->
        val aDecoder = getDecoderForMimeType(result, decoder)

        // Expecting a value. Otherwise throw the exception that can be caught by the Promise
        // If result is null it would have already thrown an exception
        return@map aDecoder.decode(type = type, value = result!!.body)!!
    }
}

private fun <RT> Promise<HTTPService.Result?>.decode(on: Executor?, type: Type, decoder: (mimeType: String) -> Decoder?): Promise<RT> {
    return this.map(on = on){ result ->
        val aDecoder = getDecoderForMimeType(result, decoder)

        // Expecting a value. Otherwise throw the exception that can be caught by the Promise
        // If result is null it would have already thrown an exception
        return@map aDecoder.decode<RT>(type = type, value = result!!.body)!!
    }
}

private fun getDecoderForMimeType(result: HTTPService.Result?, decoder: (mimeType: String) -> Decoder?): Decoder {
    if (result == null) throw HTTPService.Error.Generic(message = "Invalid result for decoding.")
    return decoder(result.mimeType) ?: throw HTTPService.Error.UnRecognizedEncoding(message = "No decoder is found for mimeType $result.mimeType")
}

//
// HTTPService Extension
//

fun MediaType.isJson(): Boolean {
    val subtype = this.subtype.toLowerCase(Locale.getDefault())
    return when (subtype) {
        "json", "javascript" -> true
        else -> subtype.endsWith("+json", false)
    }
}

fun HTTPService.decoder(mimeType: String): Decoder? {
    val mediaType = mimeType.toMediaTypeOrNull() ?: return null
    val subtype = mediaType.subtype.toLowerCase(Locale.getDefault())

    var decoder: Decoder? = null
    for ((key, value) in this.config.decoders) {
        val decoderMediaType = key.toMediaTypeOrNull() ?: continue
        if ((mediaType.isJson() && decoderMediaType.isJson())
            || subtype == decoderMediaType.subtype.toLowerCase(Locale.getDefault())) {
            decoder = value
            break
        }
    }
    return decoder ?: if (mediaType.isJson()) JSONDecoder() else null
}

fun HTTPService.encoder(mimeType: String): Encoder? {
    val mediaType = mimeType.toMediaTypeOrNull() ?: return null
    val subtype = mediaType.subtype.toLowerCase(Locale.getDefault())

    var encoder: Encoder? = null
    for ((key, value) in this.config.encoders) {
        val encoderMediaType = key.toMediaTypeOrNull() ?: continue
        if ((mediaType.isJson() && encoderMediaType.isJson())
            || subtype == encoderMediaType.subtype.toLowerCase(Locale.getDefault())) {
            encoder = value
            break
        }
    }
    return encoder ?: if (mediaType.isJson()) JSONEncoder() else null
}

//
// HTTPService HEAD & DELETE
//

fun HTTPService.head(route: String) : Promise<Unit>
        = this.download(method = HTTPService.Method.Head, route = route).asVoid()

fun HTTPService.delete(route: String): Promise<Unit>
        = this.download(method = HTTPService.Method.Delete, route = route).asVoid()

fun HTTPService.delete(route: String, query: QueryParameters? = null): Promise<Unit>
        = this.download(method = HTTPService.Method.Delete, route = route, query = query).asVoid()

fun <T> HTTPService.delete(route: String, type: Class<T>): Promise<T>
        = this.download(method = HTTPService.Method.Delete, route = route)
    .decode(on = this.config.responseExecutor, type = type, decoder = this::decoder)

fun <T> HTTPService.delete(route: String, type: Type): Promise<T>
        = this.download(method = HTTPService.Method.Delete, route = route)
    .decode(on = this.config.responseExecutor, type = type, decoder = this::decoder)

fun <T> HTTPService.delete(route: String, query: QueryParameters, type: Class<T>): Promise<T>
        = this.download(method = HTTPService.Method.Delete, route =  route, query = query )
    .decode(on = this.config.responseExecutor, type = type, decoder = this::decoder)

fun <T> HTTPService.delete(route: String, query: QueryParameters, type: Type): Promise<T>
        = this.download(method = HTTPService.Method.Delete, route = route, query = query)
    .decode(on = this.config.responseExecutor, type = type, decoder = this::decoder)

//
// HTTPService GET
//

fun HTTPService.get(route: String, query: QueryParameters? = null, cacheCriteria: CacheCriteria? = null): Promise<HTTPService.Result?>
        = this.download(method = HTTPService.Method.Get, route = route, query = query, cacheCriteria = cacheCriteria)

fun <T> HTTPService.get(route: String, query: QueryParameters? = null, type: Class<T>, cacheCriteria: CacheCriteria? = null): Promise<T>
        = this.get(route = route, query = query, cacheCriteria = cacheCriteria)
              .decode(on = this.config.responseExecutor, type = type, decoder = this::decoder)

fun <T> HTTPService.get(route: String, query: QueryParameters? = null, type: Type, cacheCriteria: CacheCriteria? = null): Promise<T>
        = this.get(route = route, query = query, cacheCriteria = cacheCriteria)
              .decode(on = this.config.responseExecutor, type = type, decoder = this::decoder)

//
// HTTPService POST
//

fun <T:Any> HTTPService.post(route: String, query: QueryParameters? = null, body: HTTPService.UploadBody<T>): Promise<HTTPService.Result?>
        = this.upload(method = HTTPService.Method.Post, route = route, query = query, body = body)

fun <T, B:Any> HTTPService.post(route: String, query: QueryParameters? = null, body: HTTPService.UploadBody<B>, type: Class<T>): Promise<T>
        = this.post(route = route, query = query, body = body)
              .decode(on = this.config.responseExecutor, type = type, decoder = this::decoder)

fun <T, B:Any> HTTPService.post(route: String, query: QueryParameters? = null, body: HTTPService.UploadBody<B>, type: Type): Promise<T>
        = this.post(route = route, query = query, body = body)
              .decode(on = this.config.responseExecutor, type = type, decoder = this::decoder)

//
// HTTPService PATCH
//

fun <T:Any> HTTPService.patch(route: String, query: QueryParameters? = null, body: HTTPService.UploadBody<T>): Promise<Unit>
        = this.upload(method = HTTPService.Method.Patch, route = route, query = query, body = body).asVoid()

//
// HTTPService PUT
//

fun <T:Any> HTTPService.put(route: String, query: QueryParameters? = null, body: HTTPService.UploadBody<T>): Promise<HTTPService.Result?>
        = this.upload(method = HTTPService.Method.Put, query = query, route = route, body = body)

fun <T, B:Any> HTTPService.put(route: String, query: QueryParameters? = null,  body: HTTPService.UploadBody<B>, type: Class<T>): Promise<T>
        = this.put(route = route, query = query, body = body)
    .decode(on = this.config.responseExecutor, type = type, decoder = this::decoder)

fun <T, B:Any> HTTPService.put(route: String, query: QueryParameters? = null, body: HTTPService.UploadBody<B>, type: Type): Promise<T>
        = this.put(route = route, query = query, body = body)
              .decode(on = this.config.responseExecutor, type = type, decoder = this::decoder)

//
// HttpUrl.Builder Extension
//

private fun HttpUrl.Builder.addQuery(query: QueryParameters?): HttpUrl.Builder {
    query?.let {
        for ((name, values) in it.fields) {
            values.forEach { value -> this.addQueryParameter(name, value) }
        }
    }
    return this
}

//
// Response Extension
//

private fun Response.serviceError(body: ByteArray?, mimeType: String): HTTPService.Error? {
    val statusCode = HTTPService.StatusCode.withValue(value = this.code)
    if (statusCode.isOk()) return null

    return  HTTPService.Error.Response(statusCode = statusCode, responseMessage = this.message, body = body, mimeType = mimeType)
}
