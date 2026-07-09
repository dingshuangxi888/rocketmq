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

package org.apache.rocketmq.proxy.grpc.interceptor;

import io.grpc.Attributes;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.rocketmq.common.constant.GrpcConstants;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HeaderInterceptorTest {

    private static final InetSocketAddress REAL_REMOTE = new InetSocketAddress("10.0.0.7", 5000);
    private static final InetSocketAddress REAL_LOCAL = new InetSocketAddress("10.0.0.1", 8081);

    private Metadata intercept(Metadata inboundHeaders) {
        HeaderInterceptor interceptor = new HeaderInterceptor();

        final Attributes attributes = Attributes.newBuilder()
            .set(Grpc.TRANSPORT_ATTR_REMOTE_ADDR, REAL_REMOTE)
            .set(Grpc.TRANSPORT_ATTR_LOCAL_ADDR, REAL_LOCAL)
            .build();

        ServerCall<Object, Object> call = new ServerCall<Object, Object>() {
            @Override
            public Attributes getAttributes() {
                return attributes;
            }

            @Override
            public void request(int numMessages) {
            }

            @Override
            public void sendHeaders(Metadata headers) {
            }

            @Override
            public void sendMessage(Object message) {
            }

            @Override
            public void close(io.grpc.Status status, Metadata trailers) {
            }

            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public MethodDescriptor<Object, Object> getMethodDescriptor() {
                return null;
            }
        };

        final AtomicReference<Metadata> forwarded = new AtomicReference<>();
        ServerCallHandler<Object, Object> next = (c, headers) -> {
            forwarded.set(headers);
            return null;
        };

        interceptor.interceptCall(call, inboundHeaders, next);
        return forwarded.get();
    }

    @Test
    public void shouldUseRealRemoteAddressWhenClientDoesNotSpoof() {
        Metadata forwarded = intercept(new Metadata());

        assertThat(forwarded.get(GrpcConstants.REMOTE_ADDRESS)).isEqualTo("10.0.0.7:5000");
        assertThat(forwarded.get(GrpcConstants.LOCAL_ADDRESS)).isEqualTo("10.0.0.1:8081");
    }

    /**
     * Regression test for the ACL source-IP bypass: a client that pre-seeds the
     * {@code rpc-remote-address} header must NOT be able to override the real socket address used
     * for ACL IP-restriction decisions.
     */
    @Test
    public void shouldDiscardClientSuppliedRemoteAddress() {
        Metadata headers = new Metadata();
        headers.put(GrpcConstants.REMOTE_ADDRESS, "127.0.0.1:9999");
        headers.put(GrpcConstants.LOCAL_ADDRESS, "127.0.0.1:1");

        Metadata forwarded = intercept(headers);

        assertThat(forwarded.getAll(GrpcConstants.REMOTE_ADDRESS)).containsExactly("10.0.0.7:5000");
        assertThat(forwarded.getAll(GrpcConstants.LOCAL_ADDRESS)).containsExactly("10.0.0.1:8081");
    }
}
