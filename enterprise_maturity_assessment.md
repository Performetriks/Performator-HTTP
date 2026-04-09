# Performator-HTTP — Enterprise Maturity Assessment

> **Repository:** `com.performetriks:performator-http` v1.2.3
> **License:** Eclipse Public License v2.0
> **Assessment Date:** 2026-04-07

---

## Executive Summary

Performator-HTTP is a niche, small-footprint HTTP plugin (~5,800 LOC across 11 Java files) for the Performator load testing framework. While the core HTTP client code demonstrates **solid engineering** (fluent API, connection pooling, PAC proxy support, multiple auth methods), the repository **falls significantly short of enterprise-grade maturity** in the areas of testing, CI/CD, documentation, and community adoption.

**Overall Readiness: 🟡 Not Enterprise-Ready (30/100)**

---

## Scoring Matrix

| # | Dimension | Weight | Score | Weighted |
|---|-----------|--------|-------|----------|
| 1 | **Automated Testing** | 15% | 0/10 | 0.0 |
| 2 | **CI/CD Pipeline** | 12% | 0/10 | 0.0 |
| 3 | **Documentation** | 10% | 2/10 | 2.0 |
| 4 | **Security Posture** | 12% | 2/10 | 2.4 |
| 5 | **Dependency Management** | 8% | 5/10 | 4.0 |
| 6 | **Code Quality & Structure** | 10% | 6/10 | 6.0 |
| 7 | **Licensing & IP** | 5% | 8/10 | 4.0 |
| 8 | **Observability & Logging** | 5% | 7/10 | 3.5 |
| 9 | **Community & Governance** | 8% | 1/10 | 0.8 |
| 10 | **Versioning & Release** | 5% | 4/10 | 2.0 |
| 11 | **Error Handling & Resilience** | 5% | 6/10 | 3.0 |
| 12 | **Architecture & Extensibility** | 5% | 5/10 | 2.5 |
| | **TOTAL** | **100%** | | **30.2/100** |

---

## Detailed Findings

### 1. Automated Testing — 🔴 0/10

> **CAUTION: There is no `src/test` directory.** Zero unit tests, zero integration tests, zero test classes exist in the entire repository.

- No JUnit tests despite JUnit 5 being declared as a dependency in `pom.xml`
- No test resources, no test fixtures, no mocking frameworks
- No code coverage tooling (JaCoCo, Cobertura, etc.)
- No mutation testing, no property-based testing
- The JUnit dependencies are scoped as `provided`, suggesting they're intended for the consuming framework only

**Enterprise expectation:** ≥80% code coverage, integration test suites, test reports in CI.

---

### 2. CI/CD Pipeline — 🔴 0/10

> **CAUTION: No CI/CD configuration exists whatsoever.**

- No `.github/workflows/` directory (no GitHub Actions)
- No Jenkinsfile, no `.travis.yml`, no `.gitlab-ci.yml`
- No Dockerfile or `docker-compose.yml`
- Build is manual: `docs/commands.txt` contains raw GPG signing commands for version `1.1.1` (stale — current is `1.2.3`)
- No automated release pipeline

**Enterprise expectation:** Automated build → test → SAST/DAST → publish pipeline with branch protection.

---

### 3. Documentation — 🟠 2/10

- **README.md:** 7 lines total — only a title, one-sentence description, and a link to external docs
- External documentation lives in a separate repo ([Performator-Quickstart](https://github.com/Performetriks/Performator-Quickstart/blob/main/docs/200_PerformatorHTTP.md))
- No architecture decision records (ADRs)
- No API usage examples in-repo
- No CHANGELOG, no migration guide between versions
- Javadoc is present on most public methods (positive) but `skipJavadoc=false` with `-Xdoclint:none` suppresses all Javadoc quality checks
- `docs/commands.txt` is a bare-bones internal reference, not user-facing

**Enterprise expectation:** Comprehensive README, inline examples, API reference, changelog, contribution guide.

---

### 4. Security Posture — 🔴 2/10

> **WARNING: TLS certificate validation is unconditionally disabled.**

```java
// PFRHttp.java:1065
final TrustStrategy acceptingTrustStrategy = (cert, authType) -> true;
// PFRHttp.java:1074
new SSLConnectionSocketFactory(sslContextBuilder.build(), NoopHostnameVerifier.INSTANCE);
```

This **trusts all certificates** and **disables hostname verification** globally. This is a fundamental MITM vulnerability with no opt-in/opt-out mechanism.

Additional security concerns:
- **No SECURITY.md** or vulnerability disclosure policy
- **No dependency scanning** (OWASP Dependency-Check, Snyk, Dependabot)
- **No SBOM generation** (CycloneDX, SPDX)
- Passwords stored as `char[]` (good) but no credential management integration
- Proxy PAC scripts evaluated via GraalVM JavaScript — potential injection vector if PAC source is untrusted
- GPG signing is configured but with `--always-trust` flag

---

### 5. Dependency Management — 🟡 5/10

| Dependency | Version | Latest | Status |
|---|---|---|---|
| `performator` (parent) | 1.2.3 | 1.2.3 | ✅ Current |
| `httpclient5` | 5.3.1 | 5.4.x | ⚠️ Behind |
| `logback-classic` | 1.5.12 | — | ✅ Recent |
| `graalvm-js` | 25.0.0 | — | ✅ Recent |
| `junit-jupiter` | 5.7.2 | 5.11.x | ⚠️ Very old |
| `junit-platform` | 1.8.2 | 1.11.x | ⚠️ Very old |
| `maven-compiler-plugin` | 3.8.0 | 3.13.x | ⚠️ Old |
| `maven-gpg-plugin` | 1.6 | 3.2.x | ⚠️ Very old |
| `maven-install-plugin` | 2.3.1 | 3.1.x | ⚠️ Old |

- No dependabot/renovate configuration
- No `maven-enforcer-plugin` for dependency convergence
- No BOM (Bill of Materials) for transitive dependency management

---

### 6. Code Quality & Structure — 🟢 6/10

**Positives:**
- Clean package structure: `com.performetriks.performator.http`
- Fluent builder API pattern (`PFRHttpRequestBuilder`)
- Thread-safety via `InheritableThreadLocal` for multi-user load testing
- Connection pooling with configurable limits (1000 max / 200 per-route)
- SPI service registration via `META-INF/services`
- No `TODO`, `FIXME`, or `HACK` markers found
- Consistent copyright headers across all files

**Concerns:**
- **God class:** `PFRHttp.java` (1,092 lines) and `PFRHttpRequestBuilder.java` (1,083 lines) handle too many responsibilities
- `PFRHttpConverter.java` is 63KB — likely a monolithic Swing UI class
- `PFRProxy` is an inner non-static class with public fields — should be a record or DTO
- Eclipse IDE files (`.classpath`, `.project`, `.settings/`) committed to repo — should be gitignored
- No static analysis configuration (SpotBugs, PMD, Checkstyle, ErrorProne)
- `@SuppressWarnings("deprecation")` in critical send path
- **Bug:** `defaultBodyCharset(Charset charset)` setter ignores its parameter (line 286: always sets UTF-8)
- `getProxy()` logic appears inverted — returns `null` when PAC *is* configured

---

### 7. Licensing & IP — 🟢 8/10

- EPL-2.0 license: enterprise-compatible, clearly stated
- License file present at root
- Copyright headers on every source file referencing Performetriks GmbH, Switzerland
- `HttpPacScriptMethods.java` properly attributes Apache 2.0 origin (proxy-vole project)
- SCM metadata in POM is correctly configured

**Minor issue:** SCM `<url>` points to `Performator-Quickstart` repo rather than this repo's tree.

---

### 8. Observability & Logging — 🟢 7/10

- SLF4J + Logback for structured logging
- Debug logging with configurable granularity (`debugLogAll`, `debugLogFail`)
- HSR (High-Speed Recording) integration for metrics collection
- Response timing measurement built-in
- SLA checking with operator-based assertions
- Range-based metric bucketing for analysis
- Size measurement support

**Gaps:** No OpenTelemetry/OTLP integration, no distributed tracing context propagation.

---

### 9. Community & Governance — 🔴 1/10

- **3 GitHub stars** — extremely low adoption signal
- Single maintainer (`xresch` / Reto Scheiwiller)
- No `CONTRIBUTING.md`, no `CODE_OF_CONDUCT.md`
- No issue templates, no PR templates
- No discussions, no community forum
- Published to Maven Central (positive), but low download counts
- Company-backed (Performetriks GmbH) but appears to be a single-person effort

**Bus factor: 1** — critical risk for enterprise dependency.

---

### 10. Versioning & Release — 🟡 4/10

- Semantic versioning used (1.2.3)
- Maven Central publication with GPG signing
- Source and Javadoc JARs attached

**Gaps:**
- No CHANGELOG.md or release notes
- `docs/commands.txt` references v1.1.1 while POM is at v1.2.3 — stale docs
- No automated release process
- No multi-version support strategy documented

---

### 11. Error Handling & Resilience — 🟢 6/10

**Positives:**
- `ResponseFailedException` with response access for error inspection
- Configurable `throwOnFail` at both global and per-request level
- Graceful degradation: returns empty response on error to prevent NPEs
- Connection keep-alive strategy (60s) to prevent ephemeral port exhaustion
- Socket linger and TCP_NODELAY configured
- Proxy failover: iterates PAC proxies when addresses are unresolved

**Gaps:**
- No retry logic or circuit breaker
- No configurable backoff strategy
- Broad `catch (Throwable e)` in critical paths
- Proxy PAC cache is static and never invalidated
- Connection manager singleton with no shutdown hook

---

### 12. Architecture & Extensibility — 🟡 5/10

- SPI plugin system via `PFRCustomMode`
- Extensible check system with custom `PFRHttpCheckCustom` interface
- Builder pattern enables composable requests
- Thread-isolated state propagated to child threads

**Limitations:**
- No async/non-blocking HTTP support
- Tight coupling to Apache HC5 — no abstraction layer
- No plugin for HTTP/2 or WebSocket
- Converter is a Swing desktop application — unusual for a server-side library
- No dependency injection integration

---

## Risk Summary for Enterprise Adoption

| Risk Level | Category |
|---|---|
| 🔴 **Critical** | Zero test coverage — no confidence in correctness |
| 🔴 **Critical** | No CI/CD — no automated quality gates |
| 🔴 **Critical** | TLS validation disabled by default — MITM vulnerability |
| 🔴 **Critical** | Bus factor of 1 — single maintainer with 3 GitHub stars |
| 🟠 **High** | No security scanning, no SBOM, no vulnerability disclosure process |
| 🟠 **High** | Stale dependencies (JUnit 5.7.2, maven-gpg-plugin 1.6) |
| 🟡 **Medium** | No CHANGELOG, no migration guides between versions |
| 🟡 **Medium** | God classes exceeding 1,000 lines |
| 🟢 **Low** | Eclipse IDE files committed to repo |

---

## Recommendations

### Immediate (Before Enterprise Use)

1. **Fork and add test coverage** — write tests for `PFRHttpRequestBuilder.send()`, proxy resolution, PAC script parsing, and all auth methods
2. **Fix TLS configuration** — make certificate trust configurable; never default to `acceptAll`
3. **Add CI/CD** — GitHub Actions with build → test → dependency scan → publish
4. **Add OWASP Dependency-Check** to the Maven build

### Short-Term (Enterprise Hardening)

5. Add a retry/backoff mechanism for transient failures
6. Update stale dependencies (especially JUnit and Maven plugins)
7. Break up god classes (`PFRHttp`, `PFRHttpRequestBuilder`, `PFRHttpConverter`)
8. Add CHANGELOG.md and proper release notes
9. Configure Dependabot/Renovate for automated dependency updates

### Long-Term (Enterprise-Grade)

10. Add OpenTelemetry integration for distributed tracing
11. Support async HTTP (CompletableFuture-based API)
12. Establish a multi-maintainer governance model
13. Add SBOM generation (CycloneDX Maven plugin)
14. Publish security policy (SECURITY.md)

---

## Verdict

> **Performator-HTTP is not ready for enterprise-grade testing in its current state.** The combination of zero test coverage, no CI/CD, disabled TLS verification, and a single maintainer makes it an unacceptable risk for production enterprise environments. The core HTTP client logic is competently written, but the project lacks the surrounding engineering practices required for enterprise trust. If adoption is necessary, **fork it, test it, and harden it** before deploying into any environment handling sensitive data.
