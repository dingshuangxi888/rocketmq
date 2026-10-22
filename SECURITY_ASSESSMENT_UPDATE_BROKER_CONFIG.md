# 安全报告核实与定级评估备忘

> 内部文档 — 仅供 PMC / ASF 安全流程使用，请勿公开或加入公开 issue tracker。

## 0. 一句话结论

报告描述的**鉴权（授权）缺失问题真实存在**，但报告给出的 **9.1 Critical 定级不成立**。真实威胁模型对应 **CWE-862，CVSS 约 8.8（High）甚至更低**，建议作为**常规安全加固（hardening）在后续版本修复**，而非按紧急 Critical CVE 处理。

---

## 1. 已核实成立的事实（不可反驳）

在已开启认证与授权的前提下，一个**已通过认证的普通（低权限）用户**仍可执行 `UPDATE_BROKER_CONFIG`(RequestCode=25) 等管理操作。逐环核实如下：

| 环节 | 文件 / 位置 | 事实 |
| --- | --- | --- |
| 授权 switch 未覆盖 admin code | `auth/.../builder/DefaultAuthorizationContextBuilder.java` `build(ChannelHandlerContext, RemotingCommand)` | 仅覆盖 16 个客户端操作，code=25 落入 `default` 分支 |
| 注解兜底返回空 | 同上 `buildContextByAnnotation()` | `getRequestHeader(25)` 返回 null → 返回空 context 列表 |
| 无 header 类可扫描 | 全局检索 `@RocketMQAction(... UPDATE_BROKER_CONFIG ...)` | 0 个结果，code=25 无对应带注解的 RequestHeader |
| 空列表直接放行 | `auth/.../AuthorizationEvaluator.java` L39-41 | `CollectionUtils.isEmpty(contexts)` 时直接 `return`，不做任何鉴权 |
| 处理器自身无鉴权 | `broker/.../processor/AdminBrokerProcessor.java` `updateBrokerConfig()` | 仅做配置黑名单校验（见下），无任何角色/权限校验 |

> 黑名单实际构成（`AdminBrokerProcessor.initConfigBlackList()`）：硬编码 `brokerConfigPath`、`rocketmqHome`、`configBlackList` 三项，再加上 `BrokerConfig.configBlackList` 字段拆分项（默认值 `"configBlackList;brokerConfigPath"`，与硬编码项重叠）。即默认实际仅拦截 `brokerConfigPath`、`rocketmqHome`、`configBlackList` 三个配置键。`namesrvAddr`、`storePathRootDir`、`storePathCommitLog` 等敏感项均不在内，可被任意改写。

**结论**：这是标准的 **CWE-862: Missing Authorization（认证后越权）**，问题真实，不否认。

---

## 2. 认证（Authentication）是有效的 —— 据此排除"匿名/未认证"叙事

报告将一部分严重性建立在"无需凭据即可执行"上。但代码显示**认证与授权取数逻辑相互独立**，认证不依赖 `CommandCustomHeader`：

- `auth/.../authentication/builder/DefaultAuthenticationContextBuilder.java` `build(ChannelHandlerContext, RemotingCommand)`：
  - 从 `request.getExtFields()` 取 `AccessKey` / `Signature`（由 `AclClientRPCHook` 注入），与请求是否有 header 无关；
  - 第 103-105 行：未携带 `ACCESS_KEY` 时返回 username 为空的 context，后续 `authenticationProvider.authenticate()` 会因 username 为空而**认证失败被拦截**。

**结论**：开启认证后，`UPDATE_BROKER_CONFIG` 这类"无 header、裸 body"请求**仍会经过有效的 AccessKey + 签名校验**。即认证做了，仅鉴权被跳过。这使威胁模型被精确锁定为"**已认证的低权限用户越权**"，而非"匿名攻击"。

---

## 3. 驳回 9.1 Critical 定级的依据

报告给出两个评分：auth 开启 8.8、auth 关闭 9.1（Critical）。其中 **9.1 不应采纳**：

### 3.1 默认无 auth 是设计预期，不计入漏洞评分
- `auth/.../config/AuthConfig.java`：`authenticationEnabled = false`、`authorizationEnabled = false`，**默认均关闭**。
- auth 关闭时，所有管理操作本就对所有连接开放，这是 RocketMQ 一贯的安全模型（默认裸跑，生产环境须自行开启 ACL + 网络隔离）。把"未配置安全 = 漏洞"计入评分属于错误归因。9.1 的前提（auth 关闭时匿名可执行）因此不成立。

### 3.2 报告核心论据的代码引用与事实不符
- 报告大段引用 `client/.../acl/common/Permission.java` 的 `ADMIN_CODE` 集合（声称代码库已认定 code=25 为 admin 权限）。
- **核实结果**：当前代码库 `Permission.java` 仅 45 行，只含 `DENY/ANY/PUB/SUB` 常量与 `parsePermFromString` 方法，**不存在 `ADMIN_CODE` 集合**。
- 报告所述版本为 5.5.0，而本代码库为 **5.3.3**（见各 `pom.xml`）。
- 据此可合理要求报告者在**真实目标版本**上补充可复现 PoC，其引用的关键论据存在版本错配，PoC 可信度需重新验证。

### 3.3 真实威胁模型的可达性折扣
- 触发前提为：(a) 管理端口（10911 等）对攻击者网络可达；(b) 攻击者已被发放合法凭据（即已是系统内某低权限用户）。
- 官方部署建议一贯要求管理端口做网络层隔离、不暴露公网。叠加上述前提，实际可利用性低于"远程无前提"，CVSS 的 PR/AC 可相应调整，综合定级应 **≤ 8.8（High）**。

---

## 4. 建议处理方式（部分采纳 + 重新定级）

1. **受理**第 1 节所述的"授权开启时未覆盖 admin RequestCode"问题（真实、扎实）。
2. **驳回** 9.1 Critical 定级；按"认证开启、已认证低权限用户越权"路径评估，定级 ≤ 8.8（High）。
3. **要求报告者**在 5.3.3 / 对应真实版本上补充可复现 PoC，并指出其 `ADMIN_CODE` 引用与当前版本不符。
4. 作为**常规安全加固**纳入后续版本修复计划，而非紧急 Critical CVE 流程。
5. 同步在部署文档中强调：管理端口必须网络隔离；生产环境须开启认证与授权。

---

## 5. 修复方向（供后续加固参考，非本备忘必需）

- **授权层（根治）**：在 `DefaultAuthorizationContextBuilder.build()` 的 switch 中为 admin RequestCode 补充 cluster 级 `Action.UPDATE` 授权上下文，消除空列表 fail-open。
- **纵深防御**：将 `namesrvAddr`、`storePathRootDir`、`storePathCommitLog` 等涉及路径/网络地址的敏感项加入 `configBlackList`。
- **可观测性**：为管理类操作增加审计日志。

---

## 附：合规提醒

- 本问题已被 ASF 安全团队登记，按 ASF 流程，报告者可在约定期限后公开披露。
- 不建议出具"完全否认漏洞"的结论——第 1 节问题客观成立，否认将带来声誉与合规风险。
- 推荐路径为"部分采纳 + 重新定级 + 常规加固"，既合规又可显著简化处理强度。
