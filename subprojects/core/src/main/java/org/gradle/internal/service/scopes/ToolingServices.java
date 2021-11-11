/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.service.scopes;

import org.gradle.internal.service.ServiceRegistration;

public class ToolingServices extends AbstractPluginServiceRegistry {

    @Override
    public void registerBuildServices(ServiceRegistration registration) {
        registration.addProvider(new ExceptionServices());
    }

    // TODO (donat) @VisibleForTesting (e.g. GradleUserHomeServices for testing)
    static class ExceptionServices {
        public ExceptionCollector createExceptionSuppressor() {
            // TODO (donat) is classpath is the only value to suppress warnings? there's lenient mode and strict-classpath
            // TODO (donat) should we push the calculation to the kotlin-dsl?
            String classpathMode = System.getProperty("org.gradle.kotlin.dsl.provider.mode");
            boolean expressionsSuppressed = classpathMode != null && classpathMode.contains("classpath");
            return new ExceptionCollector(expressionsSuppressed);
        }
    }
}
