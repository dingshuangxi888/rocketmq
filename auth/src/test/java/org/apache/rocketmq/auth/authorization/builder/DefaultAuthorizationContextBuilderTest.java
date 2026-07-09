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
package org.apache.rocketmq.auth.authorization.builder;

import apache.rocketmq.v2.AckMessageRequest;
import apache.rocketmq.v2.ChangeInvisibleDurationRequest;
import apache.rocketmq.v2.ClientType;
import apache.rocketmq.v2.EndTransactionRequest;
import apache.rocketmq.v2.ForwardMessageToDeadLetterQueueRequest;
import apache.rocketmq.v2.HeartbeatRequest;
import apache.rocketmq.v2.Message;
import apache.rocketmq.v2.MessageQueue;
import apache.rocketmq.v2.NotifyClientTerminationRequest;
import apache.rocketmq.v2.Publishing;
import apache.rocketmq.v2.QueryAssignmentRequest;
import apache.rocketmq.v2.QueryRouteRequest;
import apache.rocketmq.v2.RecallMessageRequest;
import apache.rocketmq.v2.ReceiveMessageRequest;
import apache.rocketmq.v2.Resource;
import apache.rocketmq.v2.SendMessageRequest;
import apache.rocketmq.v2.Settings;
import apache.rocketmq.v2.Subscription;
import apache.rocketmq.v2.SubscriptionEntry;
import apache.rocketmq.v2.TelemetryCommand;
import com.alibaba.fastjson2.JSON;
import com.google.common.collect.Sets;
import com.google.protobuf.GeneratedMessageV3;
import io.grpc.Metadata;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.rocketmq.auth.authorization.context.DefaultAuthorizationContext;
import org.apache.rocketmq.auth.config.AuthConfig;
import org.apache.rocketmq.common.TopicFilterType;
import org.apache.rocketmq.common.action.Action;
import org.apache.rocketmq.common.constant.GrpcConstants;
import org.apache.rocketmq.common.resource.ResourceType;
import org.apache.rocketmq.remoting.netty.AttributeKeys;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.apache.rocketmq.remoting.protocol.RequestCode;
import org.apache.rocketmq.remoting.protocol.RequestHeaderRegistry;
import org.apache.rocketmq.remoting.protocol.body.BatchAck;
import org.apache.rocketmq.remoting.protocol.body.BatchAckMessageRequestBody;
import org.apache.rocketmq.remoting.protocol.body.CheckClientRequestBody;
import org.apache.rocketmq.remoting.protocol.body.QueryAssignmentRequestBody;
import org.apache.rocketmq.remoting.protocol.body.SetMessageRequestModeRequestBody;
import org.apache.rocketmq.remoting.protocol.header.ConsumerSendMsgBackRequestHeader;
import org.apache.rocketmq.remoting.protocol.header.CreateTopicRequestHeader;
import org.apache.rocketmq.remoting.protocol.header.CreateUserRequestHeader;
import org.apache.rocketmq.remoting.protocol.header.EndTransactionRequestHeader;
import org.apache.rocketmq.remoting.protocol.header.GetConsumerListByGroupRequestHeader;
import org.apache.rocketmq.remoting.protocol.header.HeartbeatRequestHeader;
import org.apache.rocketmq.remoting.protocol.header.PopMessageRequestHeader;
import org.apache.rocketmq.remoting.protocol.header.PullMessageRequestHeader;
import org.apache.rocketmq.remoting.protocol.header.QueryConsumerOffsetRequestHeader;
import org.apache.rocketmq.remoting.protocol.header.QueryMessageRequestHeader;
import org.apache.rocketmq.remoting.protocol.header.RecallMessageRequestHeader;
import org.apache.rocketmq.remoting.protocol.header.SendMessageRequestHeader;
import org.apache.rocketmq.remoting.protocol.header.SendMessageRequestHeaderV2;
import org.apache.rocketmq.remoting.protocol.header.UnregisterClientRequestHeader;
import org.apache.rocketmq.remoting.protocol.header.UpdateConsumerOffsetRequestHeader;
import org.apache.rocketmq.remoting.protocol.heartbeat.ConsumerData;
import org.apache.rocketmq.remoting.protocol.heartbeat.HeartbeatData;
import org.apache.rocketmq.remoting.protocol.heartbeat.SubscriptionData;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class DefaultAuthorizationContextBuilderTest {

    private AuthorizationContextBuilder builder;

    @Mock
    private ChannelHandlerContext channelHandlerContext;

    @Mock
    private Channel channel;

    @Before
    public void setUp() throws Exception {
        AuthConfig authConfig = new AuthConfig();
        authConfig.setClusterName("DefaultCluster");
        builder = new DefaultAuthorizationContextBuilder(authConfig);
        RequestHeaderRegistry.getInstance().initialize();
    }

    @Test
    public void buildGrpc() {
        Metadata metadata = new Metadata();
        metadata.put(GrpcConstants.AUTHORIZATION_AK, "rocketmq");
        metadata.put(GrpcConstants.REMOTE_ADDRESS, "192.168.0.1");
        metadata.put(GrpcConstants.CHANNEL_ID, "channel-id");

        GeneratedMessageV3 request = SendMessageRequest.newBuilder()
            .addMessages(Message.newBuilder()
                .setTopic(Resource.newBuilder().setName("topic").build())
                .build())
            .build();
        List<DefaultAuthorizationContext> result = builder.build(metadata, request);
        Assert.assertEquals(1, result.size());
        Assert.assertEquals(result.get(0).getSubject().getSubjectKey(), "User:rocketmq");
        Assert.assertEquals(result.get(0).getResource().getResourceKey(), "Topic:topic");
        Assert.assertTrue(result.get(0).getActions().containsAll(Arrays.asList(Action.PUB)));
        Assert.assertEquals(result.get(0).getSourceIp(), "192.168.0.1");
        Assert.assertEquals(result.get(0).getChannelId(), "channel-id");
        Assert.assertEquals(result.get(0).getRpcCode(), SendMessageRequest.getDescriptor().getFullName());

        request = RecallMessageRequest.newBuilder()
            .setTopic(Resource.newBuilder().setName("topic").build())
            .setRecallHandle("handle")
            .build();
        result = builder.build(metadata, request);
        Assert.assertEquals(1, result.size());
        Assert.assertEquals(result.get(0).getSubject().getSubjectKey(), "User:rocketmq");
        Assert.assertEquals(result.get(0).getResource().getResourceKey(), "Topic:topic");
        Assert.assertTrue(result.get(0).getActions().containsAll(Arrays.asList(Action.PUB)));
        Assert.assertEquals(result.get(0).getSourceIp(), "192.168.0.1");
        Assert.assertEquals(result.get(0).getChannelId(), "channel-id");
        Assert.assertEquals(result.get(0).getRpcCode(), RecallMessageRequest.getDescriptor().getFullName());

        request = EndTransactionRequest.newBuilder()
            .setTopic(Resource.newBuilder().setName("topic").build())
            .build();
        result = builder.build(metadata, request);
        Assert.assertEquals(1, result.size());
        Assert.assertEquals(result.get(0).getSubject().getSubjectKey(), "User:rocketmq");
        Assert.assertEquals(result.get(0).getResource().getResourceKey(), "Topic:topic");
        Assert.assertTrue(result.get(0).getActions().containsAll(Arrays.asList(Action.PUB)));

        request = HeartbeatRequest.newBuilder()
            .setClientType(ClientType.PUSH_CONSUMER)
            .setGroup(Resource.newBuilder().setName("group").build())
            .build();
        result = builder.build(metadata, request);
        Assert.assertEquals(1, result.size());
        Assert.assertEquals(result.get(0).getSubject().getSubjectKey(), "User:rocketmq");
        Assert.assertEquals(result.get(0).getResource().getResourceKey(), "Group:group");
        Assert.assertTrue(result.get(0).getActions().containsAll(Arrays.asList(Action.SUB)));

        request = ReceiveMessageRequest.newBuilder()
            .setMessageQueue(MessageQueue.newBuilder()
                .setTopic(Resource.newBuilder().setName("topic").build())
                .build())
            .setGroup(Resource.newBuilder().setName("group").build())
            .build();
        result = builder.build(metadata, request);
        Assert.assertEquals(2, result.size());
        Assert.assertEquals(getContext(result, ResourceType.GROUP).getSubject().getSubjectKey(), "User:rocketmq");
        Assert.assertEquals(getContext(result, ResourceType.GROUP).getResource().getResourceKey(), "Group:group");
        Assert.assertTrue(getContext(result, ResourceType.GROUP).getActions().containsAll(Arrays.asList(Action.SUB)));
        Assert.assertEquals(getContext(result, ResourceType.TOPIC).getSubject().getSubjectKey(), "User:rocketmq");
        Assert.assertEquals(getContext(result, ResourceType.TOPIC).getResource().getResourceKey(), "Topic:topic");
        Assert.assertTrue(getContext(result, ResourceType.TOPIC).getActions().containsAll(Arrays.asList(Action.SUB)));

        request = AckMessageRequest.newBuilder()
            .setTopic(Resource.newBuilder().setName("topic").build())
            .setGroup(Resource.newBuilder().setName("group").build())
            .build();
        result = builder.build(metadata, request);
        Assert.assertEquals(2, result.size());
        Assert.assertEquals(getContext(result, ResourceType.GROUP).getSubject().getSubjectKey(), "User:rocketmq");
        Assert.assertEquals(getContext(result, ResourceType.GROUP).getResource().getResourceKey(), "Group:group");
        Assert.assertTrue(getContext(result, ResourceType.GROUP).getActions().containsAll(Arrays.asList(Action.SUB)));
        Assert.assertEquals(getContext(result, ResourceType.TOPIC).getSubject().getSubjectKey(), "User:rocketmq");
        Assert.assertEquals(getContext(result, ResourceType.TOPIC).getResource().getResourceKey(), "Topic:topic");
        Assert.assertTrue(getContext(result, ResourceType.TOPIC).getActions().containsAll(Arrays.asList(Action.SUB)));

        request = ForwardMessageToDeadLetterQueueRequest.newBuilder()
            .setTopic(Resource.newBuilder().setName("topic").build())
            .setGroup(Resource.newBuilder().setName("group").build())
            .build();
        result = builder.build(metadata, request);
        Assert.assertEquals(2, result.size());
        Assert.assertEquals(getContext(result, ResourceType.GROUP).getSubject().getSubjectKey(), "User:rocketmq");
        Assert.assertEquals(getContext(result, ResourceType.GROUP).getResource().getResourceKey(), "Group:group");
        Assert.assertTrue(getContext(result, ResourceType.GROUP).getActions().containsAll(Arrays.asList(Action.SUB)));
        Assert.assertEquals(getContext(result, ResourceType.TOPIC).getSubject().getSubjectKey(), "User:rocketmq");
        Assert.assertEquals(getContext(result, ResourceType.TOPIC).getResource().getResourceKey(), "Topic:topic");
        Assert.assertTrue(getContext(result, ResourceType.TOPIC).getActions().containsAll(Arrays.asList(Action.SUB)));

        request = NotifyClientTerminationRequest.newBuilder()
            .setGroup(Resource.newBuilder().setName("group").build())
            .build();
        result = builder.build(metadata, request);
        Assert.assertEquals(1, result.size());
        Assert.assertEquals(result.get(0).getSubject().getSubjectKey(), "User:rocketmq");
        Assert.assertEquals(result.get(0).getResource().getResourceKey(), "Group:group");
        Assert.assertTrue(result.get(0).getActions().containsAll(Arrays.asList(Action.SUB)));

        request = ChangeInvisibleDurationRequest.newBuilder()
            .setGroup(Resource.newBuilder().setName("group").build())
            .build();
        result = builder.build(metadata, request);
        Assert.assertEquals(1, result.size());
        Assert.assertEquals(result.get(0).getSubject().getSubjectKey(), "User:rocketmq");
        Assert.assertEquals(result.get(0).getResource().getResourceKey(), "Group:group");
        Assert.assertTrue(result.get(0).getActions().containsAll(Arrays.asList(Action.SUB)));

        request = QueryRouteRequest.newBuilder()
            .setTopic(Resource.newBuilder().setName("topic").build())
            .build();
        result = builder.build(metadata, request);
        Assert.assertEquals(1, result.size());
        Assert.assertEquals(result.get(0).getSubject().getSubjectKey(), "User:rocketmq");
        Assert.assertEquals(result.get(0).getResource().getResourceKey(), "Topic:topic");
        Assert.assertTrue(result.get(0).getActions().containsAll(Arrays.asList(Action.PUB, Action.SUB)));

        request = QueryAssignmentRequest.newBuilder()
            .setTopic(Resource.newBuilder().setName("topic").build())
            .setGroup(Resource.newBuilder().setName("group").build())
            .build();
        result = builder.build(metadata, request);
        Assert.assertEquals(2, result.size());
        Assert.assertEquals(getContext(result, ResourceType.GROUP).getSubject().getSubjectKey(), "User:rocketmq");
        Assert.assertEquals(getContext(result, ResourceType.GROUP).getResource().getResourceKey(), "Group:group");
        Assert.assertTrue(getContext(result, ResourceType.GROUP).getActions().containsAll(Arrays.asList(Action.SUB)));
        Assert.assertEquals(getContext(result, ResourceType.TOPIC).getSubject().getSubjectKey(), "User:rocketmq");
        Assert.assertEquals(getContext(result, ResourceType.TOPIC).getResource().getResourceKey(), "Topic:topic");
        Assert.assertTrue(getContext(result, ResourceType.TOPIC).getActions().containsAll(Arrays.asList(Action.SUB)));

        request = TelemetryCommand.newBuilder()
            .setSettings(Settings.newBuilder()
                .setPublishing(Publishing.newBuilder()
                    .addTopics(Resource.newBuilder().setName("topic").build())
                    .build())
                .build())
            .build();
        result = builder.build(metadata, request);
        Assert.assertEquals(1, result.size());
        Assert.assertEquals(getContext(result, ResourceType.TOPIC).getSubject().getSubjectKey(), "User:rocketmq");
        Assert.assertEquals(getContext(result, ResourceType.TOPIC).getResource().getResourceKey(), "Topic:topic");
        Assert.assertTrue(getContext(result, ResourceType.TOPIC).getActions().containsAll(Arrays.asList(Action.PUB)));

        request = TelemetryCommand.newBuilder()
            .setSettings(Settings.newBuilder()
                .setSubscription(Subscription.newBuilder()
                    .setGroup(Resource.newBuilder().setName("group").build())
                    .addSubscriptions(SubscriptionEntry.newBuilder()
                        .setTopic(Resource.newBuilder().setName("topic").build())
                        .build())
                    .build())
                .build())
            .build();
        result = builder.build(metadata, request);
        Assert.assertEquals(2, result.size());
        Assert.assertEquals(getContext(result, ResourceType.GROUP).getSubject().getSubjectKey(), "User:rocketmq");
        Assert.assertEquals(getContext(result, ResourceType.GROUP).getResource().getResourceKey(), "Group:group");
        Assert.assertTrue(getContext(result, ResourceType.GROUP).getActions().containsAll(Arrays.asList(Action.SUB)));
        Assert.assertEquals(getContext(result, ResourceType.TOPIC).getSubject().getSubjectKey(), "User:rocketmq");
        Assert.assertEquals(getContext(result, ResourceType.TOPIC).getResource().getResourceKey(), "Topic:topic");
        Assert.assertTrue(getContext(result, ResourceType.TOPIC).getActions().containsAll(Arrays.asList(Action.SUB)));
    }

    @Test
    public void buildRemoting() {
        when(channel.id()).thenReturn(mockChannelId("channel-id"));
        when(channel.hasAttr(eq(AttributeKeys.PROXY_PROTOCOL_ADDR))).thenReturn(true);
        when(channel.attr(eq(AttributeKeys.PROXY_PROTOCOL_ADDR))).thenReturn(mockAttribute("192.168.0.1"));
        when(channel.hasAttr(eq(AttributeKeys.PROXY_PROTOCOL_PORT))).thenReturn(true);
        when(channel.attr(eq(AttributeKeys.PROXY_PROTOCOL_PORT))).thenReturn(mockAttribute("1234"));
        when(channelHandlerContext.channel()).thenReturn(channel);

        SendMessageRequestHeader sendMessageRequestHeader = new SendMessageRequestHeader();
        sendMessageRequestHeader.setTopic("topic");
        RemotingCommand request = RemotingCommand.createRequestCommand(RequestCode.SEND_MESSAGE, sendMessageRequestHeader);
        request.setVersion(441);
        request.addExtField("AccessKey", "rocketmq");
        request.makeCustomHeaderToNet();
        List<DefaultAuthorizationContext> result = builder.build(channelHandlerContext, request);
        Assert.assertEquals(1, result.size());
        Assert.assertEquals("User:rocketmq", result.get(0).getSubject().getSubjectKey());
        Assert.assertEquals("Topic:topic", result.get(0).getResource().getResourceKey());
        Assert.assertTrue(result.get(0).getActions().containsAll(Arrays.asList(Action.PUB)));
        Assert.assertEquals("192.168.0.1", result.get(0).getSourceIp());
        Assert.assertEquals("channel-id", result.get(0).getChannelId());
        Assert.assertEquals(RequestCode.SEND_MESSAGE + "", result.get(0).getRpcCode());

        sendMessageRequestHeader = new SendMessageRequestHeader();
        sendMessageRequestHeader.setTopic("%RETRY%group");
        request = RemotingCommand.createRequestCommand(RequestCode.SEND_MESSAGE, sendMessageRequestHeader);
        request.setVersion(441);
        request.addExtField("AccessKey", "rocketmq");
        request.makeCustomHeaderToNet();
        result = builder.build(channelHandlerContext, request);
        Assert.assertEquals(1, result.size());
        Assert.assertEquals("User:rocketmq", result.get(0).getSubject().getSubjectKey());
        Assert.assertEquals("Group:group", result.get(0).getResource().getResourceKey());
        Assert.assertTrue(result.get(0).getActions().containsAll(Arrays.asList(Action.SUB)));

        SendMessageRequestHeaderV2 sendMessageRequestHeaderV2 = new SendMessageRequestHeaderV2();
        sendMessageRequestHeaderV2.setTopic("topic");
        request = RemotingCommand.createRequestCommand(RequestCode.SEND_MESSAGE_V2, sendMessageRequestHeaderV2);
        request.setVersion(441);
        request.addExtField("AccessKey", "rocketmq");
        request.makeCustomHeaderToNet();
        result = builder.build(channelHandlerContext, request);
        Assert.assertEquals(1, result.size());
        Assert.assertEquals("User:rocketmq", result.get(0).getSubject().getSubjectKey());
        Assert.assertEquals("Topic:topic", result.get(0).getResource().getResourceKey());
        Assert.assertTrue(result.get(0).getActions().containsAll(Arrays.asList(Action.PUB)));

        sendMessageRequestHeaderV2 = new SendMessageRequestHeaderV2();
        sendMessageRequestHeaderV2.setTopic("%RETRY%group");
        request = RemotingCommand.createRequestCommand(RequestCode.SEND_MESSAGE_V2, sendMessageRequestHeaderV2);
        request.setVersion(441);
        request.addExtField("AccessKey", "rocketmq");
        request.makeCustomHeaderToNet();
        result = builder.build(channelHandlerContext, request);
        Assert.assertEquals(1, result.size());
        Assert.assertEquals("User:rocketmq", result.get(0).getSubject().getSubjectKey());
        Assert.assertEquals("Group:group", result.get(0).getResource().getResourceKey());
        Assert.assertTrue(result.get(0).getActions().containsAll(Arrays.asList(Action.SUB)));

        RecallMessageRequestHeader recallMessageRequestHeader = new RecallMessageRequestHeader();
        recallMessageRequestHeader.setTopic("topic");
        recallMessageRequestHeader.setRecallHandle("handle");
        request = RemotingCommand.createRequestCommand(RequestCode.RECALL_MESSAGE, recallMessageRequestHeader);
        request.setVersion(441);
        request.addExtField("AccessKey", "rocketmq");
        request.makeCustomHeaderToNet();
        result = builder.build(channelHandlerContext, request);
        Assert.assertEquals(1, result.size());
        Assert.assertEquals("User:rocketmq", result.get(0).getSubject().getSubjectKey());
        Assert.assertEquals("Topic:topic", result.get(0).getResource().getResourceKey());
        Assert.assertTrue(result.get(0).getActions().containsAll(Arrays.asList(Action.PUB)));
        Assert.assertEquals("192.168.0.1", result.get(0).getSourceIp());
        Assert.assertEquals("channel-id", result.get(0).getChannelId());
        Assert.assertEquals(RequestCode.RECALL_MESSAGE + "", result.get(0).getRpcCode());

        EndTransactionRequestHeader endTransactionRequestHeader = new EndTransactionRequestHeader();
        endTransactionRequestHeader.setTopic("topic");
        request = RemotingCommand.createRequestCommand(RequestCode.END_TRANSACTION, endTransactionRequestHeader);
        request.setVersion(441);
        request.addExtField("AccessKey", "rocketmq");
        request.makeCustomHeaderToNet();
        result = builder.build(channelHandlerContext, request);
        Assert.assertEquals(1, result.size());
        Assert.assertEquals("User:rocketmq", result.get(0).getSubject().getSubjectKey());
        Assert.assertEquals("Topic:topic", result.get(0).getResource().getResourceKey());
        Assert.assertTrue(result.get(0).getActions().containsAll(Arrays.asList(Action.PUB)));

        endTransactionRequestHeader = new EndTransactionRequestHeader();
        request = RemotingCommand.createRequestCommand(RequestCode.END_TRANSACTION, endTransactionRequestHeader);
        request.setVersion(441);
        request.addExtField("AccessKey", "rocketmq");
        request.makeCustomHeaderToNet();
        result = builder.build(channelHandlerContext, request);
        Assert.assertEquals(0, result.size());

        ConsumerSendMsgBackRequestHeader consumerSendMsgBackRequestHeader = new ConsumerSendMsgBackRequestHeader();
        consumerSendMsgBackRequestHeader.setGroup("group");
        request = RemotingCommand.createRequestCommand(RequestCode.CONSUMER_SEND_MSG_BACK, consumerSendMsgBackRequestHeader);
        request.setVersion(441);
        request.addExtField("AccessKey", "rocketmq");
        request.makeCustomHeaderToNet();
        result = builder.build(channelHandlerContext, request);
        Assert.assertEquals(1, result.size());
        Assert.assertEquals("User:rocketmq", result.get(0).getSubject().getSubjectKey());
        Assert.assertEquals("Group:group", result.get(0).getResource().getResourceKey());
        Assert.assertTrue(result.get(0).getActions().containsAll(Arrays.asList(Action.SUB)));

        PullMessageRequestHeader pullMessageRequestHeader = new PullMessageRequestHeader();
        pullMessageRequestHeader.setTopic("topic");
        pullMessageRequestHeader.setConsumerGroup("group");
        request = RemotingCommand.createRequestCommand(RequestCode.PULL_MESSAGE, pullMessageRequestHeader);
        request.setVersion(441);
        request.addExtField("AccessKey", "rocketmq");
        request.makeCustomHeaderToNet();
        result = builder.build(channelHandlerContext, request);
        Assert.assertEquals(2, result.size());
        Assert.assertEquals("User:rocketmq", getContext(result, ResourceType.GROUP).getSubject().getSubjectKey());
        Assert.assertEquals("Group:group", getContext(result, ResourceType.GROUP).getResource().getResourceKey());
        Assert.assertTrue(getContext(result, ResourceType.GROUP).getActions().containsAll(Arrays.asList(Action.SUB)));
        Assert.assertEquals("User:rocketmq", getContext(result, ResourceType.TOPIC).getSubject().getSubjectKey());
        Assert.assertEquals("Topic:topic", getContext(result, ResourceType.TOPIC).getResource().getResourceKey());
        Assert.assertTrue(getContext(result, ResourceType.TOPIC).getActions().containsAll(Arrays.asList(Action.SUB)));

        QueryMessageRequestHeader queryMessageRequestHeader = new QueryMessageRequestHeader();
        queryMessageRequestHeader.setTopic("topic");
        request = RemotingCommand.createRequestCommand(RequestCode.QUERY_MESSAGE, queryMessageRequestHeader);
        request.setVersion(441);
        request.addExtField("AccessKey", "rocketmq");
        request.makeCustomHeaderToNet();
        result = builder.build(channelHandlerContext, request);
        Assert.assertEquals(1, result.size());
        Assert.assertEquals("User:rocketmq", result.get(0).getSubject().getSubjectKey());
        Assert.assertEquals("Topic:topic", result.get(0).getResource().getResourceKey());
        Assert.assertTrue(result.get(0).getActions().containsAll(Arrays.asList(Action.SUB, Action.GET)));

        HeartbeatRequestHeader heartbeatRequestHeader = new HeartbeatRequestHeader();
        request = RemotingCommand.createRequestCommand(RequestCode.HEART_BEAT, heartbeatRequestHeader);
        HeartbeatData heartbeatData = new HeartbeatData();
        ConsumerData consumerData = new ConsumerData();
        consumerData.setGroupName("group");
        SubscriptionData subscriptionData = new SubscriptionData();
        subscriptionData.setTopic("topic");
        consumerData.setSubscriptionDataSet(Sets.newHashSet(subscriptionData));
        heartbeatData.setConsumerDataSet(Sets.newHashSet(consumerData));
        request.setBody(JSON.toJSONBytes(heartbeatData));
        request.setVersion(441);
        request.addExtField("AccessKey", "rocketmq");
        request.makeCustomHeaderToNet();
        result = builder.build(channelHandlerContext, request);
        Assert.assertEquals(2, result.size());
        Assert.assertEquals("User:rocketmq", getContext(result, ResourceType.GROUP).getSubject().getSubjectKey());
        Assert.assertEquals("Group:group", getContext(result, ResourceType.GROUP).getResource().getResourceKey());
        Assert.assertTrue(getContext(result, ResourceType.GROUP).getActions().containsAll(Arrays.asList(Action.SUB)));
        Assert.assertEquals("User:rocketmq", getContext(result, ResourceType.TOPIC).getSubject().getSubjectKey());
        Assert.assertEquals("Topic:topic", getContext(result, ResourceType.TOPIC).getResource().getResourceKey());
        Assert.assertTrue(getContext(result, ResourceType.TOPIC).getActions().containsAll(Arrays.asList(Action.SUB)));

        UnregisterClientRequestHeader unregisterClientRequestHeader = new UnregisterClientRequestHeader();
        unregisterClientRequestHeader.setConsumerGroup("group");
        request = RemotingCommand.createRequestCommand(RequestCode.UNREGISTER_CLIENT, unregisterClientRequestHeader);
        request.setVersion(441);
        request.addExtField("AccessKey", "rocketmq");
        request.makeCustomHeaderToNet();
        result = builder.build(channelHandlerContext, request);
        Assert.assertEquals(1, result.size());
        Assert.assertEquals("User:rocketmq", result.get(0).getSubject().getSubjectKey());
        Assert.assertEquals("Group:group", result.get(0).getResource().getResourceKey());
        Assert.assertTrue(result.get(0).getActions().containsAll(Arrays.asList(Action.SUB)));

        GetConsumerListByGroupRequestHeader getConsumerListByGroupRequestHeader = new GetConsumerListByGroupRequestHeader();
        getConsumerListByGroupRequestHeader.setConsumerGroup("group");
        request = RemotingCommand.createRequestCommand(RequestCode.GET_CONSUMER_LIST_BY_GROUP, getConsumerListByGroupRequestHeader);
        request.setVersion(441);
        request.addExtField("AccessKey", "rocketmq");
        request.makeCustomHeaderToNet();
        result = builder.build(channelHandlerContext, request);
        Assert.assertEquals(1, result.size());
        Assert.assertEquals("User:rocketmq", result.get(0).getSubject().getSubjectKey());
        Assert.assertEquals("Group:group", result.get(0).getResource().getResourceKey());
        Assert.assertTrue(result.get(0).getActions().containsAll(Arrays.asList(Action.SUB, Action.GET)));

        QueryConsumerOffsetRequestHeader queryConsumerOffsetRequestHeader = new QueryConsumerOffsetRequestHeader();
        queryConsumerOffsetRequestHeader.setTopic("topic");
        queryConsumerOffsetRequestHeader.setConsumerGroup("group");
        request = RemotingCommand.createRequestCommand(RequestCode.QUERY_CONSUMER_OFFSET, queryConsumerOffsetRequestHeader);
        request.setVersion(441);
        request.addExtField("AccessKey", "rocketmq");
        request.makeCustomHeaderToNet();
        result = builder.build(channelHandlerContext, request);
        Assert.assertEquals(2, result.size());
        Assert.assertEquals("User:rocketmq", getContext(result, ResourceType.GROUP).getSubject().getSubjectKey());
        Assert.assertEquals("Group:group", getContext(result, ResourceType.GROUP).getResource().getResourceKey());
        Assert.assertTrue(getContext(result, ResourceType.GROUP).getActions().containsAll(Arrays.asList(Action.SUB)));
        Assert.assertEquals("User:rocketmq", getContext(result, ResourceType.TOPIC).getSubject().getSubjectKey());
        Assert.assertEquals("Topic:topic", getContext(result, ResourceType.TOPIC).getResource().getResourceKey());
        Assert.assertTrue(getContext(result, ResourceType.TOPIC).getActions().containsAll(Arrays.asList(Action.SUB)));

        UpdateConsumerOffsetRequestHeader updateConsumerOffsetRequestHeader = new UpdateConsumerOffsetRequestHeader();
        updateConsumerOffsetRequestHeader.setTopic("topic");
        updateConsumerOffsetRequestHeader.setConsumerGroup("group");
        request = RemotingCommand.createRequestCommand(RequestCode.UPDATE_CONSUMER_OFFSET, updateConsumerOffsetRequestHeader);
        request.setVersion(441);
        request.addExtField("AccessKey", "rocketmq");
        request.makeCustomHeaderToNet();
        result = builder.build(channelHandlerContext, request);
        Assert.assertEquals(2, result.size());
        Assert.assertEquals("User:rocketmq", getContext(result, ResourceType.GROUP).getSubject().getSubjectKey());
        Assert.assertEquals("Group:group", getContext(result, ResourceType.GROUP).getResource().getResourceKey());
        Assert.assertTrue(getContext(result, ResourceType.GROUP).getActions().containsAll(Arrays.asList(Action.SUB, Action.UPDATE)));
        Assert.assertEquals("User:rocketmq", getContext(result, ResourceType.TOPIC).getSubject().getSubjectKey());
        Assert.assertEquals("Topic:topic", getContext(result, ResourceType.TOPIC).getResource().getResourceKey());
        Assert.assertTrue(getContext(result, ResourceType.TOPIC).getActions().containsAll(Arrays.asList(Action.SUB, Action.UPDATE)));

        CreateTopicRequestHeader createTopicRequestHeader = new CreateTopicRequestHeader();
        createTopicRequestHeader.setTopic("topic");
        createTopicRequestHeader.setTopicFilterType(TopicFilterType.SINGLE_TAG.name());
        request = RemotingCommand.createRequestCommand(RequestCode.UPDATE_AND_CREATE_TOPIC, createTopicRequestHeader);
        request.setVersion(441);
        request.addExtField("AccessKey", "rocketmq");
        request.makeCustomHeaderToNet();
        result = builder.build(channelHandlerContext, request);
        Assert.assertEquals(1, result.size());
        Assert.assertEquals("User:rocketmq", result.get(0).getSubject().getSubjectKey());
        Assert.assertEquals("Topic:topic", result.get(0).getResource().getResourceKey());
        Assert.assertTrue(result.get(0).getActions().containsAll(Arrays.asList(Action.CREATE)));

        CreateUserRequestHeader createUserRequestHeader = new CreateUserRequestHeader();
        createUserRequestHeader.setUsername("abc");
        request = RemotingCommand.createRequestCommand(RequestCode.AUTH_CREATE_USER, createUserRequestHeader);
        request.setVersion(441);
        request.addExtField("AccessKey", "rocketmq");
        request.makeCustomHeaderToNet();
        result = builder.build(channelHandlerContext, request);
        Assert.assertEquals(1, result.size());
        Assert.assertEquals("User:rocketmq", result.get(0).getSubject().getSubjectKey());
        Assert.assertEquals("Cluster:DefaultCluster", result.get(0).getResource().getResourceKey());
        Assert.assertTrue(result.get(0).getActions().containsAll(Arrays.asList(Action.UPDATE)));

        request = RemotingCommand.createRequestCommand(RequestCode.UPDATE_BROKER_CONFIG, null);
        request.setVersion(441);
        request.addExtField("AccessKey", "rocketmq");
        request.makeCustomHeaderToNet();
        result = builder.build(channelHandlerContext, request);
        Assert.assertEquals(1, result.size());
        Assert.assertEquals("User:rocketmq", result.get(0).getSubject().getSubjectKey());
        Assert.assertEquals("Cluster:DefaultCluster", result.get(0).getResource().getResourceKey());
        Assert.assertTrue(result.get(0).getActions().containsAll(Arrays.asList(Action.UPDATE)));
        Assert.assertEquals(RequestCode.UPDATE_BROKER_CONFIG + "", result.get(0).getRpcCode());

        request = RemotingCommand.createRequestCommand(RequestCode.GET_BROKER_CONFIG, null);
        request.setVersion(441);
        request.addExtField("AccessKey", "rocketmq");
        request.makeCustomHeaderToNet();
        result = builder.build(channelHandlerContext, request);
        Assert.assertEquals(1, result.size());
        Assert.assertEquals("User:rocketmq", result.get(0).getSubject().getSubjectKey());
        Assert.assertEquals("Cluster:DefaultCluster", result.get(0).getResource().getResourceKey());
        Assert.assertTrue(result.get(0).getActions().containsAll(Arrays.asList(Action.GET)));
        Assert.assertEquals(RequestCode.GET_BROKER_CONFIG + "", result.get(0).getRpcCode());
    }

    /**
     * Guards against the proxy cluster-mode authorization bypass: request codes that fall to the
     * default switch branch (e.g. POP_MESSAGE) rely on the annotation-driven path, which only works
     * when RequestHeaderRegistry is initialized. With the registry initialized in setUp(), the
     * builder must resolve the @RocketMQAction/@RocketMQResource annotations into proper contexts
     * instead of returning an empty list that would silently bypass policy checks.
     */
    @Test
    public void buildRemotingByAnnotation() {
        when(channel.id()).thenReturn(mockChannelId("channel-id"));
        when(channel.hasAttr(eq(AttributeKeys.PROXY_PROTOCOL_ADDR))).thenReturn(true);
        when(channel.attr(eq(AttributeKeys.PROXY_PROTOCOL_ADDR))).thenReturn(mockAttribute("192.168.0.1"));
        when(channel.hasAttr(eq(AttributeKeys.PROXY_PROTOCOL_PORT))).thenReturn(true);
        when(channel.attr(eq(AttributeKeys.PROXY_PROTOCOL_PORT))).thenReturn(mockAttribute("1234"));
        when(channelHandlerContext.channel()).thenReturn(channel);

        PopMessageRequestHeader popMessageRequestHeader = new PopMessageRequestHeader();
        popMessageRequestHeader.setConsumerGroup("group");
        popMessageRequestHeader.setTopic("topic");
        popMessageRequestHeader.setQueueId(0);
        popMessageRequestHeader.setMaxMsgNums(32);
        popMessageRequestHeader.setInvisibleTime(60000L);
        popMessageRequestHeader.setPollTime(15000L);
        popMessageRequestHeader.setBornTime(System.currentTimeMillis());
        popMessageRequestHeader.setInitMode(0);
        RemotingCommand request = RemotingCommand.createRequestCommand(RequestCode.POP_MESSAGE, popMessageRequestHeader);
        request.setVersion(441);
        request.addExtField("AccessKey", "rocketmq");
        request.makeCustomHeaderToNet();
        List<DefaultAuthorizationContext> result = builder.build(channelHandlerContext, request);

        Assert.assertEquals(2, result.size());
        Assert.assertEquals("User:rocketmq", getContext(result, ResourceType.GROUP).getSubject().getSubjectKey());
        Assert.assertEquals("Group:group", getContext(result, ResourceType.GROUP).getResource().getResourceKey());
        Assert.assertTrue(getContext(result, ResourceType.GROUP).getActions().containsAll(Arrays.asList(Action.SUB)));
        Assert.assertEquals("User:rocketmq", getContext(result, ResourceType.TOPIC).getSubject().getSubjectKey());
        Assert.assertEquals("Topic:topic", getContext(result, ResourceType.TOPIC).getResource().getResourceKey());
        Assert.assertTrue(getContext(result, ResourceType.TOPIC).getActions().containsAll(Arrays.asList(Action.SUB)));
        Assert.assertEquals("192.168.0.1", getContext(result, ResourceType.TOPIC).getSourceIp());
        Assert.assertEquals(RequestCode.POP_MESSAGE + "", getContext(result, ResourceType.TOPIC).getRpcCode());
    }

    /**
     * Broker-admin read operations (the Permission.ADMIN_CODE class) must require cluster-level GET.
     * Before the fix these codes fell to the default annotation path, which has no @RocketMQAction
     * request header for them, produced an empty context, and were silently allowed for any
     * authenticated low-privilege user.
     */
    @Test
    public void buildRemotingAdminReadCodesRequireClusterGet() {
        mockRemotingChannel();
        int[] readCodes = new int[] {
            RequestCode.GET_ALL_CONSUMER_OFFSET,
            RequestCode.GET_ALL_DELAY_OFFSET,
            RequestCode.GET_ALL_MESSAGE_REQUEST_MODE,
            RequestCode.GET_BROKER_RUNTIME_INFO,
            RequestCode.GET_BROKER_EPOCH_CACHE,
            RequestCode.GET_BROKER_HA_STATUS,
            RequestCode.GET_COLD_DATA_FLOW_CTR_INFO,
            RequestCode.GET_SYSTEM_TOPIC_LIST_FROM_BROKER,
            RequestCode.GET_TIMER_CHECK_POINT,
            RequestCode.GET_TIMER_METRICS,
        };
        for (int code : readCodes) {
            RemotingCommand request = RemotingCommand.createRequestCommand(code, null);
            request.setVersion(441);
            request.addExtField("AccessKey", "rocketmq");
            request.makeCustomHeaderToNet();
            List<DefaultAuthorizationContext> result = builder.build(channelHandlerContext, request);
            Assert.assertEquals("code " + code, 1, result.size());
            Assert.assertEquals("code " + code, "User:rocketmq", result.get(0).getSubject().getSubjectKey());
            Assert.assertEquals("code " + code, "Cluster:DefaultCluster", result.get(0).getResource().getResourceKey());
            Assert.assertTrue("code " + code, result.get(0).getActions().containsAll(Arrays.asList(Action.GET)));
            Assert.assertEquals("code " + code, code + "", result.get(0).getRpcCode());
        }
    }

    /**
     * Broker-admin write/maintenance operations must require cluster-level UPDATE. Same bypass class
     * as the read codes: previously an empty context silently allowed a low-privilege user to rewrite
     * broker state (subscription groups, static topics, commitlog read mode, data cleanup, etc.).
     */
    @Test
    public void buildRemotingAdminWriteCodesRequireClusterUpdate() {
        mockRemotingChannel();
        int[] writeCodes = new int[] {
            RequestCode.UPDATE_AND_CREATE_SUBSCRIPTIONGROUP,
            RequestCode.UPDATE_AND_CREATE_SUBSCRIPTIONGROUP_LIST,
            RequestCode.UPDATE_AND_CREATE_STATIC_TOPIC,
            RequestCode.SET_COMMITLOG_READ_MODE,
            RequestCode.CLEAN_UNUSED_TOPIC,
            RequestCode.CLEAN_EXPIRED_CONSUMEQUEUE,
            RequestCode.DELETE_EXPIRED_COMMITLOG,
            RequestCode.POP_ROLLBACK,
            RequestCode.UPDATE_COLD_DATA_FLOW_CTR_CONFIG,
            RequestCode.REMOVE_COLD_DATA_FLOW_CTR_CONFIG,
        };
        for (int code : writeCodes) {
            RemotingCommand request = RemotingCommand.createRequestCommand(code, null);
            request.setVersion(441);
            request.addExtField("AccessKey", "rocketmq");
            request.makeCustomHeaderToNet();
            List<DefaultAuthorizationContext> result = builder.build(channelHandlerContext, request);
            Assert.assertEquals("code " + code, 1, result.size());
            Assert.assertEquals("code " + code, "User:rocketmq", result.get(0).getSubject().getSubjectKey());
            Assert.assertEquals("code " + code, "Cluster:DefaultCluster", result.get(0).getResource().getResourceKey());
            Assert.assertTrue("code " + code, result.get(0).getActions().containsAll(Arrays.asList(Action.UPDATE)));
            Assert.assertEquals("code " + code, code + "", result.get(0).getRpcCode());
        }
    }

    /**
     * LITE_PULL_MESSAGE is a message-consumption interface (registered to the pull processor) that
     * fell to the default branch with no @RocketMQAction request header, so it bypassed ACL even
     * after the registry was initialized. It must be authorized exactly like PULL_MESSAGE: SUB on
     * both topic and consumer group.
     */
    @Test
    public void buildRemotingLitePullMessageRequiresTopicAndGroupSub() {
        mockRemotingChannel();
        RemotingCommand request = RemotingCommand.createRequestCommand(RequestCode.LITE_PULL_MESSAGE, null);
        request.setVersion(441);
        request.addExtField("AccessKey", "rocketmq");
        request.addExtField("topic", "topic");
        request.addExtField("consumerGroup", "group");
        request.makeCustomHeaderToNet();
        List<DefaultAuthorizationContext> result = builder.build(channelHandlerContext, request);

        Assert.assertEquals(2, result.size());
        Assert.assertEquals("Group:group", getContext(result, ResourceType.GROUP).getResource().getResourceKey());
        Assert.assertTrue(getContext(result, ResourceType.GROUP).getActions().containsAll(Arrays.asList(Action.SUB)));
        Assert.assertEquals("Topic:topic", getContext(result, ResourceType.TOPIC).getResource().getResourceKey());
        Assert.assertTrue(getContext(result, ResourceType.TOPIC).getActions().containsAll(Arrays.asList(Action.SUB)));
        Assert.assertEquals(RequestCode.LITE_PULL_MESSAGE + "", getContext(result, ResourceType.TOPIC).getRpcCode());
    }

    /**
     * BATCH_ACK_MESSAGE is the batch variant of ACK_MESSAGE; it acknowledges (permanently deletes)
     * messages for a consumer group on a topic, carried in the request body rather than an annotated
     * header. It must require SUB on both topic and group, exactly like ACK_MESSAGE.
     */
    @Test
    public void buildRemotingBatchAckMessageRequiresTopicAndGroupSub() {
        mockRemotingChannel();
        BatchAck ack = new BatchAck();
        ack.setConsumerGroup("group");
        ack.setTopic("topic");
        BatchAckMessageRequestBody body = new BatchAckMessageRequestBody();
        body.setAcks(Collections.singletonList(ack));
        RemotingCommand request = RemotingCommand.createRequestCommand(RequestCode.BATCH_ACK_MESSAGE, null);
        request.setVersion(441);
        request.addExtField("AccessKey", "rocketmq");
        request.setBody(body.encode());
        request.makeCustomHeaderToNet();
        List<DefaultAuthorizationContext> result = builder.build(channelHandlerContext, request);

        Assert.assertEquals(2, result.size());
        Assert.assertEquals("Group:group", getContext(result, ResourceType.GROUP).getResource().getResourceKey());
        Assert.assertTrue(getContext(result, ResourceType.GROUP).getActions().containsAll(Arrays.asList(Action.SUB)));
        Assert.assertEquals("Topic:topic", getContext(result, ResourceType.TOPIC).getResource().getResourceKey());
        Assert.assertTrue(getContext(result, ResourceType.TOPIC).getActions().containsAll(Arrays.asList(Action.SUB)));
        Assert.assertEquals(RequestCode.BATCH_ACK_MESSAGE + "", getContext(result, ResourceType.TOPIC).getRpcCode());
    }

    /**
     * SEND_REPLY_MESSAGE produces a reply message and reuses SendMessageRequestHeader, so it must be
     * authorized exactly like SEND_MESSAGE: PUB on the topic.
     */
    @Test
    public void buildRemotingSendReplyMessageRequiresTopicPub() {
        mockRemotingChannel();
        SendMessageRequestHeader header = new SendMessageRequestHeader();
        header.setTopic("topic");
        RemotingCommand request = RemotingCommand.createRequestCommand(RequestCode.SEND_REPLY_MESSAGE, header);
        request.setVersion(441);
        request.addExtField("AccessKey", "rocketmq");
        request.makeCustomHeaderToNet();
        List<DefaultAuthorizationContext> result = builder.build(channelHandlerContext, request);

        Assert.assertEquals(1, result.size());
        Assert.assertEquals("Topic:topic", result.get(0).getResource().getResourceKey());
        Assert.assertTrue(result.get(0).getActions().containsAll(Arrays.asList(Action.PUB)));
        Assert.assertEquals(RequestCode.SEND_REPLY_MESSAGE + "", result.get(0).getRpcCode());
    }

    /**
     * SEND_REPLY_MESSAGE_V2 reuses SendMessageRequestHeaderV2, so it must be authorized exactly like
     * SEND_MESSAGE_V2: PUB on the topic.
     */
    @Test
    public void buildRemotingSendReplyMessageV2RequiresTopicPub() {
        mockRemotingChannel();
        SendMessageRequestHeaderV2 header = new SendMessageRequestHeaderV2();
        header.setTopic("topic");
        RemotingCommand request = RemotingCommand.createRequestCommand(RequestCode.SEND_REPLY_MESSAGE_V2, header);
        request.setVersion(441);
        request.addExtField("AccessKey", "rocketmq");
        request.makeCustomHeaderToNet();
        List<DefaultAuthorizationContext> result = builder.build(channelHandlerContext, request);

        Assert.assertEquals(1, result.size());
        Assert.assertEquals("Topic:topic", result.get(0).getResource().getResourceKey());
        Assert.assertTrue(result.get(0).getActions().containsAll(Arrays.asList(Action.PUB)));
        Assert.assertEquals(RequestCode.SEND_REPLY_MESSAGE_V2 + "", result.get(0).getRpcCode());
    }

    /**
     * QUERY_ASSIGNMENT returns the queue assignment for a group+topic and carries them in the body.
     * It must require SUB on both topic and group, matching the gRPC QueryAssignmentRequest path.
     */
    @Test
    public void buildRemotingQueryAssignmentRequiresTopicAndGroupSub() {
        mockRemotingChannel();
        QueryAssignmentRequestBody body = new QueryAssignmentRequestBody();
        body.setTopic("topic");
        body.setConsumerGroup("group");
        RemotingCommand request = RemotingCommand.createRequestCommand(RequestCode.QUERY_ASSIGNMENT, null);
        request.setVersion(441);
        request.addExtField("AccessKey", "rocketmq");
        request.setBody(body.encode());
        request.makeCustomHeaderToNet();
        List<DefaultAuthorizationContext> result = builder.build(channelHandlerContext, request);

        Assert.assertEquals(2, result.size());
        Assert.assertEquals("Group:group", getContext(result, ResourceType.GROUP).getResource().getResourceKey());
        Assert.assertTrue(getContext(result, ResourceType.GROUP).getActions().containsAll(Arrays.asList(Action.SUB)));
        Assert.assertEquals("Topic:topic", getContext(result, ResourceType.TOPIC).getResource().getResourceKey());
        Assert.assertTrue(getContext(result, ResourceType.TOPIC).getActions().containsAll(Arrays.asList(Action.SUB)));
        Assert.assertEquals(RequestCode.QUERY_ASSIGNMENT + "", getContext(result, ResourceType.TOPIC).getRpcCode());
    }

    /**
     * SET_MESSAGE_REQUEST_MODE changes the pull/pop consumption mode for a group+topic and carries
     * them in the body. It must require SUB on both topic and group.
     */
    @Test
    public void buildRemotingSetMessageRequestModeRequiresTopicAndGroupSub() {
        mockRemotingChannel();
        SetMessageRequestModeRequestBody body = new SetMessageRequestModeRequestBody();
        body.setTopic("topic");
        body.setConsumerGroup("group");
        RemotingCommand request = RemotingCommand.createRequestCommand(RequestCode.SET_MESSAGE_REQUEST_MODE, null);
        request.setVersion(441);
        request.addExtField("AccessKey", "rocketmq");
        request.setBody(body.encode());
        request.makeCustomHeaderToNet();
        List<DefaultAuthorizationContext> result = builder.build(channelHandlerContext, request);

        Assert.assertEquals(2, result.size());
        Assert.assertEquals("Group:group", getContext(result, ResourceType.GROUP).getResource().getResourceKey());
        Assert.assertTrue(getContext(result, ResourceType.GROUP).getActions().containsAll(Arrays.asList(Action.SUB)));
        Assert.assertEquals("Topic:topic", getContext(result, ResourceType.TOPIC).getResource().getResourceKey());
        Assert.assertTrue(getContext(result, ResourceType.TOPIC).getActions().containsAll(Arrays.asList(Action.SUB)));
        Assert.assertEquals(RequestCode.SET_MESSAGE_REQUEST_MODE + "", getContext(result, ResourceType.TOPIC).getRpcCode());
    }

    /**
     * CHECK_CLIENT_CONFIG validates a consumer's subscription (group + topic + filter) and carries
     * them in the body. It must require SUB on both topic and group.
     */
    @Test
    public void buildRemotingCheckClientConfigRequiresTopicAndGroupSub() {
        mockRemotingChannel();
        SubscriptionData subscriptionData = new SubscriptionData();
        subscriptionData.setTopic("topic");
        CheckClientRequestBody body = new CheckClientRequestBody();
        body.setGroup("group");
        body.setSubscriptionData(subscriptionData);
        RemotingCommand request = RemotingCommand.createRequestCommand(RequestCode.CHECK_CLIENT_CONFIG, null);
        request.setVersion(441);
        request.addExtField("AccessKey", "rocketmq");
        request.setBody(body.encode());
        request.makeCustomHeaderToNet();
        List<DefaultAuthorizationContext> result = builder.build(channelHandlerContext, request);

        Assert.assertEquals(2, result.size());
        Assert.assertEquals("Group:group", getContext(result, ResourceType.GROUP).getResource().getResourceKey());
        Assert.assertTrue(getContext(result, ResourceType.GROUP).getActions().containsAll(Arrays.asList(Action.SUB)));
        Assert.assertEquals("Topic:topic", getContext(result, ResourceType.TOPIC).getResource().getResourceKey());
        Assert.assertTrue(getContext(result, ResourceType.TOPIC).getActions().containsAll(Arrays.asList(Action.SUB)));
        Assert.assertEquals(RequestCode.CHECK_CLIENT_CONFIG + "", getContext(result, ResourceType.TOPIC).getRpcCode());
    }

    private DefaultAuthorizationContext getContext(List<DefaultAuthorizationContext> contexts,
        ResourceType resourceType) {
        return contexts.stream().filter(context -> context.getResource().getResourceType() == resourceType)
            .findFirst().orElse(null);
    }

    private void mockRemotingChannel() {
        when(channel.id()).thenReturn(mockChannelId("channel-id"));
        when(channel.hasAttr(eq(AttributeKeys.PROXY_PROTOCOL_ADDR))).thenReturn(true);
        when(channel.attr(eq(AttributeKeys.PROXY_PROTOCOL_ADDR))).thenReturn(mockAttribute("192.168.0.1"));
        when(channel.hasAttr(eq(AttributeKeys.PROXY_PROTOCOL_PORT))).thenReturn(true);
        when(channel.attr(eq(AttributeKeys.PROXY_PROTOCOL_PORT))).thenReturn(mockAttribute("1234"));
        when(channelHandlerContext.channel()).thenReturn(channel);
    }

    private ChannelId mockChannelId(String channelId) {
        return new ChannelId() {
            @Override
            public String asShortText() {
                return channelId;
            }

            @Override
            public String asLongText() {
                return channelId;
            }

            @Override
            public int compareTo(ChannelId o) {
                return 0;
            }
        };
    }

    private Attribute<String> mockAttribute(String value) {
        return new Attribute<String>() {
            @Override
            public AttributeKey<String> key() {
                return null;
            }

            @Override
            public String get() {
                return value;
            }

            @Override
            public void set(String value) {
            }

            @Override
            public String getAndSet(String value) {
                return null;
            }

            @Override
            public String setIfAbsent(String value) {
                return null;
            }

            @Override
            public String getAndRemove() {
                return null;
            }

            @Override
            public boolean compareAndSet(String oldValue, String newValue) {
                return false;
            }

            @Override
            public void remove() {

            }
        };
    }
}