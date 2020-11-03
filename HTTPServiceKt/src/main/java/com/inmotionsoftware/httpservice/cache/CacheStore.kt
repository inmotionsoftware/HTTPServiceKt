/***************************************************************************
 * This source file is part of the HTTPServiceKt open source project.      *
 *                                                                         *
 * Copyright (c) 2020-present, InMotion Software and the project authors   *
 * Licensed under the MIT License                                          *
 *                                                                         *
 * See LICENSE.txt for license information                                 *
 ***************************************************************************/

package com.inmotionsoftware.httpservice.cache

import com.inmotionsoftware.promisekt.Promise

interface CacheStore {

    fun get(key: String) : Promise<ByteArray?>

    fun get(key: String, maxAge: Long): Promise<ByteArray?>

    fun put(key: String, value: ByteArray): Promise<Unit>

    fun put(key: String, value: ByteArray, flush: Boolean): Promise<Unit>

    fun remove(key: String): Promise<Unit>

}
