/***************************************************************************
 * This source file is part of the HTTPServiceKt open source project.      *
 *                                                                         *
 * Copyright (c) 2020-present, InMotion Software and the project authors   *
 * Licensed under the MIT License                                          *
 *                                                                         *
 * See LICENSE.txt for license information                                 *
 ***************************************************************************/

package com.inmotionsoftware.httpservice.cache

import android.util.LruCache
import com.inmotionsoftware.httpservice.concurrent.DispatchExecutor
import com.inmotionsoftware.httpservice.internal.okhttp3.Util
import com.inmotionsoftware.promisekt.Promise
import com.inmotionsoftware.promisekt.map
import okhttp3.internal.cache.DiskLruCache
import okhttp3.internal.io.FileSystem
import okio.BufferedSource
import okio.buffer
import java.io.File
import java.io.IOException
import java.util.*

class MemDiskLruCacheStore(
    private val fileSystem: FileSystem
    , private val directory: File
    , private val diskCacheSize: Long
    , private val memCacheSize: Int) : CacheStore {

    constructor(directory: File, diskCacheSize: Long, memCacheSize: Int):
            this(fileSystem = FileSystem.SYSTEM, directory = directory, diskCacheSize = diskCacheSize, memCacheSize = memCacheSize)

    private companion object {
        val VERSION = 201710
        val ENTRY_METADATA = 0
        val ENTRY_COUNT = 1
    }

    private val memCache: LruCache<String, ByteArray> = LruCache(this.memCacheSize)
    private val diskCache: DiskLruCache = DiskLruCache.create(this.fileSystem, this.directory, VERSION, ENTRY_COUNT, this.diskCacheSize)

    override fun get(key: String): Promise<ByteArray?> = this.get(key = key, maxAge = Long.MAX_VALUE)

    override fun get(key: String, maxAge: Long): Promise<ByteArray?> {
        return Promise.value(Unit).map(on = DispatchExecutor.global ) {
            val cacheKey = this.makeCacheKey(key = key)

            // Read from mem cache first
            var cachedValue = this.memCache.get(cacheKey)
            if (cachedValue == null) {
                // Fallback to disk cache
                var snapshot: DiskLruCache.Snapshot? = null
                try {
                    snapshot = this.diskCache.get(cacheKey)
                    snapshot?.let {
                        val source = it.getSource(ENTRY_METADATA).buffer()
                        val elapsedTimeInSec = (Date().time - this.readLong(source = source)) / 1000
                        if (elapsedTimeInSec < maxAge) {
                            cachedValue = source.readByteArray()
                        }
                    }
                }
                finally {
                    snapshot?.let {
                        Util.closeQuietly(it)
                    }
                }
            }
            cachedValue
        }
    }

    override fun put(key: String, value: ByteArray): Promise<Unit> = this.put(key = key, value = value, flush = false)

    override fun put(key: String, value: ByteArray, flush: Boolean): Promise<Unit> {
        return Promise.value(Unit).map(on = DispatchExecutor.global) {
            val cacheKey = this.makeCacheKey(key = key)
            this.memCache.put(key, value)

            var editor: DiskLruCache.Editor? = null
            try {
                editor = this.diskCache.edit(cacheKey)
                editor?.let {
                    val sink = it.newSink(ENTRY_METADATA).buffer()

                    val timeStamp = Date().time
                    sink.writeDecimalLong(timeStamp)
                    sink.writeByte('\n'.toInt())
                    sink.write(value)
                    sink.close()
                    it.commit()
                }
            } catch (ignored: Throwable) {
                this.abortQuietly(editor = editor)
            }
            if (flush) this.diskCache.flush()
        }
    }

    override fun remove(key: String): Promise<Unit> {
        return Promise.value(Unit).map(on = DispatchExecutor.global) {
            val cacheKey = this.makeCacheKey(key)
            this.memCache.remove(cacheKey)
            this.diskCache.remove(cacheKey)
            Unit
        }
    }

    //
    // Private Methods
    //

    private fun makeCacheKey(key: String): String = Util.md5Hex(key)

    private fun abortQuietly(editor: DiskLruCache.Editor?) {
        // Give up because the cache cannot be written.
        try {
            editor?.abort()
        } catch (ignored: IOException) {
        }
    }

    @Throws(IOException::class)
    private fun readLong(source: BufferedSource): Long {
        try {
            val result = source.readDecimalLong()
            val line = source.readUtf8LineStrict()
            if (result < 0 || !line.isEmpty()) {
                throw IOException("Expected a long but was \"" + result + line + "\"")
            }
            return result
        } catch (e: NumberFormatException) {
            throw IOException(e.message)
        }
    }

}
