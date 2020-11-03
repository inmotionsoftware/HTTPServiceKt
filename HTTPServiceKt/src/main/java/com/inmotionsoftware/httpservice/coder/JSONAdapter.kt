/***************************************************************************
 * This source file is part of the HTTPServiceKt open source project.      *
 *                                                                         *
 * Copyright (c) 2020-present, InMotion Software and the project authors   *
 * Licensed under the MIT License                                          *
 *                                                                         *
 * See LICENSE.txt for license information                                 *
 ***************************************************************************/

package com.inmotionsoftware.httpservice.coder

import com.squareup.moshi.Types
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

interface JSONAdapter

class CodableTypes {
    companion object {
        fun newParameterizedType(rawType: Type, typeArgument: Type): ParameterizedType {
            return Types.newParameterizedType(rawType, typeArgument)
        }
    }
}
