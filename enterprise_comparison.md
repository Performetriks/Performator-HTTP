# Performator-HTTP vs Perfluencer's Gatling-Kafka-OSS — Enterprise Maturity Comparison

> **Comparison Date:** 2026-04-07

---

## At a Glance

| Dimension | Performator-HTTP | Gatling-Kafka-OSS |
|---|:---:|:---:|
| **Overall Score** | **30/100** 🔴 | **~72/100** 🟢 |
| **Protocol** | HTTP (Apache HC5) | Kafka (native Java client) |
| **Framework** | Performator (niche) | Gatling (industry standard) |
| **Language** | Java 17 | Java 17 (+ Scala bridge) |
| **License** | EPL-2.0 | Apache-2.0 |
| **Published to Maven Central** | ✅ | ✅ |
| **GitHub Stars** | 3 | 5 |
| **Commits** | Unknown (no git access) | 65 |

---

## Dimension-by-Dimension Comparison

### 1. Automated Testing

| | Performator-HTTP | Gatling-Kafka-OSS |
|---|---|---|
| **Test directory** | ❌ No `src/test` at all | ✅ `src/test/` with integration tests & example simulations |
| **Framework** | JUnit 5 declared but unused | Gatling simulations as live tests |
| **Coverage tooling** | ❌ None | Unknown (but tests exist) |
| **Score** | **0/10** | **~5/10** |

**Verdict:** Gatling-Kafka-OSS has actual test code and runnable simulations. Performator-HTTP has literally zero tests. This is the single largest gap.

---

### 2. CI/CD Pipeline

| | Performator-HTTP | Gatling-Kafka-OSS |
|---|---|---|
| **GitHub Actions** | ❌ None | ✅ `.github/` directory present |
| **Docker** | ❌ None | ✅ `docker-compose.yml` for local Kafka |
| **Build automation** | Manual GPG signing via `docs/commands.txt` | Automated workflow |
| **Score** | **0/10** | **~7/10** |

**Verdict:** Gatling-Kafka-OSS has CI workflows and a Docker Compose for its integration test environment. Performator-HTTP has no automation at all.

---

### 3. Documentation

| | Performator-HTTP | Gatling-Kafka-OSS |
|---|---|---|
| **README** | 7 lines, link to external repo | Comprehensive — full DSL examples, architecture, tuning guides |
| **QUICKSTART** | ❌ None | ✅ `QUICKSTART.md` |
| **Architecture docs** | ❌ None | ✅ `VISION_AND_TRADEOFFS.md` |
| **CONTRIBUTING** | ❌ None | ✅ `CONTRIBUTING.md` |
| **API examples** | ❌ None in-repo | ✅ Inline code examples for every feature |
| **MkDocs site** | ❌ None | ✅ `mkdocs.yml` — documentation site |
| **Tuning guides** | ❌ None | ✅ Producer config, connection pool sizing formulas |
| **Score** | **2/10** | **~9/10** |

**Verdict:** Night and day. Gatling-Kafka-OSS has a professional documentation site with quickstart, vision doc, contribution guide, and detailed tuning advice. Performator-HTTP has a 7-line README pointing elsewhere.

---

### 4. Security Posture

| | Performator-HTTP | Gatling-Kafka-OSS |
|---|---|---|
| **TLS handling** | 🔴 **Trusts ALL certificates, disables hostname verification** | Standard Kafka SSL properties (user-configured) |
| **Credential handling** | `char[]` (good) but no vault integration | Kafka native SASL/SSL config — user passes props |
| **Vulnerability scanning** | ❌ None | Unknown (but no hardcoded trust-all) |
| **Score** | **2/10** | **~6/10** |

**Verdict:** Performator-HTTP has a critical vulnerability — globally disabled TLS validation with no opt-out. Gatling-Kafka-OSS delegates SSL/SASL to standard Kafka client config, which is the correct approach.

---

### 5. Architecture & Design

| | Performator-HTTP | Gatling-Kafka-OSS |
|---|---|---|
| **Pattern** | Fluent builder, `InheritableThreadLocal` state | Gatling DSL extension, Actor-based producers, dedicated consumer threads |
| **God classes** | ✅ Two 1,000+ line classes | Modular: `cache/`, `common/`, `kafka/` packages |
| **State management** | Thread-local only | Pluggable `RequestStore` (InMemory, Redis, Postgres, Buffered) |
| **Extensibility** | `PFRHttpCheckCustom` interface | `MessageCheck` API, multi-protocol, multi-serde support |
| **Async support** | ❌ Synchronous only | ✅ Fire-and-forget, request-reply with async matching |
| **Scalability** | Single-node | Multi-node via distributed stores (Redis/Postgres) |
| **Score** | **5/10** | **~8/10** |

**Verdict:** Gatling-Kafka-OSS demonstrates significantly more sophisticated architecture — pluggable storage backends, distributed state management, async retry with configurable backoff, and buffered write-behind stores for high throughput. Performator-HTTP is a monolithic single-node design.

---

### 6. Resilience & Error Handling

| | Performator-HTTP | Gatling-Kafka-OSS |
|---|---|---|
| **Retry logic** | ❌ None | ✅ Configurable `retryBackoff` + `maxRetries` |
| **Circuit breaker** | ❌ None | Strict-stop on persistence failure (`System.exit(1)`) |
| **Race condition handling** | N/A (synchronous HTTP) | ✅ Distributed retry for async matching |
| **Outlier detection** | ❌ None | ✅ `SLOW FLUSH DETECTED` warnings |
| **Chaos engineering** | ❌ Not designed for it | ✅ Explicit design goal — broker outage, app restart survival |
| **Score** | **6/10** | **~8/10** |

---

### 7. Observability

| | Performator-HTTP | Gatling-Kafka-OSS |
|---|---|---|
| **Metrics** | HSR (proprietary) integration | Gatling native reports (HTML/JSON) |
| **Logging** | SLF4J + Logback, configurable debug | Standard Gatling + SLF4J |
| **SLA checks** | ✅ Built-in SLA assertions | ✅ `MessageCheck` API with JSON path, echo, contains |
| **End-to-end latency** | Response time only | ✅ Full round-trip: send → process → receive |
| **OpenTelemetry** | ❌ OTel as dependency but not integrated | ❌ Not mentioned |
| **Score** | **7/10** | **~7/10** |

**Verdict:** Roughly comparable. Performator-HTTP has good HSR metrics. Gatling-Kafka-OSS leverages Gatling's mature reporting ecosystem and adds true end-to-end latency measurement for async flows.

---

### 8. Community & Governance

| | Performator-HTTP | Gatling-Kafka-OSS |
|---|---|---|
| **Stars** | 3 | 5 |
| **Open Issues** | Unknown | 3 |
| **Discussions** | ❌ Not enabled | ✅ Enabled |
| **CONTRIBUTING.md** | ❌ None | ✅ Present |
| **Content/Education** | ❌ None | ✅ YouTube channel, blog, live coding |
| **Bus factor** | 1 (single maintainer) | 1 (single maintainer) |
| **Score** | **1/10** | **~4/10** |

**Verdict:** Both are single-maintainer projects with low star counts. However, Gatling-Kafka-OSS has governance artifacts (CONTRIBUTING.md), community features (Discussions), and educational content backing it. Neither would pass a strict enterprise bus-factor requirement.

---

### 9. Feature Depth

| Feature | Performator-HTTP | Gatling-Kafka-OSS |
|---|:---:|:---:|
| Protocol-native DSL | ✅ | ✅ |
| Multi-auth (Basic, Digest, NTLM, Kerberos) | ✅ | N/A (Kafka SASL) |
| Proxy PAC auto-configuration | ✅ | N/A |
| Request-Reply pattern | N/A | ✅ |
| Fire-and-forget mode | N/A | ✅ |
| Raw consume (no request) | N/A | ✅ |
| Avro/Protobuf serialization | ❌ | ✅ |
| Multi-protocol per simulation | ❌ | ✅ |
| Custom headers | ✅ | ✅ |
| Cookie management | ✅ | N/A |
| Connection pooling | ✅ | N/A (Kafka handles internally) |
| Distributed state (Redis/Postgres) | ❌ | ✅ (Enterprise) |
| Feeder integration (CSV, random) | ❌ | ✅ |
| Idempotent/transactional producers | N/A | ✅ |
| Scala + Java DSL | ❌ Java only | ✅ Both |

---

## Summary Scorecard

| Dimension | Performator-HTTP | Gatling-Kafka-OSS | Winner |
|---|:---:|:---:|:---:|
| Automated Testing | 0 | ~5 | 🏆 Kafka |
| CI/CD Pipeline | 0 | ~7 | 🏆 Kafka |
| Documentation | 2 | ~9 | 🏆 Kafka |
| Security Posture | 2 | ~6 | 🏆 Kafka |
| Dependency Mgmt | 5 | ~6 | 🏆 Kafka |
| Code Quality | 6 | ~7 | 🏆 Kafka |
| Licensing | 8 | 8 | 🤝 Tie |
| Observability | 7 | ~7 | 🤝 Tie |
| Community | 1 | ~4 | 🏆 Kafka |
| Versioning | 4 | ~6 | 🏆 Kafka |
| Error Handling | 6 | ~8 | 🏆 Kafka |
| Architecture | 5 | ~8 | 🏆 Kafka |
| **TOTAL** | **~30** | **~72** | — |

---

## Key Takeaways

1. **Different leagues of maturity.** Gatling-Kafka-OSS is roughly **2.4× more enterprise-ready** than Performator-HTTP by the same rubric. The biggest gaps are testing (0 vs actual tests), CI/CD (nothing vs GitHub Actions + Docker), and documentation (7 lines vs a full docs site).

2. **Different protocols, but the engineering practices are what matter.** Even though HTTP and Kafka are different domains, we're comparing the *surrounding practices* — and Gatling-Kafka-OSS demonstrates significantly more discipline in documentation, architecture decisions (VISION_AND_TRADEOFFS.md), and community governance (CONTRIBUTING.md).

3. **Shared weakness: bus factor.** Both projects have a single maintainer and low star counts. Neither would satisfy a strict enterprise procurement review on community health alone.

4. **Gatling-Kafka-OSS benefits from standing on Gatling's shoulders.** By extending an industry-standard framework, it inherits Gatling's mature reporting, injection profiles, CI/CD plugins, and community ecosystem. Performator-HTTP depends on the niche Performator framework with limited adoption.

5. **Performator-HTTP's TLS issue is disqualifying.** The unconditional `acceptAll` trust strategy is a showstopper that Gatling-Kafka-OSS avoids entirely by delegating SSL config to the Kafka client.

> **Bottom line:** If you're evaluating both for enterprise use, Gatling-Kafka-OSS is meaningfully closer to production-ready, though both would benefit from additional hardening. Performator-HTTP would need a substantial remediation effort before it could be considered alongside Gatling-Kafka-OSS.
