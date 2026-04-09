package com.performetriks.performator.http;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.classic.methods.HttpOptions;
import org.apache.hc.client5.http.classic.methods.HttpPatch;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.classic.methods.HttpTrace;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;

import com.google.common.base.Strings;
import com.performetriks.performator.http.PFRHttp.PFRHttpAuthMethod;
import com.performetriks.performator.http.PFRHttp.PFRHttpSection;
import com.xresch.hsr.base.HSR;
import com.xresch.xrutils.data.ByteSize;

/***************************************************************************
 * 
 * Copyright Owner: Performetriks GmbH, Switzerland
 * License: Eclipse Public License v2.0
 * 
 * @author Reto Scheiwiller
 * 
 ***************************************************************************/
public class PFRHttpRequestBuilder {

	protected String metricName = null;
	protected String URL = null;
	protected String method = "GET";
	protected HashMap<String, String> params = null;
	protected HashMap<String, String> lowercaseHeaders = null;
	protected String body = null;
	protected long sla = -1;
	
	protected boolean autoFailOnHTTPErrors = false;
	protected boolean autoDecompressResponse = true;

	protected long pauseMillisLower = PFRHttp.defaultPauseLower();
	protected long pauseMillisUpper = PFRHttp.defaultPauseUpper();
	
	protected ArrayList<PFRHttpCheck> checksList = new ArrayList<>();
		
	protected ArrayList<Range> ranges = null;
	protected ByteSize measuredSize = null;
	
	protected Charset bodyCharset = PFRHttp.defaultBodyCharset();
	protected boolean throwOnFail = PFRHttp.defaultThrowOnFail();
	
	protected long responseTimeoutMillis = PFRHttp.defaultResponseTimeout();
	
	private static ThreadLocal<CloseableHttpClient> httpClient = new ThreadLocal<>();
	
	/***************************************************************************
	 * @param url used for the request.
	 ***************************************************************************/
	public PFRHttpRequestBuilder(String url) {
		this.URL = url;
	}
	
	/***************************************************************************
	 * @param url used for the request.
	 ***************************************************************************/
	public PFRHttpRequestBuilder(String metric, String url) {
		this.metricName = metric;
		this.URL = url;
	}
	
	/***************************************************************************
	 * 
	 ***************************************************************************/
	public PFRHttpRequestBuilder metricName(String metric) {
		this.metricName = metric;
		return this;
	}
	
	/***************************************************************************
	 * 
	 ***************************************************************************/
	public PFRHttpRequestBuilder URL(String url) {
		this.URL = url;
		return this;
	}
	
	/***************************************************************************
	 * 
	 ***************************************************************************/
	public PFRHttpRequestBuilder GET() {
		this.method = "GET";
		return this;
	}
	
	/***************************************************************************
	 * 
	 ***************************************************************************/
	public PFRHttpRequestBuilder POST() {
		this.method = "POST";
		return this;
	}
	
	/***************************************************************************
	 * 
	 ***************************************************************************/
	public PFRHttpRequestBuilder PUT() {
		this.method = "PUT";
		return this;
	}
	
	/***************************************************************************
	 * 
	 ***************************************************************************/
	public PFRHttpRequestBuilder DELETE() {
		this.method = "DELETE";
		return this;
	}
	
	/***************************************************************************
	 * 
	 ***************************************************************************/
	public PFRHttpRequestBuilder OPTIONS() {
		this.method = "OPTIONS";
		return this;
	}
	
	/***************************************************************************
	 * 
	 ***************************************************************************/
	public PFRHttpRequestBuilder HEAD() {
		this.method = "HEAD";
		return this;
	}
	
	/***************************************************************************
	 * 
	 ***************************************************************************/
	public PFRHttpRequestBuilder PATCH() {
		this.method = "PATCH";
		return this;
	}
	
	/***************************************************************************
	 * 
	 ***************************************************************************/
	public PFRHttpRequestBuilder TRACE() {
		this.method = "TRACE";
		return this;
	}
	
	/***************************************************************************
	 * Set a SLA for the response. 
	 ***************************************************************************/
	public PFRHttpRequestBuilder sla(long sla) {
		this.sla = sla;
		return this;
	}
	
	/***************************************************************************
	 * If set to true, requests with HTTP status code => 400 will be considered
	 * failing.
	 * Default: false
	 ***************************************************************************/
	public PFRHttpRequestBuilder autoFailOnHTTPErrors(boolean fail) {
		this.autoFailOnHTTPErrors = fail;
		return this;
	}
	
	/***************************************************************************
	 * Is decompress responses automatically.
	 * Default: true
	 ***************************************************************************/
	public PFRHttpRequestBuilder autoDecompressResponse(boolean decompress) {
		this.autoDecompressResponse = decompress;
		return this;
	}
	
	/***************************************************************************
	 * The pause that should be added before continuing with next request.
	 * Value is in milliseconds. 
	 ***************************************************************************/
	public PFRHttpRequestBuilder pause(long millis) {
		this.pauseMillisLower = millis;
		this.pauseMillisUpper = millis;
		return this;
	}
	
	/***************************************************************************
	 * The pause that should be added before continuing with next request.
	 * Random values between lower and upper milliseconds.
	 ***************************************************************************/
	public PFRHttpRequestBuilder pause(long lowerMillis, long upperMillis) {
		this.pauseMillisLower = lowerMillis;
		this.pauseMillisUpper = upperMillis;
		return this;
	}
	
	/***************************************************************************
	 * If set to true, requests that fail will throw a ResponseFailedException.
	 * This value overrides the global setting PFRHttp.defaultThrowOnFail().
	 ***************************************************************************/
	public PFRHttpRequestBuilder throwOnFail(boolean throwOnFail) {
		this.throwOnFail = throwOnFail;
		return this;
	}
	
	/***************************************************************************
	 * Sets the response timeout for the current request.
	 * Values is in milliseconds. 
	 ***************************************************************************/
	public PFRHttpRequestBuilder responseTimeout(long millis) {
		this.responseTimeoutMillis = millis;
		return this;
	}
	
	/***************************************************************************
	 * Adds parameters to the request.
	 * If method is POST the parameters will be added to the body, otherwise
	 * the parameters will be added to the query of the URL.
	 * @param parametersKeyValue params at odd positions are keys, params at even positions are values
	 ***************************************************************************/
	public PFRHttpRequestBuilder params(String... parametersKeyValue) {
		
		if(params == null) { params = new HashMap<>(); }
		
		for(int i = 0; i < parametersKeyValue.length; i += 2) {
			params.put(parametersKeyValue[i], parametersKeyValue[i+1]);
		}
		
		return this;
	}
	
	/***************************************************************************
	 * Adds parameters to the request.
	 * If method is POST the parameters will be added to the body, otherwise
	 * the parameters will be added to the query of the URL.
	 * @param map of values
	 ***************************************************************************/
	public PFRHttpRequestBuilder params(HashMap<String, String> map) {
		
		if(map == null) { return this; }
		
		if(params == null) { params = new HashMap<>(); }
		params.putAll(map);
		
		return this;
	}
	
	/***************************************************************************
	 * Adds headers to the request.
	 * @param headersKeyValue params at odd positions are keys, params at even positions are values
	 ***************************************************************************/
	public PFRHttpRequestBuilder headers(String... headersKeyValue) {
		
		if(lowercaseHeaders == null) { lowercaseHeaders = new HashMap<>(); }
		
		for(int i = 0; i < headersKeyValue.length; i += 2) {
			lowercaseHeaders.put(headersKeyValue[i].toLowerCase(), headersKeyValue[i+1]);
		}
		
		return this;
	}
	
	/***************************************************************************
	 * Adds headers to the request.
	 * @param map of values
	 ***************************************************************************/
	public PFRHttpRequestBuilder headers(HashMap<String, String> map) {
		
		if(map == null) { return this; }
		if(lowercaseHeaders == null) { lowercaseHeaders = new HashMap<>(); }
		
		for(Entry<String, String> entry : map.entrySet()) {
			lowercaseHeaders.put(entry.getKey().toLowerCase(), entry.getValue());
		}
		
		return this;
	}
	
	/***************************************************************************
	 * Set the body of the request.
	 * @param body
	 ***************************************************************************/
	public PFRHttpRequestBuilder body(String body) {
		this.body = body;
		return this;
	}
	
	/***************************************************************************
	 * Set the charset for the body.
	 * Default: PFRHttp.defaultBodyCharset() 
	 ***************************************************************************/
	public PFRHttpRequestBuilder bodyCharset(Charset charset) {
		this.bodyCharset = charset;
		return this;
	}
	
	/***************************************************************************
	 * Adds a check which will verify the response.
	 * Checks can be found in PFRHttpCheck class.
	 * @param check instance of check
	 ***************************************************************************/
	public PFRHttpRequestBuilder check(PFRHttpCheck check) {
		this.checksList.add(check);
		return this;
	}
	
	/***************************************************************************
	 * Adds a range to the request. The range value will be put into buckets
	 * based on the initial value.
	 * @param rangeValue value to put into bucket
	 * @param rangeInitial initial value
	 ***************************************************************************/
	public PFRHttpRequestBuilder measureRange(int rangeValue, int rangeInitial) {
		return measureRange(null, rangeValue, rangeInitial);
	}
	
	/***************************************************************************
	 * Adds a range to the request. The range value will be put into buckets
	 * based on the initial value.
	 * @param suffix to add to the metric name
	 * @param rangeValue value to put into bucket
	 * @param rangeInitial initial value
	 ***************************************************************************/
	public PFRHttpRequestBuilder measureRange(String suffix, int rangeValue, int rangeInitial) {
		if(ranges == null) { ranges = new ArrayList<>(); }
		ranges.add(new Range(suffix, rangeValue, rangeInitial));
		return this;
	}
	
	/***************************************************************************
	 * Enable measuring the body size of the response.
	 * By default size is not measured. 
	 ***************************************************************************/
	public PFRHttpRequestBuilder measureSize() {
		this.measuredSize = ByteSize.B;
		return this;
	}
	
	/***************************************************************************
	 * Enable measuring the body size of the response in specific unit.
	 * By default size is not measured. 
	 ***************************************************************************/
	public PFRHttpRequestBuilder measureSize(ByteSize byteSize) {
		this.measuredSize = byteSize;
		return this;
	}
	
	/***************************************************************************
	 * 
	 ***************************************************************************/
	protected record Range(String suffix, int rangeValue, int rangeInitial) {}
	
	/***************************************************************************
	 * Returns the HttpClient used for requests.
	 ***************************************************************************/
	private CloseableHttpClient getClient() {
		
		if(httpClient.get() == null) {
			
			HttpClientBuilder builder = HttpClients.custom();
			builder.setConnectionManager(PFRHttp.getConnectionManager());
			//---------------------------
			// Proxy Handling
			PFRHttp.httpClientAddProxy(builder);
			
			//---------------------------
			// Cookie Handling
			builder.setDefaultCookieStore(PFRHttp.cookieStore.get());
			
			//---------------------------
			// Build Client
			httpClient.set( builder.build() );
		}
		
		return httpClient.get();
	}
	
	/***************************************************************************
	 * Build and send the request asynchronously. Returns a 
	 * CompletableFuture<PRFHttpResponse>.
	 ***************************************************************************/
	public CompletableFuture<PFRHttpResponse> sendAsync() {
		if (PFRHttp.defaultUseVirtualThreads()) {
			try {
				java.lang.reflect.Method m = Executors.class.getMethod("newVirtualThreadPerTaskExecutor");
				ExecutorService virtualExec = (ExecutorService) m.invoke(null);
				return CompletableFuture.supplyAsync(this::send, virtualExec);
			} catch (Exception e) {
				PFRHttp.logger.warn("Virtual Threads are not supported on this JVM. Falling back to standard ForkJoinPool.", e);
				return CompletableFuture.supplyAsync(this::send);
			}
		} else {
			return CompletableFuture.supplyAsync(this::send);
		}
	}

	/***************************************************************************
	 * Build and send the request. Returns a 
	 * PRFHttpResponse or null in case of errors.
	 ***************************************************************************/
	public PFRHttpResponse send() {
		
		try {
			
			//----------------------------------
			// Build Request Method 
			HttpUriRequestBase requestBase = null;
			switch(method.toUpperCase()) {
				case "GET":		requestBase = new HttpGet(buildURLwithParams());		break;
				case "POST":	requestBase = new HttpPost(URL);						break;
				case "PUT":		requestBase = new HttpPut(URL);							break;
				case "DELETE":	requestBase = new HttpDelete(buildURLwithParams());		break;
				case "OPTIONS":	requestBase = new HttpOptions(buildURLwithParams());	break;
				case "HEAD":	requestBase = new HttpHead(buildURLwithParams());		break;
				case "PATCH":	requestBase = new HttpPatch(URL);						break;
				case "TRACE":	requestBase = new HttpTrace(buildURLwithParams());		break;
				
				default:
					PFRHttp.logger.error("HTTP Method is not supported: "+method);
					return null;
			}
			
	        //----------------------------------
	        // Retrieve HTTP Context (cached per thread)
	        HttpClientContext context = PFRHttp.httpContextStore.get();
			
		    //----------------------------------
			// Set Auth mechanism
			if(lowercaseHeaders != null && lowercaseHeaders.containsKey("prf-auth-method")) {
				setAuthMechanism(context);
			}
			
			//----------------------------------
			// Set Request Config
			RequestConfig.Builder requestConfigBuilder = RequestConfig.custom()
	            .setResponseTimeout( Timeout.ofMilliseconds(responseTimeoutMillis) )
	            ;
			
			if(!autoDecompressResponse) {
				requestConfigBuilder.setContentCompressionEnabled(false);
			}

			requestBase.setConfig(requestConfigBuilder.build());
			
			//----------------------------------
			// Add Default Headers
			HashMap<String, String> defaults = PFRHttp.defaultHeaders();
			if(defaults != null) {
				for(Entry<String, String> entry : defaults.entrySet()) {
					requestBase.addHeader(entry.getKey(), entry.getValue());
				}
			}
			
			//----------------------------------
			// Add User Agent
			if(PFRHttp.defaultUserAgent() != null 
			&& (lowercaseHeaders == null || !lowercaseHeaders.containsKey("user-agent")) 
			) {
				requestBase.addHeader("User-Agent", PFRHttp.defaultUserAgent());
			}
			
			//----------------------------------
			// Add Headers
			if(lowercaseHeaders != null) {
				for(Entry<String, String> entry : lowercaseHeaders.entrySet()) {
					requestBase.addHeader(entry.getKey(), entry.getValue());
				}
			}
			
			//----------------------------------
			// Body 
			if(body != null) {
				
				ContentType contentType = null;
				Header contentTypeHeader = requestBase.getFirstHeader("Content-Type");
				if(contentTypeHeader != null) {
					contentType = ContentType.parse(contentTypeHeader.getValue());
				}
				
				requestBase.setEntity(new StringEntity(body, contentType));
				
			}else if(method.equalsIgnoreCase("POST") && params != null && !params.isEmpty()) {
				//----------------------------------
				// POST Params
				requestBase.setEntity(new StringEntity(PFRHttp.buildQueryString(params), ContentType.APPLICATION_FORM_URLENCODED));
			}
			
			//-----------------------------------
			// Connect and create response

			PFRHttp.currentMetricName(metricName);
			if (context != null) {
				context.setAttribute("pfr.metric", metricName);
			}

			try {
				PFRHttpResponse response = new PFRHttpResponse(this, getClient(), requestBase, context);
				return response;
			} finally {
				PFRHttp.currentMetricName(null);
			}
			
		
		} catch (Throwable e) { 
			PFRHttp.logger.error("Exception occured while building and sending request: "+e.getMessage(), e);
		}
		
		return new PFRHttpResponse(this);
	}
	
	/***************************************************************************
	 * 
	 ***************************************************************************/
	private void setAuthMechanism(HttpClientContext context) {
		
		PFRHttpAuthMethod authMethod = PFRHttpAuthMethod.valueOf(lowercaseHeaders.remove("prf-auth-method").toUpperCase());
		String username = lowercaseHeaders.remove("prf-auth-user");
		String password = lowercaseHeaders.remove("prf-auth-password");
		
		switch (authMethod) {
			case BASIC_HEADER:
				PFRHttp.addBasicAuthorizationHeader(lowercaseHeaders, username, password);
				break;
				
			case BASIC:
			case DIGEST:
			case NTLM:
			case KERBEROS:
				CredentialsProvider credsProvider = new BasicCredentialsProvider();
				credsProvider.setCredentials(new AuthScope(null, -1), new UsernamePasswordCredentials(username, password.toCharArray()));
				context.setCredentialsProvider(credsProvider);
				break;

			default:
				break;
		}
	}

	/***************************************************************************
	 * 
	 ***************************************************************************/
	protected String buildURLwithParams() {
		
		if(method.equalsIgnoreCase("POST") || params == null || params.isEmpty()) {
			return URL;
		}
		
		return PFRHttp.buildURL(URL, params);
	}
	
	/***************************************************************************
	 * Returns the metric name.
	 ***************************************************************************/
	public String getMetricName() {
		return (metricName != null) ? metricName : URL;
	}
	
	/***************************************************************************
	 * Get the logic for extracting body. 
	 * @return extracting as String or null
	 ***************************************************************************/
	public String body() {
		return body;
	}
	
}