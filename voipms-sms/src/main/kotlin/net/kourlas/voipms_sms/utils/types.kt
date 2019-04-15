/*
 * VoIP.ms SMS
 * Copyright (C) 2017-2019 Michael Kourlas
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.kourlas.voipms_sms.utils

/**
 * Returns true if the specified value is 1 and false if 0. Throws an exception
 * otherwise.
 */
fun toBoolean(value: Long): Boolean {
    if (value != 0L && value != 1L) {
        throw IllegalArgumentException("value must be 0 or 1")
    }
    return value == 1L
}

/**
 * Returns true if the specified value is "1" and false if "0". Throws an
 * exception otherwise.
 */
fun toBoolean(value: String): Boolean {
    if (value != "0" && value != "1") {
        throw IllegalArgumentException("value must be 0 or 1")
    }
    return value == "1"
}
