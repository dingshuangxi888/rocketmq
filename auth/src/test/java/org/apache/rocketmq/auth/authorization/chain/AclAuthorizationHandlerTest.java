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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.rocketmq.auth.authorization.enums.Decision;
import org.apache.rocketmq.auth.authorization.model.PolicyEntry;
import org.apache.rocketmq.auth.authorization.model.Resource;
import org.apache.rocketmq.auth.config.AuthConfig;
import org.apache.rocketmq.common.action.Action;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class AclAuthorizationHandlerTest {

    private AclAuthorizationHandler handler;

    @Before
    public void setUp() {
        this.handler = new AclAuthorizationHandler(new AuthConfig());
    }

    @Test
    public void compareDenyWinsAtSamePrecision() {
        PolicyEntry allow = policyEntry("Topic:test", Decision.ALLOW);
        PolicyEntry deny = policyEntry("Topic:test", Decision.DENY);

        Assert.assertTrue(handler.comparePolicyEntries(deny, allow) < 0);
        Assert.assertTrue(handler.comparePolicyEntries(allow, deny) > 0);

        List<PolicyEntry> entries = new ArrayList<>(Arrays.asList(allow, deny));
        entries.sort(handler::comparePolicyEntries);
        Assert.assertSame(deny, entries.get(0));

        entries = new ArrayList<>(Arrays.asList(deny, allow));
        entries.sort(handler::comparePolicyEntries);
        Assert.assertSame(deny, entries.get(0));
    }

    @Test
    public void compareKeepsComparatorContract() {
        PolicyEntry deny1 = policyEntry("Topic:test", Decision.DENY);
        PolicyEntry deny2 = policyEntry("Topic:test", Decision.DENY);
        PolicyEntry allow1 = policyEntry("Topic:test", Decision.ALLOW);
        PolicyEntry allow2 = policyEntry("Topic:test", Decision.ALLOW);

        Assert.assertEquals(0, handler.comparePolicyEntries(deny1, deny2));
        Assert.assertEquals(0, handler.comparePolicyEntries(allow1, allow2));

        List<PolicyEntry> entries = Arrays.asList(
            deny1, allow1, deny2, allow2, policyEntry("Topic:test", Decision.DENY));
        for (PolicyEntry e1 : entries) {
            for (PolicyEntry e2 : entries) {
                Assert.assertEquals(Integer.signum(handler.comparePolicyEntries(e1, e2)),
                    -Integer.signum(handler.comparePolicyEntries(e2, e1)));
            }
        }
    }

    @Test
    public void comparePrecisionBeforeDecision() {
        PolicyEntry literalAllow = policyEntry("Topic:test", Decision.ALLOW);
        PolicyEntry prefixedDeny = policyEntry("Topic:test*", Decision.DENY);
        PolicyEntry anyDeny = policyEntry("Topic:*", Decision.DENY);

        // higher precision always sorts first, regardless of decision
        Assert.assertTrue(handler.comparePolicyEntries(literalAllow, prefixedDeny) < 0);
        Assert.assertTrue(handler.comparePolicyEntries(prefixedDeny, literalAllow) > 0);
        Assert.assertTrue(handler.comparePolicyEntries(prefixedDeny, anyDeny) < 0);

        List<PolicyEntry> entries = new ArrayList<>(
            Arrays.asList(anyDeny, prefixedDeny, literalAllow));
        entries.sort(handler::comparePolicyEntries);
        Assert.assertSame(literalAllow, entries.get(0));
    }

    private static PolicyEntry policyEntry(String resourceKey, Decision decision) {
        return PolicyEntry.of(Resource.of(resourceKey),
            Arrays.asList(Action.PUB, Action.SUB), null, decision);
    }
}
