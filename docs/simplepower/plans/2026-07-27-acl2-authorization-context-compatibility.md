# ACL2 Authorization Context Compatibility Implementation Plan

## Goal

Complete the remaining authorization-context mappings, reject unrecognized empty contexts, preserve supported historical client behavior through a narrow built-in compatibility matcher, and verify the result across Broker, Proxy, client, message, and admin lanes without changing the wire protocol.

## Design Summary

The change is one cohesive behavior package:

- `DefaultAuthorizationContextBuilder` resolves all 7 identified message/client requests and all 20 remaining Broker admin requests from existing headers or bodies.
- Resource-bearing requests always produce concrete Topic, consumer Group, or Cluster contexts; blank or malformed resource-bearing requests are rejected.
- `producerGroup` and `ProducerData.groupName` are never converted to ACL Group resources.
- `AuthorizationEvaluator` rejects an empty context unless the original request matches an immutable, exact-shape compatibility rule.
- The built-in compatibility matcher is not configurable and does not reuse `authorizationWhitelist`.
- Broker Remoting, Proxy Remoting, and Proxy gRPC pipelines pass the original request to the evaluator.
- No client protocol, request header, processor contract, or AuthConfig field changes.
- The RocketMQ branch remains one commit relative to `origin/develop`; the existing local commit is amended only after implementation and verification. Existing untracked files, this plan, and `work-skills` changes are not staged.
- Code comments, test names, and the retained commit message use neutral authorization-context and historical-compatibility wording.

The production code, unit tests, remote compatibility evidence, and reusable skill probes depend on the same request/action contract and must stay under one coordinator. There is no independent write package whose delegation benefit outweighs the coordination risk.

### Formal request mappings

The seven non-admin mappings are:

| Request | Context |
| --- | --- |
| `LITE_PULL_MESSAGE` | Topic `SUB` plus consumer Group `SUB` |
| `BATCH_ACK_MESSAGE` | Deduplicated Topic `SUB` plus consumer Group `SUB` for every ack |
| `SEND_REPLY_MESSAGE` | Topic `PUB` |
| `SEND_REPLY_MESSAGE_V2` | Topic `PUB` |
| `QUERY_ASSIGNMENT` | Topic `SUB` plus consumer Group `SUB` |
| `SET_MESSAGE_REQUEST_MODE` | Topic `SUB` plus consumer Group `UPDATE` |
| `CHECK_CLIENT_CONFIG` | `subscriptionData.topic` `SUB` plus consumer Group `SUB` |

The 20 admin mappings are:

- Resource-specific:
  - `UPDATE_AND_CREATE_TOPIC_LIST`: every Topic `CREATE`; retry Topic names use the existing consumer Group normalization.
  - `UPDATE_COLD_DATA_FLOW_CTR_CONFIG`: every Properties key as consumer Group `UPDATE`.
  - `REMOVE_COLD_DATA_FLOW_CTR_CONFIG`: body consumer Group `UPDATE`.
  - `UPDATE_AND_CREATE_SUBSCRIPTIONGROUP_LIST`: every consumer Group `CREATE`.
  - `UPDATE_AND_CREATE_STATIC_TOPIC`: Topic `CREATE`.
- Topic `LIST` with `ResourcePattern.ANY`:
  - `GET_ALL_TOPIC_CONFIG`
  - `GET_TIMER_METRICS`
  - `GET_SYSTEM_TOPIC_LIST_FROM_BROKER`
- Consumer Group `LIST` with `ResourcePattern.ANY`:
  - `GET_COLD_DATA_FLOW_CTR_INFO`
  - `GET_ALL_SUBSCRIPTIONGROUP_CONFIG`
- Topic and consumer Group `LIST`, both with `ResourcePattern.ANY`:
  - `GET_ALL_MESSAGE_REQUEST_MODE`
- Cluster `GET`:
  - `GET_TIMER_CHECK_POINT`
  - `GET_ALL_DELAY_OFFSET`
  - `GET_BROKER_HA_STATUS`
  - `GET_BROKER_EPOCH_CACHE`
- Cluster `UPDATE`:
  - `SET_COMMITLOG_READ_MODE`
  - `CLEAN_EXPIRED_CONSUMEQUEUE`
  - `DELETE_EXPIRED_COMMITLOG`
  - `CLEAN_UNUSED_TOPIC`
  - `POP_ROLLBACK`

Batch resource lists are deduplicated while preserving first-seen order. An empty list, null entry, blank resource name, invalid body, or missing required body is rejected rather than falling back to Cluster.

### Built-in empty-context compatibility rules

The matcher is evaluated only after authentication and only when the builder returned no contexts:

- Remoting producer-only `HEART_BEAT`: no consumer entries, at least one valid producer entry.
- Remoting producer-only `UNREGISTER_CLIENT`: nonblank `producerGroup`, blank `consumerGroup`.
- Remoting historical `END_TRANSACTION`: blank Topic with all legacy required transaction fields present.
- Remoting historical `VIEW_MESSAGE_BY_ID`: blank Topic with a valid physical offset.
- gRPC producer `HeartbeatRequest`: blank consumer Group and `PRODUCER`, plus the historical `CLIENT_TYPE_UNSPECIFIED` producer shape.
- gRPC producer `NotifyClientTerminationRequest`: blank consumer Group.
- gRPC `TelemetryCommand`: only `THREAD_STACK_TRACE` or `VERIFY_MESSAGE_RESULT` with a nonblank nonce.

Any near-match, malformed shape, empty consumer request, unknown request, or new empty context is rejected. A gRPC heartbeat with a nonblank Group is treated as a consumer heartbeat only for a consumer client type or the historical unspecified type; a producer heartbeat with a Group is rejected, so producer identity text never becomes a consumer Group resource.

## Implementation Route

**Main agent**

The Auth builder, compatibility matcher, evaluator, three pipelines, and their tests form one behavior contract. The ECS run and `rocketmq-dev-env` updates are sequential consumers of the final artifact and evidence. Direct main-agent implementation preserves the approved action/resource semantics and the single-commit boundary.

The implementation is behavior-shaping and high risk, so it remains with the current-session main agent rather than a delegated worker. The mandatory quick verifier uses the resolved FAST default `gpt-5.3-codex-spark` / `xhigh`.

## Exact Files

### Planning artifact, never staged

- `/Users/shuangxi.dsx/WorkCode/rocketmq-dsx/docs/simplepower/plans/2026-07-27-acl2-authorization-context-compatibility.md`

### RocketMQ production files

- `/Users/shuangxi.dsx/WorkCode/rocketmq-dsx/auth/src/main/java/org/apache/rocketmq/auth/authorization/AuthorizationCompatibility.java` — create
- `/Users/shuangxi.dsx/WorkCode/rocketmq-dsx/auth/src/main/java/org/apache/rocketmq/auth/authorization/AuthorizationEvaluator.java`
- `/Users/shuangxi.dsx/WorkCode/rocketmq-dsx/auth/src/main/java/org/apache/rocketmq/auth/authorization/builder/DefaultAuthorizationContextBuilder.java`
- `/Users/shuangxi.dsx/WorkCode/rocketmq-dsx/broker/src/main/java/org/apache/rocketmq/broker/auth/pipeline/AuthorizationPipeline.java`
- `/Users/shuangxi.dsx/WorkCode/rocketmq-dsx/proxy/src/main/java/org/apache/rocketmq/proxy/grpc/pipeline/AuthorizationPipeline.java`
- `/Users/shuangxi.dsx/WorkCode/rocketmq-dsx/proxy/src/main/java/org/apache/rocketmq/proxy/remoting/pipeline/AuthorizationPipeline.java`

### RocketMQ test files

- `/Users/shuangxi.dsx/WorkCode/rocketmq-dsx/auth/src/test/java/org/apache/rocketmq/auth/authorization/AuthorizationEvaluatorTest.java`
- `/Users/shuangxi.dsx/WorkCode/rocketmq-dsx/auth/src/test/java/org/apache/rocketmq/auth/authorization/builder/DefaultAuthorizationContextBuilderTest.java`
- `/Users/shuangxi.dsx/WorkCode/rocketmq-dsx/auth/src/test/java/org/apache/rocketmq/auth/authorization/model/ResourceTest.java`

### Canonical `rocketmq-dev-env` files, kept outside the RocketMQ commit

- `/Users/shuangxi.dsx/WorkCode/work-skills/rocketmq-dev-env/SKILL.md`
- `/Users/shuangxi.dsx/WorkCode/work-skills/rocketmq-dev-env/references/verification.md`
- `/Users/shuangxi.dsx/WorkCode/work-skills/rocketmq-dev-env/references/acl2-compatibility-matrix.md`
- `/Users/shuangxi.dsx/WorkCode/work-skills/rocketmq-dev-env/scripts/BrokerRequestProbe.java`
- `/Users/shuangxi.dsx/WorkCode/work-skills/rocketmq-dev-env/scripts/ProxyRemotingRequestProbe.java`
- `/Users/shuangxi.dsx/WorkCode/work-skills/rocketmq-dev-env/scripts/RemotingMessageMatrix.java`
- `/Users/shuangxi.dsx/WorkCode/work-skills/rocketmq-dev-env/scripts/GrpcMessageMatrix.java`
- `/Users/shuangxi.dsx/WorkCode/work-skills/rocketmq-dev-env/scripts/run-acl2-context-matrix.py` — create

### Remote generated state, disposable and never committed

- `/root/rocketmq-dev-env`
- `/root/rocketmq-acl2-validation/runtime-before`
- `/root/rocketmq-acl2-validation/runtime-after`
- `/root/rocketmq-acl2-validation/store-before`
- `/root/rocketmq-acl2-validation/store-after`
- `/root/rocketmq-acl2-validation/client-lanes`
- `/root/rocketmq-acl2-validation/results`
- `/root/rocketmq-acl2-validation/rollback`

No other source, test, configuration, documentation, or script file is authorized for modification. A required change outside this list stops execution and requires fresh user approval.

## Implementation Steps

1. **Record and protect the starting state.**
   - Confirm `HEAD=137bea9fd`, branch `hotfix/auth-context-update`, and one commit relative to `origin/develop`.
   - Record the exact current untracked-file list and do not clean, move, stage, or modify those files.
   - Confirm the plan file is untracked and will remain outside the RocketMQ commit.
   - Follow the canonical Simple Power configuration and scratch-ref rules referenced by `simplepower:subagent-driven-development`; no worker or quick-verifier commit is permitted.

2. **Add failing builder tests for the seven non-admin mappings.**
   - Construct the real historical/current header or body for each request code.
   - Assert exact resource keys and actions, deterministic deduplication for batch ack, retry-topic normalization, and rejection for missing body/resource fields.
   - Assert `SET_MESSAGE_REQUEST_MODE` yields Topic `SUB` and consumer Group `UPDATE`.
   - Assert no context resource is derived from producerGroup text.

3. **Add failing builder tests for all 20 admin mappings.**
   - Cover each request code once with a valid request.
   - Cover old null-header shapes for `GET_ALL_TOPIC_CONFIG` and `GET_ALL_SUBSCRIPTIONGROUP_CONFIG`.
   - Cover duplicate and invalid batch Topic/Group entries.
   - Cover Properties parsing for cold-data Group updates and raw-body parsing for removal.
   - Assert the six list operations use the exact typed `ANY` resource and `LIST` action.
   - Assert the remaining Broker-level operations use the exact approved Cluster action.

4. **Add failing evaluator and compatibility tests.**
   - Assert `evaluate(List)` rejects null or empty contexts.
   - Assert request-aware evaluation accepts only the approved Remoting and gRPC compatibility shapes.
   - Assert typed Topic/Group `ANY + LIST` contexts require matching resource type and action; Cluster,
     literal-resource, and `GET` grants do not satisfy them.
   - Add a near-match rejection for every whitelist family: empty producer set, nonblank consumer Group, incomplete transaction fields, missing view offset, producer heartbeat carrying a Group, telemetry command without the approved case/nonce, and an unknown request code.
   - Assert nonempty contexts still execute the ordinary authorization strategy.
   - Assert the builder produces a consumer Group context for historical gRPC unspecified-type heartbeats with a nonblank Group.

5. **Implement `AuthorizationCompatibility`.**
   - Provide overloads for `RemotingCommand` and `GeneratedMessageV3`.
   - Decode only the approved request families and return `false` for decode errors, null bodies, incomplete fields, unknown cases, and near-matches.
   - Keep the matcher immutable, internal, and independent of `AuthConfig`.
   - Do not add security-sensitive explanatory comments; method and test names describe compatibility behavior only.

6. **Make `AuthorizationEvaluator` request-aware and closed on unrecognized empty contexts.**
   - Add Remoting and gRPC overloads that receive the original request.
   - When contexts are nonempty, evaluate every context through the existing strategy.
   - When contexts are empty, return only if `AuthorizationCompatibility` matches; otherwise throw `AuthorizationException` with a neutral empty-context message.
   - Make the existing list-only entry reject empty contexts so new callers cannot silently omit request information.

7. **Pass the original request through all three authorization pipelines.**
   - Broker Remoting calls the Remoting evaluator overload.
   - Proxy Remoting calls the Remoting evaluator overload.
   - Proxy gRPC calls the protobuf evaluator overload.
   - Preserve the existing authentication-before-authorization order and exception translation.

8. **Implement the 27 formal mappings in `DefaultAuthorizationContextBuilder`.**
   - Add explicit cases and imports for body-only requests.
   - Reuse the current send, pull, retry-topic, source-IP, subject, channel-ID, and RPC-code conventions.
   - Parse resource-bearing bodies even when the request has no functional custom header; credential metadata remains separate from resource extraction.
   - Add small private helpers for required body decoding, Topic/Group construction, and first-seen deduplication; do not refactor unrelated existing cases.
   - Update gRPC heartbeat handling according to the approved consumer/producer/unspecified rules.
   - Throw `AuthorizationException` for invalid resource-bearing shapes.

9. **Run focused local implementation tests.**
   - Run the two modified Auth test classes.
   - Compile `auth`, `broker`, and `proxy` together so all evaluator call sites are checked.
   - Run the existing `HeaderInterceptorTest` to retain the connection-context behavior already in the branch.
   - Fix only failures attributable to the approved files and behavior. Record and report unrelated failures
     without changing their gate.

10. **Extend `rocketmq-dev-env` for executable context validation.**
    - Add `CTX-D01` through `CTX-D07` for the seven non-admin requests.
    - Add `CTX-A01` through `CTX-A20` in the exact admin order recorded in the Design Summary.
    - Add lifecycle rows for Remoting producer heartbeat/unregister, historical EndTransaction/ViewMessage, gRPC producer heartbeat/termination, and the two approved Telemetry result cases.
    - Add negative rows for malformed shapes, consumer requests without grants, and an unknown empty-context request.
    - Generalize `BrokerRequestProbe` to construct the approved Broker request scenarios and assert semantic response codes.
    - Generalize `ProxyRemotingRequestProbe` for the Proxy-exposed affected Remoting paths.
    - Extend `RemotingMessageMatrix` with reply, Pull, LitePull assign, POP, ack/batch-ack, change-invisible, retry/DLQ, and transaction-check scenarios while retaining isolated version classpaths.
    - Extend `GrpcMessageMatrix` with PushConsumer, FIFO, delay, transaction, reconnect, producer/consumer lifecycle, and metadata-normalization scenarios.
    - Create `run-acl2-context-matrix.py` with `--self-test`, immutable lane labels, isolated working directories, sanitized environment allowlisting, semantic response-code parsing, per-scenario JSON records, and a final PASS/FAIL/BLOCKED/NOT_RUN summary.
    - Update `SKILL.md` and `verification.md` to route ACL context work through the new runner. Do not store credentials, ECS identifiers, signed URLs, raw full logs, or environment dumps.

11. **Create reproducible before/after server artifacts.**
    - Preserve `137bea9fd` as the immutable `S-BEFORE` source reference.
    - After local tests pass, stage only the nine authorized RocketMQ production/test files and amend the
      existing local commit with its neutral message, keeping the branch one commit ahead of `origin/develop`.
    - Create detached temporary worktrees for `137bea9fd` and the amended `S-AFTER` SHA.
    - Build each with:

      ```bash
      mvn -Prelease-all -DskipTests -Dspotbugs.skip=true clean install -U
      ```

    - Resolve only archives newer than each build marker, verify required binaries/config contents, and record SHA-256 values.
    - If later in-scope fixes change `S-AFTER`, amend the same branch commit, rebuild, and discard the superseded candidate artifact; never accumulate extra branch commits.

12. **Perform the existing-ECS reuse approval gate.**
    - Use the installed Alibaba Cloud CLI only for read-only inventory of the existing
      `rocketmq-dev-env-20260724` instance.
    - Confirm its current instance ID, region/zone, state, image, instance type, disk, VPC/vSwitch,
      security group rules, public reachability, and expected cleanup ownership.
    - Present the exact host mutations: upload paths and hashes, runtime/store/result directories, listeners,
      processes to stop/start, ACL fixture files, rollback paths, and disposable data cleanup.
    - Do not upload, extract, change configuration, create metadata, send traffic, or restart a component until
      the user explicitly approves that reuse plan.
    - If the existing host is unsuitable, stop and present a complete new-ECS tuple for separate approval;
      do not call `RunInstances` as part of the reuse path.

13. **Stage the disposable runtime after exact ECS reuse approval.**
    - Follow `/Users/shuangxi.dsx/WorkCode/work-skills/rocketmq-dev-env/references/setup-playbook.md`.
    - Prove JDK 17, disk capacity, runtime identity, and restricted connectivity.
    - Upload each archive to a partial path, verify SHA-256 remotely, extract into its exact runtime directory, and overlay only reviewed development and protected ACL configuration.
    - Use isolated store roots and explicit `JAVA_OPT_EXT` sized for the approved host.
    - Start NameServer, Broker, then Proxy; require process identity, listeners, route registration, stored-and-consumed evidence, dispatch health, and stable PIDs.

14. **Run the compatibility and authorization matrix.**
    - Server lanes: `S-BEFORE=137bea9fd`; `S-AFTER` is read from the deployed `runtime-after` build manifest and must equal the current amended branch HEAD before the runner starts.
    - Modes: ACL2 off control, ACL2 enabled, and rollback to `S-BEFORE`.
    - Remoting client lanes: 4.9.8, 5.0.0, 5.1.4, 5.2.0, 5.3.4, 5.5.0.
    - gRPC Java lanes: 5.0.0, 5.0.3, 5.0.4, 5.0.8, 5.1.0.
    - Cross-language lifecycle lanes: official Go 5.1.3 and C++ 5.0.4.
    - mqadmin lanes: 4.9.8, 5.0.0, 5.3.4, 5.5.0; unavailable commands are executed through the version-isolated probe and marked with their actual client/probe coordinate.
    - Identities: Super, exact Topic/Group grant, `Topic:* LIST`, `Group:* LIST`, producer-only,
      consumer-only, Cluster GET, Cluster UPDATE, explicit deny, disabled, matching-source IP restriction,
      and nonmatching-source IP restriction.
    - Execute every `CTX-*` row plus normal sync/async/oneway, batch, FIFO, delay/timer, transaction commit/rollback/check, request/reply, retry/DLQ, concurrent/orderly Push, Pull, LitePull subscribe/assign, POP, ack/batch-ack, change invisibility, SimpleConsumer, reconnect, and metadata-normalization rows.
    - Capture semantic response code, message ID/offset, exact consumed body/tag, ack result, Broker/Proxy runtime identity, artifact SHA, sanitized log window, and verdict.
    - Restore reversible admin settings immediately after each authorized mutation. Destructive fixture cleanup remains a separately confirmed action.

15. **Apply evidence-based skill refinements and validate the canonical skill.**
    - Keep only reusable commands, lane contracts, result schema, and failure classifications observed during the run.
    - Remove no approved scenario and do not encode live ECS IDs or one-off results.
    - Run `run-acl2-context-matrix.py --self-test`, Python compilation, Java probe compilation against the selected isolated lanes, shell syntax checks, and the canonical `quick_validate.py`.
    - Leave all `work-skills` edits uncommitted for a separate user decision.

16. **Run the mandatory FAST quick verifier.**
    - Give the verifier only the approved changed-file list, behavior contract, implementation summary, and Quick Verification commands.
    - Permit only typo-level corrections. Any behavior, structure, public interface, test expectation, or scope issue returns to the main agent.
    - Follow the canonical scratch workflow at `simplepower:subagent-driven-development/scratch-ref-workflow.md`.

17. **Perform main-agent final review and delivery verification.**
    - Inspect the complete RocketMQ diff against this plan, including imports, comments, action semantics, whitelist shapes, malformed-request behavior, and producerGroup non-resource assertions.
    - Inspect the canonical skill diff separately and verify it does not overlap unrelated dirty `work-skills` files.
    - Rerun all Final Verification commands and require the remote matrix acceptance criteria.
    - Amend the same RocketMQ commit only if final in-scope corrections were necessary, rebuild/redeploy the amended `S-AFTER`, and rerun affected remote rows plus the final smoke set.
    - Verify the RocketMQ branch is exactly one commit ahead of `origin/develop`, the remote SHA state is reported accurately, existing untracked files remain present, the plan is not staged, and no `work-skills` file is in the RocketMQ commit.

## Risks

- **Historical producer identity has no ACL resource.** The matcher checks exact lifecycle shapes; producerGroup is never mapped as Group.
- **Legacy EndTransaction/ViewMessage omit Topic.** Only complete historical wire shapes are accepted; current resource-bearing requests use Topic contexts. This compatibility exception remains explicit in results.
- **A request-code-only exception would be too broad.** The implementation matches request code, message case,
  and required fields after authentication, and rejects near-matches.
- **Batch partial authorization could mutate some resources.** Every resource context is built and evaluated before the processor executes; malformed entries reject the whole request.
- **Action drift could break admin behavior.** The tests assert the approved Topic/Group/Cluster action for all 27 mappings.
- **Super users could be rejected before their handler.** Every known protected operation receives a formal context; only exact resource-free compatibility shapes remain empty.
- **Older clients may construct different headers.** The matrix uses isolated 4.9.8 through 5.5.0 artifacts and preserves null-header cases.
- **Proxy-only listener checks can hide message-path failures.** Acceptance requires real Remoting and gRPC send/receive/ack evidence.
- **Remote Java or Broker dispatch incompatibility can mimic authorization failure.** JDK 17, runtime identity, stored-and-consumed evidence, and dispatch health are separate gates.
- **ECS creation is billable and cleanup is destructive.** Exact purchase approval and separate cleanup confirmation are mandatory.
- **The `work-skills` repository is already dirty.** Only the listed `rocketmq-dev-env` subtree files may change, and they remain uncommitted.
- **Amending changes the local commit SHA.** `137bea9fd` remains the recorded before reference; every after artifact records the current amended SHA and SHA-256.

## Quick Verification

FAST verifier budget: 25 minutes locally, excluding remote ECS execution.

Run from `/Users/shuangxi.dsx/WorkCode/rocketmq-dsx`:

```bash
/usr/bin/time -p git diff --check
/usr/bin/time -p mvn -pl auth -am \
  -Dtest=DefaultAuthorizationContextBuilderTest,AuthorizationEvaluatorTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dspotbugs.skip=true -Djacoco.skip=true test
/usr/bin/time -p mvn -pl auth,broker,proxy -am \
  -DskipTests -Dspotbugs.skip=true -Djacoco.skip=true package
```

Run for the canonical skill:

```bash
/usr/bin/time -p python3 -m py_compile \
  /Users/shuangxi.dsx/WorkCode/work-skills/rocketmq-dev-env/scripts/run-acl2-context-matrix.py
/usr/bin/time -p python3 \
  /Users/shuangxi.dsx/WorkCode/work-skills/rocketmq-dev-env/scripts/run-acl2-context-matrix.py \
  --self-test
/usr/bin/time -p bash -n \
  /Users/shuangxi.dsx/WorkCode/work-skills/rocketmq-dev-env/scripts/preflight.sh \
  /Users/shuangxi.dsx/WorkCode/work-skills/rocketmq-dev-env/scripts/render-config.sh \
  /Users/shuangxi.dsx/WorkCode/work-skills/rocketmq-dev-env/scripts/verify-runtime.sh
/usr/bin/time -p python3 \
  /Users/shuangxi.dsx/.codex/skills/.system/skill-creator/scripts/quick_validate.py \
  /Users/shuangxi.dsx/WorkCode/work-skills/rocketmq-dev-env
```

Expected results:

- no whitespace errors;
- both Auth test classes pass;
- `auth`, `broker`, and `proxy` compile and package together;
- runner self-test reports every expected `CTX-*` ID exactly once and no secret-bearing field;
- skill Python/shell syntax and canonical validation pass;
- no files outside Exact Files change.

## Final Verification

Local budget: 45 minutes. Remote matrix budget: 8 hours after ECS readiness.

Run from `/Users/shuangxi.dsx/WorkCode/rocketmq-dsx`:

```bash
/usr/bin/time -p mvn -pl auth -am \
  -Dtest=DefaultAuthorizationContextBuilderTest,AuthorizationEvaluatorTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dspotbugs.skip=true -Djacoco.skip=true test
/usr/bin/time -p mvn -pl proxy -am \
  -Dtest=HeaderInterceptorTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dspotbugs.skip=true -Djacoco.skip=true test
/usr/bin/time -p mvn -pl auth,broker,proxy -am \
  -DskipTests -Dspotbugs.skip=true -Djacoco.skip=true clean package
/usr/bin/time -p git diff --check
git status --short
git rev-list --left-right --count origin/develop...HEAD
git diff --name-only origin/develop...HEAD
```

Run on the approved ECS after uploading the canonical skill runner:

```bash
export VALIDATION_ROOT=/root/rocketmq-acl2-validation
bash /root/rocketmq-dev-env/scripts/verify-runtime.sh \
  --home "$VALIDATION_ROOT/runtime-after" \
  --namesrv 127.0.0.1:9876 \
  --topic ACL2_CTX_NORMAL \
  --expect-proxy
python3 /root/rocketmq-dev-env/scripts/run-acl2-context-matrix.py \
  --server-before "$VALIDATION_ROOT/runtime-before" \
  --server-after "$VALIDATION_ROOT/runtime-after" \
  --results "$VALIDATION_ROOT/results/run-<unique-id>" \
  --manifest "$VALIDATION_ROOT/execution-manifest.json" \
  --remoting-versions 4.9.8,5.1.4,5.2.0,5.3.3,5.4.0 \
  --candidate-remoting-version "$CANDIDATE_REMOTING_VERSION" \
  --grpc-java-versions 5.0.4,5.0.7 \
  --context-probe-version 5.4.0
```

The runner command is supplied credentials only through `ROCKETMQ_TEST_ACCESS_KEY` and `ROCKETMQ_TEST_SECRET_KEY` in its sanitized process environment; values are never printed.

Expected remote results:

- all selected legitimate historical client operations succeed on `S-AFTER`;
- exact-grant and Super rows succeed;
- nonmatching, explicit-deny, malformed, and unknown-empty-context rows reject with the expected semantic code;
- all 7 data/client mappings and 20 admin mappings show both allowed and rejected evidence;
- producer lifecycle rows pass without any producerGroup-derived ACL resource;
- Remoting and gRPC message matrices prove stored-and-consumed or receive-and-ack behavior for every selected message/client type;
- IP-restricted direct and conflicting-metadata rows use the server-observed connection context;
- rollback restores `S-BEFORE` process, route, and bounded message evidence;
- no credential or full signature appears in results/log extracts;
- the final runner verdict is `PASS`; `BLOCKED`, `NOT_RUN`, missing denied-path evidence, missing message evidence, or missing rollback evidence makes the overall result `PARTIAL` and prevents completion.

The main agent then reviews the full diff against the approved plan, makes only in-scope corrections, reruns the affected quick/final commands, and verifies the single-commit and preserved-worktree boundaries before declaring completion.

## Checkpoint Conditions

1. **Accepted plan checkpoint:** created only after the user gives combined approval for this saved plan, `Implementation Route: Main agent`, no grouped-worker allocation, and immediate current-session execution. Execution then invokes `simplepower:subagent-driven-development` with this exact plan path.
2. **Final reviewed/verified implementation checkpoint:** created only after main-agent implementation, mandatory FAST quick verification, main-agent full diff review and in-scope fixes, complete local verification, approved ECS matrix PASS, rollback proof, skill validation, single RocketMQ commit verification, and preserved unrelated worktree evidence.
