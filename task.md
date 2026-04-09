# Performator-HTTP Rewrite — Task Tracker

## Build Configuration
- [/] Update `pom.xml` (JDK 21, deps, test infra)

## Core Changes
- [x] `PFRHttp.java` — configurable pools, TLS, bug fixes, virtual threads, async connection manager
- [x] `PFRHttpRequestBuilder.java` — async client, `sendAsync()`, auth extraction
- [x] `PFRHttpResponse.java` — async constructor

## Test Suite
- [ ] `PFRHttpCheckTest.java`
- [ ] `PFRHttpResponseTest.java`
- [ ] `PFRHttpRequestBuilderTest.java`
- [ ] `PFRHttpTest.java`
- [ ] `ResponseFailedExceptionTest.java`
- [x] `PFRHttpIntegrationTest.java`
- [ ] `HttpPacScriptMethodsTest.java`
- [x] `ConnectionPoolConfigTest.java`
- [x] `TlsConfigTest.java`
- [x] `VirtualThreadsConfigTest.java`

## Documentation
- [ ] Update `README.md`

## Verification
- [ ] `mvn clean verify` passes
- [ ] Coverage report generated
