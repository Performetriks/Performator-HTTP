package com.performetriks.performator.http;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.HttpRoute;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.Cookie;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.cookie.BasicClientCookie;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.performetriks.performator.http.scriptengine.PFRScripting;
import com.performetriks.performator.http.scriptengine.PFRScriptingContext;
import com.xresch.hsr.base.HSR;
import com.xresch.xrutils.utils.XRTimeUnit;

import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.pool.PoolReusePolicy;

/***************************************************************************
 * 
 * Copyright Owner: Performetriks GmbH, Switzerland
 * License: Eclipse Public License v2.0
 * 
 * @author Reto Scheiwiller
 * 
 ***************************************************************************/
public class PFRHttp {
	
	static Logger logger = LoggerFactory.getLogger(PFRHttp.class.getName());
				
	// only one static connection pool, avoid ephemeral port exhaustion
	private static PoolingHttpClientConnectionManager connectionManager = null;
	
	//Use Threadlocal to avoid polyglot multi thread exceptions
	private static ThreadLocal<PFRScriptingContext> javascriptEngine = new ThreadLocal<PFRScriptingContext>();
	
	private static KeyStore cachedKeyStore = null;
	
	private static String proxyPAC = null;
	private static HashMap<String, ArrayList<PFRProxy>> resolvedProxiesCache = new HashMap<>();

	private static PFRHttp instance = new PFRHttp();
	
	// Connection pool configuration (global, set before first request)
	private static final AtomicInteger maxTotalConnections = new AtomicInteger(3000);
	private static final AtomicInteger maxPerRouteConnections = new AtomicInteger(1000);

	// TLS configuration (global)
	private static final AtomicBoolean trustAllCertificates = new AtomicBoolean(true);

	// Virtual thread support (global)
	private static final AtomicBoolean useVirtualThreads = new AtomicBoolean(false);

	// Sub-request measurement flags (global, off by default)
	private static final AtomicBoolean measureDns = new AtomicBoolean(false);
	private static final AtomicBoolean measureTls = new AtomicBoolean(false);
	private static final AtomicBoolean measureConnect = new AtomicBoolean(false);

	// either a http URL or a resourcePath like "com/mycompany/files/script.pac"
	private static InheritableThreadLocal<String> proxyPacFile = new InheritableThreadLocal<>();
	
	private static InheritableThreadLocal<String> keystorePath = new InheritableThreadLocal<>();
	private static InheritableThreadLocal<String> keystorePW = new InheritableThreadLocal<>();
	private static InheritableThreadLocal<String> keystoreManagerPW = new InheritableThreadLocal<>();
	
	private static InheritableThreadLocal<String> defaultUserAgent = new InheritableThreadLocal<>();


	static InheritableThreadLocal<BasicCookieStore> cookieStore = new InheritableThreadLocal<>() { 
		@Override
	    protected BasicCookieStore initialValue() {
	        return new BasicCookieStore();
	    }
	};
	
	static InheritableThreadLocal<HttpClientContext> httpContextStore = new InheritableThreadLocal<>() {
		@Override
		protected HttpClientContext initialValue() {
			HttpClientContext context = HttpClientContext.create();
			context.setCookieStore(cookieStore.get());
			return context;
		}
	};

	private static InheritableThreadLocal<HashMap<String, String>> defaultHeaders =  new InheritableThreadLocal<>();
	
	private static InheritableThreadLocal<Charset> defaultBodyCharset =  new InheritableThreadLocal<>() { 
		@Override
		protected Charset initialValue() {
			return StandardCharsets.UTF_8;
		}
	};
	
	private static InheritableThreadLocal<Long> defaultResponseTimeoutMillis =  new InheritableThreadLocal<>() { 
		@Override
	    protected Long initialValue() {
			//default timeout of  3 minutes
	        return XRTimeUnit.m.toMillis(3);
	    }
	};
	
	private static InheritableThreadLocal<Long> defaultConnectTimeoutMillis =  new InheritableThreadLocal<>() { 
		@Override
		protected Long initialValue() {
			//default timeout of  10 s
			return XRTimeUnit.s.toMillis(10);
		}
	};
	
	private static InheritableThreadLocal<Long> defaultSocketTimeoutMillis =  new InheritableThreadLocal<>() { 
		@Override
		protected Long initialValue() {
			//default timeout of 30 s
			return XRTimeUnit.s.toMillis(30);
		}
	};
	
	private static InheritableThreadLocal<Long> defaultPauseMillisLower = new InheritableThreadLocal<>() { 
		@Override
	    protected Long initialValue() {
	        return 0L;
	    }
	};
	
	private static InheritableThreadLocal<Long> defaultPauseMillisUpper = new InheritableThreadLocal<>() { 
		@Override
	    protected Long initialValue() {
	        return 0L;
	    }
	};
	
			
	private static InheritableThreadLocal<Boolean> debugLogAll = new InheritableThreadLocal<>() { 
		@Override
	    protected Boolean initialValue() {
	        return false;
	    }
	};
	
	private static InheritableThreadLocal<Boolean> debugLogFail = new InheritableThreadLocal<>() { 
		@Override
	    protected Boolean initialValue() {
	        return false;
	    }
	};
	
	private static InheritableThreadLocal<Boolean> throwOnFail = new InheritableThreadLocal<>() { 
		@Override
	    protected Boolean initialValue() {
	        return false;
	    }
	};
		
	
	public enum PFRHttpAuthMethod{
		/* Digest authentication with the Apache HttpClient */
		  BASIC
		  /* Digest authentication using Basic Header */
		, BASIC_HEADER
		  /* Digest authentication with the Apache HttpClient */
		, DIGEST
		  /* NTLM: untested, deprecated, experimental */
		, NTLM
		  /* Kerberos: untested, deprecated, experimental */
		, KERBEROS 
	}
	
	public enum PFRHttpSection{
		  HEADER
		, BODY
		, STATUS
	}

	/******************************************************************************************************
	 * Clears the current thread's HTTP session state (cookies and HTTP context).
	 * This should be called by the scheduler at the end of a use case iteration
	 * to prevent session leakage when threads are reused.
	 ******************************************************************************************************/
	public static void resetSession() {
		cookieStore.remove();
		httpContextStore.remove();
	}

	/******************************************************************************************************
	 * Clears ALL thread-local configuration for the current thread, including
	 * timeouts, charsets, and proxy settings, returning the thread to a clean
	 * state.
	 ******************************************************************************************************/
	public static void resetThreadState() {
		resetSession();
		proxyPacFile.remove();
		keystorePath.remove();
		keystorePW.remove();
		keystoreManagerPW.remove();
		defaultHeaders.remove();
		defaultUserAgent.remove();
		defaultBodyCharset.remove();
		defaultResponseTimeoutMillis.remove();
		defaultConnectTimeoutMillis.remove();
		defaultSocketTimeoutMillis.remove();
		debugLogAll.remove();
		debugLogFail.remove();
		throwOnFail.remove();
		defaultPauseMillisLower.remove();
		defaultPauseMillisUpper.remove();
	}

	/******************************************************************************************************
	 * Returns the current thread's HTTP context.
	 ******************************************************************************************************/
	public static HttpClientContext getContext() {
		return httpContextStore.get();
	}

	/******************************************************************************************************
	 * Manually sets the current thread's HTTP context.
	 ******************************************************************************************************/
	public static void setContext(HttpClientContext context) {
		httpContextStore.set(context);
	}
	
	/******************************************************************************************************
	 * <b>Scope:</b> Propagated (Inheritable Thread Local) <br>
	 * Enable debug logs for the current user thread for all request, regardless if they are successful
	 * or failing.
	 ******************************************************************************************************/
	public static void debugLogAll(boolean enable) {
		PFRHttp.debugLogAll.set(enable);
	}
	
	/******************************************************************************************************
	 * <b>Scope:</b> Propagated (Inheritable Thread Local) <br>
	 * @return boolean setting value of debugLogAll
	 ******************************************************************************************************/
	public static boolean debugLogAll() {
		return PFRHttp.debugLogAll.get();
	}
	
	/******************************************************************************************************
	 * <b>Scope:</b> Propagated (Inheritable Thread Local) <br>
	 * Enable debug logs for the current user thread for failing requests.
	 ******************************************************************************************************/
	public static void debugLogFail(boolean enable) {
		PFRHttp.debugLogFail.set(enable);
	}
	
	/******************************************************************************************************
	 * <b>Scope:</b> Propagated (Inheritable Thread Local) <br>
	 * @return boolean setting value of debugLogAll
	 ******************************************************************************************************/
	public static boolean debugLogFail() {
		return PFRHttp.debugLogFail.get();
	}
	
	/******************************************************************************************************
	 * <b>Scope:</b> Propagated (Inheritable Thread Local) <br>
	 * Set the default value of throwOnFail.
	 * If true, requests that fail will throw a ResponseFailedException.
	 * 
	 ******************************************************************************************************/
	public static void defaultThrowOnFail(boolean enable) {
		PFRHttp.throwOnFail.set(enable);
	}
	
	/******************************************************************************************************
	 * <b>Scope:</b> Propagated (Inheritable Thread Local) <br>
	 * Get the default value of throwOnFail.
	 * If true, requests that fail will throw a ResponseFailedException.
	 * 
	 ******************************************************************************************************/
	public static boolean defaultThrowOnFail() {
		return PFRHttp.throwOnFail.get();
	}
	
	/******************************************************************************************************
	 * <b>Scope:</b> Propagated (Inheritable Thread Local) <br>
	 * Sets(overrides) the default headers for all the requests of the current thread. These headers will always
	 * be added first to any request and can be enhanced and overridden with any additional headers specified
	 * on the request. 
	 * 
	 * @param headersKeyValue params at odd positions are keys, params at even positions are values
	 ******************************************************************************************************/
	public static void defaultHeaders(String... headersKeyValue) {
		
		HashMap<String, String> headers = new HashMap<>();
		for(int i = 0; i < headersKeyValue.length; i += 2) {
			headers.put(headersKeyValue[i], headersKeyValue[i+1]);
		}
		
		defaultHeaders.set(headers);
	}
	
	/******************************************************************************************************
	 * <b>Scope:</b> Propagated (Inheritable Thread Local) <br>
	 * Sets(overrides) the default headers for all the requests of the current thread. These headers will always
	 * be added first to any request and can be enhanced and overridden with any additional headers specified
	 * on the request. 
	 * 
	 * @param map of values
	 ******************************************************************************************************/
	public static void defaultHeaders(HashMap<String, String> headers) {
		defaultHeaders.set(headers);
	}
	
	/******************************************************************************************************
	 * <b>Scope:</b> Propagated (Inheritable Thread Local) <br>
	 * Returns the default headers for all the requests of the current thread.
	 ******************************************************************************************************/
	public static HashMap<String, String> defaultHeaders() {
		return defaultHeaders.get();
	}
	/******************************************************************************************************
	 * <b>Scope:</b> Propagated (Inheritable Thread Local) <br>
	 * Set the default charset for all the request bodies of the current thread. This value will be ignored
	 * if a charset is set inside the content-type header.
	 ******************************************************************************************************/
	public static void defaultBodyCharset(Charset charset) {
		defaultBodyCharset.set(StandardCharsets.UTF_8);
	}
	
	/******************************************************************************************************
	 * <b>Scope:</b> Propagated (Inheritable Thread Local) <br>
	 * Returns the default charset for all the request bodies of the current thread.This value will be ignored
	 * if a charset is set inside the content-type header.
	 ******************************************************************************************************/
	public static Charset defaultBodyCharset() {
		return defaultBodyCharset.get();
	}
	
	/******************************************************************************************************
	 * <b>Scope:</b> Propagated (Inheritable Thread Local) <br>
	 * Set the default response timeout used for all the requests of the current thread.
	 ******************************************************************************************************/
	public static void defaultResponseTimeout(long millis) {
		defaultResponseTimeoutMillis.set(millis);
	}
	
	/******************************************************************************************************
	 * <b>Scope:</b> Propagated (Inheritable Thread Local) <br>
	 * Returns the default response timeout for all the requests of the current thread.
	 ******************************************************************************************************/
	public static long defaultResponseTimeout() {
		return defaultResponseTimeoutMillis.get();
	}
	
	/******************************************************************************************************
	 * <b>Scope:</b> Global <br>
	 * <b>IMPORTANT:</b> This method has to be called before the first request is sent.<br>
	 * Set the default connect timeout used for all the requests globally.
	 ******************************************************************************************************/
	public static void defaultConnectTimeout(long millis) {
		defaultConnectTimeoutMillis.set(millis);
	}
	
	/******************************************************************************************************
	 * <b>Scope:</b> Global <br>
	 * Returns the default connect timeout for all the requests globally.
	 ******************************************************************************************************/
	public static long defaultConnectTimeout() {
		return defaultConnectTimeoutMillis.get();
	}
	
	/******************************************************************************************************
	 * <b>Scope:</b> Global <br>
	 * <b>IMPORTANT:</b> This method has to be called before the first request is sent.<br>
	 * Set the default socket timeout used for all the requests globally.
	 * 
	 ******************************************************************************************************/
	public static void defaultSocketTimeout(long millis) {
		defaultSocketTimeoutMillis.set(millis);
	}
	
	/******************************************************************************************************
	 * <b>Scope:</b> Global <br>
	 * Returns the default socket timeout for all the requests globally.
	 ******************************************************************************************************/
	public static long defaultSocketTimeout() {
		return defaultSocketTimeoutMillis.get();
	}
	
	/******************************************************************************************************
	 * <b>Scope:</b> Global <br>
	 * Set the default user agent header used for all the requests globally.
	 * 
	 ******************************************************************************************************/
	public static void defaultUserAgent(String userAgent) {
		defaultUserAgent.set(userAgent);
	}
	
	/******************************************************************************************************
	 * <b>Scope:</b> Global <br>
	 * Returns the default user agent header used for all the requests globally.
	 ******************************************************************************************************/
	public static String defaultUserAgent() {
		return defaultUserAgent.get();
	}

	/******************************************************************************************************
	 * <b>Scope:</b> Global <br>
	 * Set the maximum total number of connections in the pool.
	 * Default: 1000
	 ******************************************************************************************************/
	public static void defaultMaxTotalConnections(int max) {
		
		PFRHttp.maxTotalConnections.set(max);
		
		if(connectionManager != null) {
			connectionManager.setMaxTotal(max);
		}
	}

	/******************************************************************************************************
	 * Returns the current max total connections setting.
	 ******************************************************************************************************/
	public static int defaultMaxTotalConnections() {
		return PFRHttp.maxTotalConnections.get();
	}

	/******************************************************************************************************
	 * <b>Scope:</b> Global <br>
	 * <b>IMPORTANT:</b> Must be called before the first request is sent.<br>
	 * Set the maximum connections per route (per host).
	 * Default: 200
	 ******************************************************************************************************/
	public static void defaultMaxPerRouteConnections(int max) {
		
		PFRHttp.maxPerRouteConnections.set(max);
		
		if(connectionManager != null) {
			connectionManager.setDefaultMaxPerRoute(max);
		}
		
	}

	/******************************************************************************************************
	 * Returns the current max per-route connections setting.
	 ******************************************************************************************************/
	public static int defaultMaxPerRouteConnections() {
		return PFRHttp.maxPerRouteConnections.get();
	}

	/******************************************************************************************************
	 * <b>Scope:</b> Global <br>
	 * <b>IMPORTANT:</b> Must be called before the first request is sent.<br>
	 * If true, all SSL/TLS certificates will be trusted (insecure, for testing
	 * only).
	 * Default: false (uses JVM default trust store).
	 ******************************************************************************************************/
	public static void defaultTrustAllCertificates(boolean trustAll) {
		PFRHttp.trustAllCertificates.set(trustAll);
	}

	/******************************************************************************************************
	 * Returns the current trust-all-certificates setting.
	 ******************************************************************************************************/
	public static boolean defaultTrustAllCertificates() {
		return PFRHttp.trustAllCertificates.get();
	}

	/******************************************************************************************************
	 * <b>Scope:</b> Global <br>
	 * Enable virtual threads (JDK 21+) for HTTP request execution.
	 * When enabled, blocking HTTP calls run on virtual threads, allowing
	 * massive concurrency without OS thread overhead.
	 * Default: false
	 ******************************************************************************************************/
	public static void defaultUseVirtualThreads(boolean enable) {
		PFRHttp.useVirtualThreads.set(enable);
	}

	/******************************************************************************************************
	 * Returns whether virtual threads are enabled.
	 ******************************************************************************************************/
	public static boolean defaultUseVirtualThreads() {
		return PFRHttp.useVirtualThreads.get();
	}

	/******************************************************************************************************
	 * <b>Scope:</b> Global <br>
	 * <b>IMPORTANT:</b> Must be called before the first request is sent.<br>
	 * Enable DNS resolution time measurement as a separate HSR metric (suffix: -DNS).
	 * Default: false
	 ******************************************************************************************************/
	public static void defaultMeasureDns(boolean enable) {
		PFRHttp.measureDns.set(enable);
	}

	/******************************************************************************************************
	 * Returns whether DNS measurement is enabled.
	 ******************************************************************************************************/
	public static boolean defaultMeasureDns() {
		return PFRHttp.measureDns.get();
	}

	/******************************************************************************************************
	 * <b>Scope:</b> Global <br>
	 * <b>IMPORTANT:</b> Must be called before the first request is sent.<br>
	 * Enable TLS handshake time measurement as a separate HSR metric (suffix: -TLS).
	 * Default: false
	 ******************************************************************************************************/
	public static void defaultMeasureTls(boolean enable) {
		PFRHttp.measureTls.set(enable);
	}

	/******************************************************************************************************
	 * Returns whether TLS measurement is enabled.
	 ******************************************************************************************************/
	public static boolean defaultMeasureTls() {
		return PFRHttp.measureTls.get();
	}

	/******************************************************************************************************
	 * <b>Scope:</b> Global <br>
	 * <b>IMPORTANT:</b> Must be called before the first request is sent.<br>
	 * Enable TCP connect time measurement as a separate HSR metric (suffix: -Connect).
	 * Default: false
	 ******************************************************************************************************/
	public static void defaultMeasureConnect(boolean enable) {
		PFRHttp.measureConnect.set(enable);
	}

	/******************************************************************************************************
	 * Returns whether TCP connect measurement is enabled.
	 ******************************************************************************************************/
	public static boolean defaultMeasureConnect() {
		return PFRHttp.measureConnect.get();
	}

	
	/******************************************************************************************************
	 * <b>Scope:</b> Propagated (Inheritable Thread Local) <br>
	 * Set the default pause used for all the requests of the current user(thread).
	 ******************************************************************************************************/
	public static void defaultPause(long millis) {
		defaultPauseMillisLower.set(millis);
		defaultPauseMillisUpper.set(millis);
	}
	
	/******************************************************************************************************
	 * <b>Scope:</b> Propagated (Inheritable Thread Local) <br>
	 * Set the default pause used for all the requests of the current user(thread).
	 ******************************************************************************************************/
	public static void defaultPause(long lowerMillis, long upperMillis) {
		defaultPauseMillisLower.set(lowerMillis);
		defaultPauseMillisUpper.set(upperMillis);
	}
	
	/******************************************************************************************************
	 * <b>Scope:</b> Propagated (Inheritable Thread Local) <br>
	 * Returns the default pause lower range.
	 ******************************************************************************************************/
	public static long defaultPauseLower() {
		return defaultPauseMillisLower.get();
	}
	
	/******************************************************************************************************
	 * <b>Scope:</b> Propagated (Inheritable Thread Local) <br>
	 * Returns the default pause upper range.
	 ******************************************************************************************************/
	public static long defaultPauseUpper() {
		return defaultPauseMillisUpper.get();
	}
	
	/******************************************************************************************************
	 * 
	 ******************************************************************************************************/
	public static String encode(String toEncode) {
		
		if(toEncode == null) {
			return null;
		}
		try {
			return URLEncoder.encode(toEncode, StandardCharsets.UTF_8.toString());
		} catch (UnsupportedEncodingException e) {
			logger
				.error("Exception while encoding: "+e.getMessage(), e);	
		}
		
		return toEncode;
	}
	
	/******************************************************************************************************
	 * 
	 ******************************************************************************************************/
	public static String decode(String toDecode) {
		
		if(toDecode == null) {
			return null;
		}
		try {
			return URLDecoder.decode(toDecode, StandardCharsets.UTF_8.toString());
		} catch (UnsupportedEncodingException e) {
			logger
				.error("Exception while decoding: "+e.getMessage(), e);	
		}
		
		return toDecode;
	}
	
	
	/******************************************************************************************************
	 * Returns an encoded parameter string with leading '&'.
	 ******************************************************************************************************/
	public static String  encode(String paramName, String paramValue) {
		
		return "&" + encode(paramName) + "=" + encode(paramValue);
	}
	
	
	/******************************************************************************************************
	 * Creates a query string from the given parameters.
	 ******************************************************************************************************/
	public static String  buildQueryString(HashMap<String,String> params) {
		
		StringBuilder builder = new StringBuilder();
		
		for(Entry<String,String> param : params.entrySet()) {
			builder.append(encode(param.getKey(), param.getValue()));
		}
		
		//remove leading "&"
		return builder.substring(1);
	}
			
	
	/******************************************************************************************************
	 * Creates a url with the given parameters;
	 ******************************************************************************************************/
	public static String  buildURL(String urlWithPath, HashMap<String,String> params) {
		
		if(params != null && !params.isEmpty()) {
			
			StringBuilder builder = new StringBuilder(urlWithPath);
			
			if( !urlWithPath.endsWith("?") ) {
				builder.append("?");
			}
			
			for(Entry<String,String> param : params.entrySet()) {
				builder.append(encode(param.getKey(), param.getValue()));
			}
			
			// "?&" seems valid syntax, but some HTTP servers or apps might have troubles with that
			return builder.toString().replace("?&", "?");
		}
		
		return urlWithPath;
		
	}
	

	/******************************************************************************************************
	 * 
	 ******************************************************************************************************/
	private static PFRScriptingContext getScriptContext() {
		
		if(javascriptEngine.get() == null) {
			javascriptEngine.set( PFRScripting.createJavascriptContext().putMemberWithFunctions( new HttpPacScriptMethods()) );
			
			//------------------------------
			// Add to engine if pac loaded
			loadPacFile();
			if(proxyPAC != null) {
				//Prepend method calls with PRFHttpPacScriptMethods
				proxyPAC = HttpPacScriptMethods.preparePacScript(proxyPAC);

				PFRScriptingContext polyglot = getScriptContext();

			    polyglot.addScript("proxy.pac", proxyPAC);
			    polyglot.executeScript("FindProxyForURL('localhost:9090/test', 'localhost');");

			}
		}
		
		return javascriptEngine.get();
	}
	
	/******************************************************************************************************
	 * 
	 ******************************************************************************************************/
	private static void loadPacFile() {
		
		if(proxyPacFile.get() != null && proxyPAC == null) {
		
			String proxyPacPath = proxyPacFile.get();
			if(proxyPacPath.toLowerCase().startsWith("http")) {
				//------------------------------
				// Get PAC from URL
				PFRHttpResponse response = null;
				
				try {	
					
					response = PFRHttp.create(proxyPacPath)
							.GET()
							.send()
							;
//					HttpURLConnection connection = (HttpURLConnection)new URL(PRF.Properties.PROXY_PAC).openConnection();
//					if(connection != null) {
//						connection.setRequestMethod("GET");
//						connection.connect();
//						
//						response = instance.new PRFHttpResponse(connection);
//					}
			    
				} catch (Exception e) {
					logger
						.error("Exception occured.", e);
				} 
				
				//------------------------------
				// Cache PAC Contents
				if(response != null && response.getStatus() <= 299) {
					proxyPAC = response.getBody();
					
					if(proxyPAC == null || !proxyPAC.contains("FindProxyForURL")) {
						logger.error("The Proxy .pac-File seems not be in the expected format.");
						proxyPAC = null;
					}
				}else {
					logger.error("Error occured while retrieving .pac-File from URL. (HTTP Code: "+response.getStatus()+")");
				}
			}else {
				//------------------------------
				// Load from Disk
				InputStream in = PFRHttp.class.getClassLoader().getResourceAsStream(proxyPacPath);
				proxyPAC = HSR.Files.readContentsFromInputStream(in);
				
				if(proxyPAC == null || !proxyPAC.contains("FindProxyForURL")) {
					logger.error("The Proxy .pac-File seems not be in the expected format.");
					proxyPAC = null;
				}
			}
		}
	}

	/******************************************************************************************************
	 * Returns a String Array with URLs used as proxies. If the string is "DIRECT", it means the URL should
	 * be called without a proxy. This method will only return "DIRECT" when "cfw_proxy_enabled" is set to 
	 * false.
	 * 
	 * @param urlToCall used for the request.
	 * @return ArrayList<String> with proxy URLs
	 * @throws  
	 ******************************************************************************************************/
	public static ArrayList<PFRProxy> getProxies(String urlToCall) {
		
		//------------------------------
		// Get Proxy PAC
		//------------------------------
		loadPacFile();
		
		//------------------------------
		// Get Proxy List
		//------------------------------
		
		ArrayList<PFRProxy> proxyArray = null;
		
		if(proxyPacFile.get() != null && proxyPAC != null) {
			 URL tempURL;
			try {
				tempURL = new URL(urlToCall);
				String hostname = tempURL.getHost();
				
				if(! resolvedProxiesCache.containsKey(hostname)) {

					ArrayList<PFRProxy> proxies = new ArrayList<PFRProxy>();
					
					Value result = getScriptContext().executeScript("FindProxyForURL('"+urlToCall+"', '"+hostname+"');");
					if(result != null) {
						String[] newProxyArray = result.asString().split(";");
						
						for(String proxyDef : newProxyArray) {
							if(proxyDef.trim().isEmpty()) { continue; }
							
							String[] splitted = proxyDef.trim().split(" ");
							
							String host;
							int port;
							
							String type = splitted[0];
							if(splitted.length > 1) {
								String hostport = splitted[1];
								if(hostport.indexOf(":") != -1) {
									host = hostport.substring(0, hostport.indexOf(":"));
									String portStr = hostport.substring(hostport.indexOf(":")+1);
									try {
										port = Integer.parseInt(portStr);
									} catch (NumberFormatException e) {
										logger.error("Could not parse proxy port '{}', defaulting to 80.", portStr);
										port = 80;
									}
								}else {
									host = hostport;
									port = 80;	
								}
								
								proxies.add(new PFRProxy(type, host, port));
							}
						}							
					}
					resolvedProxiesCache.put(hostname, proxies);

				}
										
				proxyArray = resolvedProxiesCache.get(hostname);
				
			} catch (Throwable e) {
				logger.error("Resolving proxies failed.", e);
			}
		}
		
		if (proxyArray == null || proxyArray.size() == 0){
			proxyArray = new ArrayList<PFRProxy>(); 
			PFRProxy direct = new PFRProxy("DIRECT", null, 80);
			proxyArray.add(direct);
		}
		
		return proxyArray;
	}

	/******************************************************************************************************
	 * Returns a Proxy retrieved from the Proxy PAC Config.
	 * Returns null if there is no Proxy needed or proxies are disabled.
	 * 
	 ******************************************************************************************************/
	public static Proxy getProxy(String url) {
		
		if(proxyPacFile.get() != null) {
			return null;
		}else {

			ArrayList<PFRProxy> proxiesArray = getProxies(url);
			
			//--------------------------------------------------
			// Iterate PAC Proxies until address is resolved
			for(PFRProxy cfwProxy : proxiesArray) {

				if(cfwProxy.type().trim().toUpperCase().equals("DIRECT")) {
					return null;
				}else {
					
					InetSocketAddress address = new InetSocketAddress(cfwProxy.host(), cfwProxy.port());
					if(address.isUnresolved()) { 
						continue;
					}
					Proxy proxy = new Proxy(Proxy.Type.HTTP, address);
					return proxy;
				}
				
			}
			
		}
		
		return null;
		
	}
	/******************************************************************************************************
	 * Return a URL or null in case of exceptions.
	 * 
	 ******************************************************************************************************/
	public static HttpURLConnection createProxiedURLConnection(String url) {
		
		try {
			if(proxyPacFile.get() != null) {
				return (HttpURLConnection)new URL(url).openConnection();
			}else {
				HttpURLConnection proxiedConnection = null;
				ArrayList<PFRProxy> proxiesArray = getProxies(url);
				
				//--------------------------------------------------
				// Return direct if no proxies were returned by PAC
				if(proxiesArray.isEmpty()) {
					return (HttpURLConnection)new URL(url).openConnection();
				}
				
				//--------------------------------------------------
				// Iterate PAC Proxies until address is resolved
				for(PFRProxy cfwProxy : proxiesArray) {

					if(cfwProxy.type().trim().toUpperCase().equals("DIRECT")) {
						proxiedConnection = (HttpURLConnection)new URL(url).openConnection();
					}else {
						
						InetSocketAddress address = new InetSocketAddress(cfwProxy.host(), cfwProxy.port());
						if(address.isUnresolved()) { 
							continue;
						};
						Proxy proxy = new Proxy(Proxy.Type.HTTP, address);

						proxiedConnection = (HttpURLConnection)new URL(url).openConnection(proxy);
					}
					return proxiedConnection;
				}
				
				//-------------------------------------
				// None of the addresses were resolved
				logger
					.warn("The proxy addresses couldn't be resolved.");
			}
		} catch (MalformedURLException e) {
			logger
				.error("The URL is malformed.", e);
			
		} catch (IOException e) {
			logger
				.error("An IO error occured.", e);
		}
		
		return null;
		
	}
	
	/******************************************************************************************************
	 * If proxy is enabled, adds a HttpHost to the client.
	 * 
	 * @param clientBuilder the client that should get a proxy
	 * @param targetURL the URL that should be called over a proxy (not the url of the proxy host)
	 * 
	 ******************************************************************************************************/
	public static void httpClientAddProxy(HttpClientBuilder clientBuilder) {
		
		//--------------------------------
		// Check Do nothing
		if(proxyPacFile.get() == null) {
			return; 
		}
		
	    clientBuilder.setRoutePlanner(new HttpRoutePlanner() {
			
			@Override
			public HttpRoute determineRoute(HttpHost target, HttpContext context) throws HttpException {
				
				//----------------------------------
				// Build full target URL from HttpHost
				String scheme = target.getSchemeName();
				String host = target.getHostName();
				int port = target.getPort();
				boolean isSecure = "https".equalsIgnoreCase(scheme);
				
				StringBuilder urlBuilder = new StringBuilder();
				urlBuilder.append(scheme).append("://").append(host);
				
				if (port > 0) {
				    urlBuilder.append(":").append(port);
				}
				
				String url = urlBuilder.toString();
                
				//----------------------------------
				// Doing this because someone had the
				// grandiose idea to throw an exception
				// when the port is not set(-1).
				int finalPort = port;
				if (port <= 0) {
					if(isSecure) {	finalPort = 443; }
					else		 {	finalPort = 80; }
						
				}
				HttpHost finalTarget = new HttpHost(scheme, host, finalPort);
				
				//----------------------------------
				// Check has Proxy PAC
				if(proxyPacFile.get() == null) {
					return new HttpRoute(finalTarget); 
				}
				
				//----------------------------------
				// Resolve proxies for THIS request
				ArrayList<PFRProxy> proxiesArray = getProxies(url);

				//----------------------------------
				// No proxy &rarr; DIRECT
				if (proxiesArray == null || proxiesArray.isEmpty()) {
				    return new HttpRoute(finalTarget);
				}
				
				//--------------------------------------------------
				// Iterate PAC Proxies until address is resolved
				for(PFRProxy cfwProxy : proxiesArray) {
					
					if(cfwProxy.type().trim().toUpperCase().equals("DIRECT")) {
						return new HttpRoute(finalTarget); // no proxy required
					}else {
						
						InetSocketAddress address = new InetSocketAddress(cfwProxy.host(), cfwProxy.port());
						if(address.isUnresolved()) { 
							continue;
						};

						HttpHost proxy = new HttpHost(cfwProxy.host(), cfwProxy.port());

						return new HttpRoute(finalTarget, null, proxy, isSecure);
					}
				}
				
				//-------------------------------------
				// None of the addresses were resolved
				logger.warn("The proxy addresses couldn't be resolved.");
				
				return new HttpRoute(target);
			}
		});
	    
	}
		
	/******************************************************************************************************
	 * Adds a basic Authorization header to the list of headers
	 * @param headers used for the request.
	 * @param basicUsername
	 * @param basicPassword
	 ******************************************************************************************************/
	public static void addBasicAuthorizationHeader(HashMap<String, String> headers, String basicUsername, String basicPassword) {
		
		String valueToEncode = basicUsername + ":" + basicPassword;
		headers.put("Authorization", "Basic "+Base64.getEncoder().encodeToString(valueToEncode.getBytes()));	    	
	}
	
	/******************************************************************************************************
	 * Clears the cookies for the current user thread.
	 ******************************************************************************************************/
	public static void addCookie(BasicClientCookie cookie) {
		cookieStore.get().addCookie(cookie);
	}
	
	/******************************************************************************************************
	 * Returns an immutable array of {@link Cookie cookies} that this HTTP
	 * state currently contains.
	 * 
	 * @return list of cookies, baked well without sugar
	 ******************************************************************************************************/
	public static List<Cookie> getCookies() {
		return cookieStore.get().getCookies();
	}
	
	/******************************************************************************************************
	 * Clears the cookies for the current user thread.
	 ******************************************************************************************************/
	public static void clearCookies() {
		cookieStore.get().clear();
	}
	
	/******************************************************************************************************
	 * Clears the cookies that have expired for the current user thread.
	 ******************************************************************************************************/
	public static void clearCookiesExpired() {
		cookieStore.get().clearExpired(Instant.now());
	}
	
	/******************************************************************************************************
	 * Returns the connection manager used for all the connections.
	 * @return 
	 * 
	 ******************************************************************************************************/
	public static PoolingHttpClientConnectionManager getConnectionManager() {
		
		synchronized (logger) {
			
			if(connectionManager == null) {

				try{
					connectionManager = new PoolingHttpClientConnectionManager(getSocketFactoryRegistry());
				}catch(Exception e) {
					logger.warn("Error initializing connection manager with SocketFactoryRegistry. Fallback to default factory(= no measurement of TLS, DNS or Connect metrics).", e);
					connectionManager = new PoolingHttpClientConnectionManager();
				}
				
				connectionManager.setMaxTotal(PFRHttp.maxTotalConnections.get());
				connectionManager.setDefaultMaxPerRoute(PFRHttp.maxPerRouteConnections.get());

				
				connectionManager.setDefaultConnectionConfig(
						ConnectionConfig.custom()
					        .setConnectTimeout( Timeout.ofMilliseconds(PFRHttp.defaultConnectTimeout()) )
					        .setSocketTimeout(Timeout.ofMilliseconds(PFRHttp.defaultSocketTimeout()) )
					        .build()
				        );
				
				connectionManager.setDefaultSocketConfig(SocketConfig.custom()
						    .setSoKeepAlive(true)
						    .setTcpNoDelay(true)
						    .setSoLinger(TimeValue.ofSeconds(5))
						    .build()
					    );
			}
		}
		return connectionManager;
	}
	
	/******************************************************************************************************
	 * Creates a request builder for chained building of requests.
	 * @param url used for the request.
	 ******************************************************************************************************/
	public static PFRHttpRequestBuilder create(String url) {
		return new PFRHttpRequestBuilder(url);	    	
	}
	
	/******************************************************************************************************
	 * Creates a request builder for chained building of requests.
	 * @param url used for the request.
	 ******************************************************************************************************/
	public static PFRHttpRequestBuilder create(String metric, String url) {
		return new PFRHttpRequestBuilder(metric, url);	    	
	}
	

	/**************************************************************************************
	 * Loads the keystore, caches it and then returns the cached instance.
	 * 
	 * @return keystore or null if it could not be loaded 
	 **************************************************************************************/
    private static KeyStore getCachedKeyStore() {
    	
    	if(cachedKeyStore == null && keystorePath.get() != null) {

	    	//-------------------------------------
	    	// Settings
	    	String path = keystorePath.get(); // or .p12
	    	String password = keystorePW.get();
	
	    	String keystoreType = "PKCS12";
			if(path.endsWith("jks")) {
				keystoreType = "JKS";
			}
	    	
	    	//-------------------------------------
	    	// Load Keystore
			try (FileInputStream keyStoreStream = new FileInputStream(path)) { 
				cachedKeyStore = KeyStore.getInstance(keystoreType); // or "PKCS12"
				cachedKeyStore.load(keyStoreStream, password.toCharArray());
			    
			}catch (Exception e) {
				logger.error("Error loading keystore: "+e.getMessage(), e);
			}
    	}
    	
    	return cachedKeyStore;
	    
    }
    
	/**************************************************************************************
	 * Set SSL Context
	 * @throws KeyStoreException 
	 * @throws NoSuchAlgorithmException 
	 * @throws KeyManagementException 
	 **************************************************************************************/
    private static void addKeyStore(SSLContextBuilder builder) throws Exception {
    	
    	if(keystorePath.get() != null) {
	    	//-------------------------------------
	    	// Initialize
	    	KeyStore keyStore = getCachedKeyStore();
	    	String keyManagerPW = keystoreManagerPW.get();
			
		    //-------------------------------------
	    	// Add to Context Builder
	    	if(keyStore != null) {
			    if( !Strings.isNullOrEmpty(keyManagerPW) ) {
			    	builder.loadKeyMaterial(keyStore, keyManagerPW.toCharArray());
			    }else {
			    	builder.loadKeyMaterial(keyStore, null);
			    }
	    	}
    	}

    }
    
    
    
    
	/**************************************************************************************
	 * Set SSL Context
	 * 
	 **************************************************************************************/
	@SuppressWarnings("deprecation")
	private static Registry<ConnectionSocketFactory> getSocketFactoryRegistry() throws Exception {
		
		//=====================================================
		// Initialize Connection Manager
		//=====================================================

		// -------------------------------
		// Create SSL Context Builder
		final SSLContextBuilder sslContextBuilder;

		if (trustAllCertificates.get()) {
			// Trust anything
			final TrustStrategy acceptingTrustStrategy = (cert, authType) -> true;
			sslContextBuilder = SSLContexts.custom().loadTrustMaterial(null, acceptingTrustStrategy);
		} else {
			// Use JVM default trust store
			sslContextBuilder = SSLContexts.custom();
		}

		addKeyStore(sslContextBuilder);

		// -------------------------------
		// Connection Factory
		final SSLConnectionSocketFactory sslsf;
		if (trustAllCertificates.get()) {
			sslsf = new SSLConnectionSocketFactory(sslContextBuilder.build(), NoopHostnameVerifier.INSTANCE);
		} else {
			sslsf = new SSLConnectionSocketFactory(sslContextBuilder.build());
		}

		final Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder
				.<ConnectionSocketFactory>create()
				.register("https", new PFRSocketFactory.PFRLayeredSocketFactory(sslsf))
				.register("http", new PFRSocketFactory(new PlainConnectionSocketFactory()))
				.build();

		return socketFactoryRegistry;
	}
	
	protected record PFRProxy(String type, String host, int port) { }
}
