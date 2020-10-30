/***************************************************************************
 * This source file is part of the HTTPServiceKt open source project.      *
 *                                                                         *
 * Copyright (c) 2020-present, InMotion Software and the project authors   *
 * Licensed under the MIT License                                          *
 *                                                                         *
 * See LICENSE.txt for license information                                 *
 ***************************************************************************/

package com.inmotionsoftware.httpservice.cache

enum class CacheAge(val interval: Long){
    oneMinute (interval = 60)
    , oneHour (interval = 60 * 60)
    , oneDay  ( interval = 60 * 60 * 24)
    , immortal (interval = Long.MAX_VALUE)
}

enum class CachePolicy {
    useAge
    , useAgeReturnCacheIfError
    , returnCacheElseLoad
    , reloadReturnCacheIfError
    , reloadReturnCacheWithAgeCheckIfError
}

data class CacheCriteria(val policy: CachePolicy, val age: Long, val cacheKey: String? = null)
