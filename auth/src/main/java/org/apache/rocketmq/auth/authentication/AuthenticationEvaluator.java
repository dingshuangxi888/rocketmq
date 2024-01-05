/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rocketmq.auth.authentication;

import java.util.function.Supplier;
import org.apache.rocketmq.auth.authentication.context.AuthenticationContext;
import org.apache.rocketmq.auth.authentication.factory.AuthenticationFactory;
import org.apache.rocketmq.auth.authentication.strategy.AuthenticationStrategy;
import org.apache.rocketmq.auth.config.AuthConfig;

public class AuthenticationEvaluator {

    private final AuthenticationStrategy authenticationStrategy;

    public AuthenticationEvaluator(AuthConfig authConfig) {
        this(authConfig, null);
    }

    public AuthenticationEvaluator(AuthConfig authConfig, Supplier<?> metadataService) {
        this.authenticationStrategy = AuthenticationFactory.getStrategy(authConfig, metadataService);
    }

    public void evaluate(AuthenticationContext context) {
        this.authenticationStrategy.evaluate(context);
    }
}
