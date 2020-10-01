/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.execution;

import com.google.common.cache.Cache;
import org.gradle.internal.execution.UnitOfWork.Identity;

import javax.annotation.Nullable;

public interface WorkExecutor {
    CachingResult execute(UnitOfWork work, @Nullable String rebuildReason);

    <T> T executeDeferred(UnitOfWork work, @Nullable String rebuildReason, Cache<Identity, CachingResult> cache, DeferredResultProcessor<CachingResult, T> processor);
}
