/*
 * VoIP.ms SMS
 * Copyright (C) 2021 Michael Kourlas
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

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * Temporary implementation of a coroutine-compatible read-write lock.
 *
 * This should be removed once coroutine-compatible read-write locks are
 * supported by the Kotlin standard library.
 */
class CoroutineReadWriteLock(val readers: Int) {
    val r = Semaphore(readers)
    val w = Mutex()

    suspend inline fun <T> read(action: () -> T): T {
        // As long as at least one reader holds a permit from r, a writer
        // will not be able to obtain the write lock.
        return r.withPermit(action)
    }

    suspend inline fun <T> write(action: () -> T): T {
        // We acquire w to ensure that only one writer is trying to acquire all
        // the permits at any given time.
        return w.withLock {
            var permitsAcquired = 0
            try {
                // To obtain a write lock, we must acquire all of the permits
                // provided by r.
                repeat(readers) {
                    r.acquire()
                    permitsAcquired += 1
                }
                action()
            } finally {
                // The acquire() function can throw a CancellationException
                // while we are in the middle of acquiring permits, so we must
                // track the number of permits we actually acquired and only
                // free those.
                repeat(permitsAcquired) {
                    r.release()
                }
            }
        }
    }
}