# Performator-HTTP Rewrite: JDK 21 + Async HC5 + Full Test Suite

## Goal

Modernize Performator-HTTP from a blocking Java 17 / HC5 classic implementation to a JDK 21 / HC5 async-capable architecture with configurable connection pools, TLS safety, and comprehensive unit tests. Preserve full backward compatibility with the existing public API.

---

## User Review Required

> [!IMPORTANT]
> **Dependency constraint:** The `performator` framework (v1.2.3) is a `provided` dependency. We cannot modify it, and we depend on `PFR`, `PFRContext`, `HSR`, `HSRRecord`, `HSRSLA`, `ByteSize`, `XRText.CheckType`. The rewrite must keep the same API contract that existing Performator scripts use. Verify that the parent framework runs on JDK 21 before proceeding.

> [!WARNING]
> **Breaking change risk:** Switching the `InheritableThreadLocal` pattern to virtual threads may affect frameworks that rely on thread identity or `ThreadGroup`. Need confirmation that Performator's thread model is compatible with virtual threads.

> [!IMPORTANT]  
> **`sendAsync()` API addition:** The plan adds a new `sendAsync()` method returning `CompletableFuture<PFRHttpResponse>`. The existing `send()` remains synchronous (but internally uses the async client + `.get()`). Is this the desired approach, or should `send()` itself become async?

---

## Proposed Changes

### Build Configuration

#### [MODIFY] [pom.xml](file:///Users/jakubdering/Performator-HTTP/pom.xml)
- Bump Java source/target from `17` → `21`
- Bump `httpclient5` from `5.3.1` → `5.4.1` (latest stable)
- Bump JUnit from `5.7.2` / `1.8.2` → `5.11.4` / `1.11.4`
- Change JUnit scope from `provided` → `test`
- Add `mockito-core` 5.x for mocking
- Add `wiremock` 3.x for HTTP server simulation in tests
- Add `maven-surefire-plugin` 3.x configuration
- Bump `maven-compiler-plugin` from `3.8.0` → `3.13.0`
- Add `jacoco-maven-plugin` for code coverage reporting

---

### Core HTTP Client (Async Rewrite)

#### [MODIFY] [PFRHttp.java](file:///Users/jakubdering/Performator-HTTP/src/main/java/com/performetriks/performator/http/PFRHttp.java)

**Connection pool — make configurable:**
```diff
-connectionManager.setMaxTotal(1000);
-connectionManager.setDefaultMaxPerRoute(200);
+connectionManager.setMaxTotal(maxTotalConnections.get());
+connectionManager.setDefaultMaxPerRoute(maxPerRouteConnections.get());
```
Add new public API:
```java
public static void defaultMaxTotalConnections(int max)
public static void defaultMaxPerRouteConnections(int max)
```

**TLS — make trust strategy configurable:**
```diff
-final TrustStrategy acceptingTrustStrategy = (cert, authType) -> true;
+final TrustStrategy trustStrategy = trustAllCertificates.get()
+    ? (cert, authType) -> true
+    : null; // use JVM default trust
```
Add new public API:
```java
public static void defaultTrustAllCertificates(boolean trustAll)  // default: false
```

**Bug fix — `defaultBodyCharset()` setter:**
```diff
 public static void defaultBodyCharset(Charset charset) {
-    PFRHttp.bodyCharset.set(StandardCharsets.UTF_8);
+    PFRHttp.bodyCharset.set(charset);
 }
```

**Connection manager — switch to async-compatible:**
- Replace `PoolingHttpClientConnectionManager` with `PoolingAsyncClientConnectionManager`
- Add `getAsyncConnectionManager()` alongside existing `getConnectionManager()`
- Add JVM shutdown hook to close connection manager

**Inner class cleanup:**
```diff
-protected class PFRProxy {
+protected record PFRProxy(String type, String host, int port) {}
```

#### [MODIFY] [PFRHttpRequestBuilder.java](file:///Users/jakubdering/Performator-HTTP/src/main/java/com/performetriks/performator/http/PFRHttpRequestBuilder.java)

**Client creation — add async client:**
```diff
-private static CloseableHttpClient httpClientSingle;
+private static CloseableHttpClient httpClientSingle;          // legacy sync
+private static CloseableHttpAsyncClient httpAsyncClientSingle; // new async
```

**New `sendAsync()` method:**
```java
public CompletableFuture<PFRHttpResponse> sendAsync() {
    // Uses CloseableHttpAsyncClient
    // Returns immediately, completes when response arrives
    // Runs checks and metrics on the async callback thread
}
```

**Existing `send()` — backward compatible:**
```java
public PFRHttpResponse send() {
    return sendAsync().join(); // blocks caller, but uses async client internally
}
```

This means the existing blocking API continues to work identically, but the underlying HTTP call uses the NIO-based async client. When used with virtual threads, this gives 10K+ concurrency without the pool contention issue.

**Auth handling — extract to helper class (reduces god-class):**
- Extract the 70-line auth `switch` block from `send()` into a private `AuthHelper.configureAuth(context, ...)` method

#### [MODIFY] [PFRHttpResponse.java](file:///Users/jakubdering/Performator-HTTP/src/main/java/com/performetriks/performator/http/PFRHttpResponse.java)

**Add async constructor:**
```java
// New: constructed from async response data
public PFRHttpResponse(PFRHttpRequestBuilder request,
                       int statusCode, Header[] headers, String body) {
    // Sets fields directly — no HTTP execution
    // Checks and metrics are run after construction
}
```

The existing constructor (which executes the request) remains for backward compat but is marked `@Deprecated`.

---

### Test Suite

All tests go in `src/test/java/com/performetriks/performator/http/`.

#### [NEW] PFRHttpCheckTest.java
Unit tests for `PFRHttpCheck` validation logic:
- `checkBody_contains_success` / `_failure`
- `checkBody_regex_matchSuccess` / `_noMatch`
- `checkBody_equals_success` / `_failure`
- `checkHeader_exists` / `_missing`
- `checkHeader_regex`
- `checkStatus_equals_200` / `_404` / `_500`
- `checkCustom_callbackInvoked`
- `check_nullResponse_returnsFalse`
- `check_errorResponse_returnsFalse`
- `appendLogDetails_enabled` / `_disabled`
- `messageOnFail_customMessage`

#### [NEW] PFRHttpResponseTest.java
Unit tests for response accessors and JSON parsing:
- `isSuccess_200_noChecks`
- `isSuccess_500_autoFailOn`
- `isSuccess_500_autoFailOff`
- `isSuccess_checkFailed`
- `getBody_nullSafe`
- `getBodySize_utf8Calculation`
- `getBodySize_byteConversion`
- `getBodyAsJsonObject_validJson`
- `getBodyAsJsonObject_invalidJson_returnsNull`
- `getBodyAsJsonObject_emptyBody_returnsNull`
- `getBodyAsJsonArray_validArray`
- `getBodyAsJsonArray_wrappedObject`
- `getHeadersAsJson_conversion`
- `getHeadersAsMap_conversion`
- `getHeadersAsMap_nullHeaders`
- `getStatusWithReason_200` / `_404` / `_negativeOne`
- `throwOnFail_successDoesNotThrow`
- `throwOnFail_failureThrows`
- `throwOnFail_exceptionContainsResponse`

#### [NEW] PFRHttpRequestBuilderTest.java  
Unit tests for the builder pattern and URL construction:
- `defaultMethod_isGET`
- `GET_POST_PUT_DELETE_METHOD_setCorrectly`
- `param_addedToURL`
- `params_multipleParams_encodedCorrectly`
- `header_lowercased`
- `headers_mapMerge`
- `body_setsContentType`
- `bodyCharset_usesDefault`
- `bodyCharset_overridden`
- `buildURLwithParams_noParams`
- `buildURLwithParams_withParams_urlEncoded`
- `buildURLwithParams_specialCharacters`
- `isIncludedHeader_excludesPseudoHeaders`
- `isIncludedHeader_excludesContentLength`
- `isIncludedHeader_excludesTransferEncoding`
- `isIncludedHeader_includesNormalHeaders`
- `auth_basic_setsCredentials`
- `auth_basicHeader_addsAuthorizationHeader`
- `auth_NTLM_parsesDomain`
- `responseTimeout_default` / `_overridden`
- `throwOnFail_default` / `_overridden`
- `sla_configuration`
- `check_addedToList`
- `multipleChecks_addedInOrder`

#### [NEW] PFRHttpTest.java
Unit tests for global state management:
- `defaultTimeout_getAndSet`
- `defaultSocketTimeout_getAndSet`
- `defaultConnectTimeout_getAndSet`
- `defaultHeaders_getAndSet`
- `defaultBodyCharset_getAndSet`
- `defaultBodyCharset_bugFixed` (verifies parameter is used)
- `defaultUserAgent_getAndSet`
- `defaultThrowOnFail_getAndSet`
- `defaultPause_getAndSet`
- `defaultMaxTotalConnections_getAndSet` (new)
- `defaultMaxPerRouteConnections_getAndSet` (new)
- `defaultTrustAllCertificates_getAndSet` (new)
- `inheritableThreadLocal_childInheritsParent`
- `inheritableThreadLocal_childOverrideDoesNotAffectParent`
- `addBasicAuthorizationHeader_encodesCorrectly`
- `cookieStore_addAndClear`
- `cookieStore_clearExpired`
- `create_returnsBuilderWithUrl`
- `create_withMetric_returnsBuilderWithMetricAndUrl`

#### [NEW] ResponseFailedExceptionTest.java
- `constructor_withError_usesErrorMessage`
- `constructor_withoutError_usesStatusAndReason`
- `getResponse_returnsOriginalResponse`

#### [NEW] PFRHttpIntegrationTest.java
Integration tests using WireMock (HTTP server mock):
- `sendGET_returns200`
- `sendPOST_withBody_returns201`
- `sendPUT_returns200`
- `sendDELETE_returns204`
- `send_customMethod_PATCH`
- `send_withParams_encodedInURL`
- `send_withHeaders_sentToServer`
- `send_withBasicAuth_headerPresent`
- `send_followsRedirects_default`
- `send_disableRedirects`
- `send_timeout_throwsException`
- `send_serverError_500_autoFail`
- `send_serverError_500_noAutoFail`
- `send_checkBodyContains_pass` / `_fail`
- `send_checkStatusEquals_pass` / `_fail`
- `send_checkHeaderRegex_pass` / `_fail`
- `send_throwOnFail_throwsResponseFailedException`
- `send_connectionPool_reusesConnections`
- `sendAsync_returns200` (new async API)
- `sendAsync_completesOnDifferentThread`
- `sendAsync_multipleParallel_allComplete`

#### [NEW] HttpPacScriptMethodsTest.java
Unit tests for PAC proxy script evaluation:
- `isPlainHostName_trueForPlainHost`
- `isPlainHostName_falseForFQDN`
- `dnsDomainIs_matchesDomain`
- `localHostOrDomainIs_matchesLocalHost`
- `isResolvable_localhost`
- `isInNet_matchesSubnet`
- `myIpAddress_returnsNonNull`
- `dnsDomainLevels_countsDots`
- `shExpMatch_wildcardMatch`

#### [NEW] ConnectionPoolConfigTest.java
Tests for the new configurable pool:
- `defaultPool_1000max_200perRoute` (backward compat)
- `customPool_setMaxTotal`
- `customPool_setMaxPerRoute`
- `connectionManager_singletonPattern`
- `connectionManager_shutdownHook_registered`

#### [NEW] TlsConfigTest.java
Tests for the new configurable TLS:
- `default_doesNotTrustAll` (new secure default!)
- `trustAll_enabled_acceptsSelfSigned`
- `trustAll_disabled_rejectsSelfSigned`
- `keystorePath_loadsJKS`
- `keystorePath_loadsPKCS12`
- `keystoreManagerPW_passed`

---

### Documentation

#### [MODIFY] [README.md](file:///Users/jakubdering/Performator-HTTP/README.md)
- Add badges (build status, coverage, Maven Central version)
- Add "Quick Start" section with code example
- Document `sendAsync()` API
- Document configurable pool sizes
- Document TLS configuration (default-secure)

---

## Open Questions

> [!IMPORTANT]
> 1. **Performator JDK 21 compatibility**: Has the parent `performator` framework been tested on JDK 21? If not, upgrading this plugin to 21 would force a framework-wide upgrade.
> 2. **Virtual threads**: Should we add an explicit `Thread.ofVirtual()` integration, or leave that to the framework layer?
> 3. **Converter/Swing**: The `PFRHttpConverter.java` (63KB Swing UI) is untouched by this plan. Should it stay as-is, or should it be extracted to a separate module?
> 4. **GraalVM JS**: The PAC script engine uses GraalVM JS 25.0.0. Does this work on JDK 21?

## Verification Plan

### Automated Tests
```bash
# Build + run all tests
mvn clean verify

# Run with coverage report
mvn clean verify -Djacoco.skip=false

# View coverage report
open target/site/jacoco/index.html
```

### Manual Verification
- Verify `mvn clean package` produces a valid JAR
- Verify the JAR works as a drop-in replacement for the existing 1.2.3 artifact
- Verify `sendAsync()` completes on a different thread than the caller
- Verify connection pool metrics under load (1000+ concurrent calls to WireMock)
