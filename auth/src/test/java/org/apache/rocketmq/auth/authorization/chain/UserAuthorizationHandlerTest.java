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
package org.apache.rocketmq.auth.authorization.chain;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.apache.rocketmq.auth.authentication.enums.UserStatus;
import org.apache.rocketmq.auth.authentication.enums.UserType;
import org.apache.rocketmq.auth.authentication.exception.AuthenticationException;
import org.apache.rocketmq.auth.authentication.model.User;
import org.apache.rocketmq.auth.authentication.provider.AuthenticationMetadataProvider;
import org.apache.rocketmq.auth.authorization.context.DefaultAuthorizationContext;
import org.apache.rocketmq.auth.authorization.exception.AuthorizationException;
import org.apache.rocketmq.auth.authorization.model.Resource;
import org.apache.rocketmq.common.action.Action;
import org.apache.rocketmq.common.chain.HandlerChain;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class UserAuthorizationHandlerTest {

    private AuthenticationMetadataProvider metadataProvider;
    private UserAuthorizationHandler handler;
    private HandlerChain<DefaultAuthorizationContext, CompletableFuture<Void>> chain;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        this.metadataProvider = mock(AuthenticationMetadataProvider.class);
        this.handler = new UserAuthorizationHandler(metadataProvider);
        this.chain = mock(HandlerChain.class);
        when(this.chain.handle(any())).thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    public void handleRejectsDisabledUser() {
        User persisted = User.of("test");
        persisted.setUserStatus(UserStatus.DISABLE);
        when(metadataProvider.getUser("test"))
            .thenReturn(CompletableFuture.completedFuture(persisted));

        // the request subject carries no user status, only the username
        CompletionException exception = Assert.assertThrows(CompletionException.class,
            () -> handler.handle(newContext(), chain).join());
        Assert.assertTrue(exception.getCause() instanceof AuthenticationException);
        verify(chain, never()).handle(any());
    }

    @Test
    public void handleRejectsUnknownUser() {
        when(metadataProvider.getUser("test"))
            .thenReturn(CompletableFuture.completedFuture(null));

        CompletionException exception = Assert.assertThrows(CompletionException.class,
            () -> handler.handle(newContext(), chain).join());
        Assert.assertTrue(exception.getCause() instanceof AuthorizationException);
        verify(chain, never()).handle(any());
    }

    @Test
    public void handleContinuesChainForEnabledUser() {
        User persisted = User.of("test");
        persisted.setUserType(UserType.NORMAL);
        persisted.setUserStatus(UserStatus.ENABLE);
        when(metadataProvider.getUser("test"))
            .thenReturn(CompletableFuture.completedFuture(persisted));

        DefaultAuthorizationContext context = newContext();
        handler.handle(context, chain).join();
        verify(chain).handle(context);
    }

    @Test
    public void handleAllowsSuperUserWithoutChain() {
        User persisted = User.of("test");
        persisted.setUserType(UserType.SUPER);
        persisted.setUserStatus(UserStatus.ENABLE);
        when(metadataProvider.getUser("test"))
            .thenReturn(CompletableFuture.completedFuture(persisted));

        handler.handle(newContext(), chain).join();
        verify(chain, never()).handle(any());
    }

    private static DefaultAuthorizationContext newContext() {
        return DefaultAuthorizationContext.of(User.of("test"),
            Resource.ofTopic("topic"), Action.PUB, "192.168.0.1");
    }
}
