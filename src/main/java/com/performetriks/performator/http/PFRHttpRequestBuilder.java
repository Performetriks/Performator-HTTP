package com.performetriks.performator.http;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.auth.AuthSchemeFactory;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.KerberosConfig;
import org.apache.hc.client5.http.auth.KerberosCredentials;
import org.apache.hc.client5.http.auth.NTCredentials;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.auth.BasicScheme;
import org.apache.hc.client5.http.impl.auth.BasicSchemeFactory;
import org.apache.hc.client5.http.impl.auth.CredentialsProviderBuilder;
import org.apache.hc.client5.http.impl.auth.DigestScheme;
import org.apache.hc.client5.http.impl.auth.DigestSchemeFactory;
import org.apache.hc.client5.http.impl.auth.KerberosScheme;
import org.apache.hc.client5.http.impl.auth.KerberosSchemeFactory;
import org.apache.hc.client5.http.impl.auth.NTLMScheme;
import org.apache.hc.client5.http.impl.auth.NTLMSchemeFactory;
import org.apache.hc.client5.http.impl.auth.SPNegoSchemeFactory;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.protocol.RedirectStrategy;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;

import com.performetriks.performator.http.PFRHttp.PFRHttpAuthMethod;
import com.xresch.hsr.stats.HSRSLA;
import com.xresch.hsr.utils.ByteSize;
import com.xresch.hsr.utils.HSRText.CheckType;
import com.xresch.hsr.utils.HSRTime.HSRTimeUnit;

/*******************************************************************************************************
 * 
 * Copyright Owner: Performetriks GmbH, Switzerland
 * License: Eclipse Public License v2.0
 * 
 * @author Reto Scheiwiller
 * 
 *******************************************************************************************************/
public class PFRHttpRequestBuilder {
	
	private static final String HEADER_CONTENT_TYPE = "Content-Type";
	private PFRHttpAuthMethod authMethod = PFRHttpAuthMethod.BASIC;
	private String username = null;
	private char[] pwdArray = null;
	
	boolean autoFailOnHTTPErrors = true;
	boolean disableFollowRedirects = false;
	ByteSize measuredSize = null;
	
	String metricName = null;
	String method = "GET";
	String URL = null;
	String body = null;
	
	String requestBodyContentType = "plain/text; charset=UTF-8";
	private boolean autoCloseClient = true;
	
	long responseTimeoutMillis = PFRHttp.defaultResponseTimeout(); 
	long pauseMillisLower = PFRHttp.defaultPauseLower(); 
	long pauseMillisUpper = PFRHttp.defaultPauseUpper(); 
	
	HSRSLA sla = null;
	ArrayList<PFRHttpCheck> checksList = new ArrayList<>();
	
	HashMap<String, String> params = new HashMap<>();
	HashMap<String, String> headers = new HashMap<>();
	
	public PFRHttpRequestBuilder(String urlNoParams) {
		this.URL = urlNoParams;
	}
	
	public PFRHttpRequestBuilder(String metricName, String urlNoParams) {
		this.metricName = metricName;
		this.URL = urlNoParams;
	}
	
	/***************************************************************************
	 * Set method to GET
	 ***************************************************************************/
	public PFRHttpRequestBuilder GET() {
		method = "GET";
		return this;
	}
	
	/***************************************************************************
	 * Set method to POST
	 ***************************************************************************/
	public PFRHttpRequestBuilder POST() {
		method = "POST";
		return this;
	}
	
	/***************************************************************************
	 * Add a parameter to the request.
	 ***************************************************************************/
	public PFRHttpRequestBuilder param(String name, String value) {
		params.put(name, value);
		return this;
	}
	
	
	/***************************************************************************
	 * Adds a map of parameters
	 ***************************************************************************/
	public PFRHttpRequestBuilder params(Map<String, String> paramsMap) {
		
		if(paramsMap == null) { return this; }
		
		this.params.putAll(paramsMap);
		return this;
		
	}
	
	/***************************************************************************
	 * Add a header
	 ***************************************************************************/
	public PFRHttpRequestBuilder header(String name, String value) {
		headers.put(name, value);
		return this;
	}
	
	
	/***************************************************************************
	 * Adds a map of headers
	 ***************************************************************************/
	public PFRHttpRequestBuilder headers(Map<String, String> headerMap) {
		
		if(headerMap == null) { return this; }
		
		this.headers.putAll(headerMap);
		return this;
		
	}
	
	/***************************************************************************
	 * Toggles the checks to allow HTTP statuses >= 400 without failing 
	 * automatically.
	 * @return instance for chaining
	 ***************************************************************************/
	public PFRHttpRequestBuilder allowHTTPErrors() {
		this.autoFailOnHTTPErrors = false;
		return this;
	}
	
	/***************************************************************************
	 * Disables the automatic following of HTTP Redirects.
	 * Might be useful in certain cases.
	 * @return instance for chaining.
	 ***************************************************************************/
	public PFRHttpRequestBuilder disableFollowRedirects() {
		this.disableFollowRedirects = true;
		return this;
	}
	
	/***************************************************************************
	 * Toggles the measurement of response size after unzipping.
	 * This will measure the actual size of the received content in UTF8 encoding. 
	 * 
	 * @param size that should be reported
	 ***************************************************************************/
	public PFRHttpRequestBuilder measureSize(ByteSize size) {
		this.measuredSize = size;
		return this;
	}
	
	/***************************************************************************
	 * Set an SLA for this request.
	 ***************************************************************************/
	public PFRHttpRequestBuilder sla(HSRSLA sla) {
		this.sla = sla;
		return this;
	}
	
	/***************************************************************************
	 * Toggle autoCloseClient
	 ***************************************************************************/
	public PFRHttpRequestBuilder autoCloseClient(boolean autoCloseClient) {
		this.autoCloseClient = autoCloseClient;
		return this;
	}
	
	/***************************************************************************
	 * Get autoCloseClient
	 ***************************************************************************/
	public boolean autoCloseClient() {
		return this.autoCloseClient;
	}
		
	/***************************************************************************
	 * Set Basic Authentication
	 ***************************************************************************/
	public PFRHttpRequestBuilder setAuthCredentialsBasic(String username, String password) {
		return setAuthCredentials(PFRHttpAuthMethod.BASIC, username, password);
	}
	
	/***************************************************************************
	 * Set authentication credentials
	 ***************************************************************************/
	public PFRHttpRequestBuilder setAuthCredentials(PFRHttpAuthMethod authMethod, String username, String password) {
	
		this.authMethod = (authMethod != null ) ? authMethod : PFRHttpAuthMethod.BASIC;
		this.username = username;
		this.pwdArray = (password != null ) ? password.toCharArray() : "".toCharArray();

		return this;
	}
	
	/***************************************************************************
	 * Add a request Body
	 ***************************************************************************/
	public PFRHttpRequestBuilder body(String content) {
		this.body = content;
		return this;
	}
	
	/***************************************************************************
	 * Add a request Body
	 ***************************************************************************/
	public PFRHttpRequestBuilder body(String contentType, String content) {
		this.requestBodyContentType = contentType;
		this.header(HEADER_CONTENT_TYPE, contentType);
		this.body = content;
		return this;
	}
	
	/***************************************************************************
	 * Add a request Body in JSON format UTF-8 encoding
	 ***************************************************************************/
	public PFRHttpRequestBuilder bodyJSON(String content) {
		return this.body("application/json; charset=UTF-8", content);
	}
	
	/***************************************************************************
	 * Add a response timeout.
	 ***************************************************************************/
	public PFRHttpRequestBuilder timeout(long responseTimeoutMillis) {
		this.responseTimeoutMillis = responseTimeoutMillis;
		return this;
	}
	
	/***************************************************************************
	 * Add a pause after the response was received.
	 ***************************************************************************/
	public PFRHttpRequestBuilder pause(long pauseMillis) {
		this.pauseMillisLower = pauseMillis;
		this.pauseMillisUpper = pauseMillis;
		return this;
	}
	
	/***************************************************************************
	 * Add a random pause in milliseconds that lies in between the specified
	 * range.
	 ***************************************************************************/
	public PFRHttpRequestBuilder pause(long lowerMillis, long upperMillis) {
		
		if(lowerMillis <= upperMillis) {
			this.pauseMillisLower = lowerMillis;
			this.pauseMillisUpper = upperMillis;
		}else {
			this.pauseMillisLower = upperMillis;
			this.pauseMillisUpper = lowerMillis;
		}
		return this;
	}
	
	/***************************************************************************
	 * Add a request Body in JSON format UTF-8 encoding
	 ***************************************************************************/
	public String buildURLwithParams() {
		return  PFRHttp.buildURL(URL, params);
	}
	
	/***************************************************************************
	 * Add a check to the list of checks.
	 * @param check the check to be added
	 * @return instance of chaining
	 ***************************************************************************/
	public PFRHttpRequestBuilder check(PFRHttpCheck check) {
		checksList.add(check);
		return this;
	}
	
	/***************************************************************************
	 * Add a check to the list of checks. 
	 * @param type the check type that should be used for this check
	 * @param containsThis the string to be checked
	 * @param appendLogDetails retrieved from PFRContext 
	 * @param customMessage a custom message instead of the default message
	 ***************************************************************************/
	public PFRHttpRequestBuilder checkBody(CheckType type, String containsThis, boolean appendLogDetails, String customMessage) { 
			this.check(
					new PFRHttpCheck(type)
						.checkBody(containsThis)
						.appendLogDetails(appendLogDetails) 
						.messageOnFail(customMessage)
				); 	
			return this; 
	}
	
	/***************************************************************************
	 * Add a check to the list of checks. 
	 * @param containsThis the string to be checked
	 * @param appendLogDetails retrieved from PFRContext 
	 * @param customMessage a custom message instead of the default message
	 ***************************************************************************/ 
	public PFRHttpRequestBuilder checkBodyContains(String containsThis, boolean appendLogDetails, String customMessage) { 
			this.check(
					new PFRHttpCheck(CheckType.CONTAINS)
						.checkBody(containsThis)
						.appendLogDetails(appendLogDetails) 
						.messageOnFail(customMessage)
				); 	
			return this; 
	}
	
	/***************************************************************************
	 * Add a check to the list of checks. 
	 * @param containsThis the string to be checked
	 * @param appendLogDetails retrieved from PFRContext 
	 ***************************************************************************/ 
	public PFRHttpRequestBuilder checkBodyContains(String containsThis, boolean appendLogDetails) { 
			this.check(
					new PFRHttpCheck(CheckType.CONTAINS)
						.checkBody(containsThis)
						.appendLogDetails(appendLogDetails) 
					); 	
			return this; 
	}
	
	/***************************************************************************
	 * Add a check to the list of checks. 
	 * @param containsThis the string to be checked
	 ***************************************************************************/ 
	public PFRHttpRequestBuilder checkBodyContains(String containsThis) { 
		this.check(
			new PFRHttpCheck(CheckType.CONTAINS)
				.checkBody(containsThis) 
			); 	
		return this; 
	}
	
	/***************************************************************************
	 * Add a check to the list of checks. 
	 * @param notContainsThis the string to be checked
	 * @param appendLogDetails retrieved from PFRContext 
	 * @param customMessage a custom message instead of the default message
	 ***************************************************************************/ 
	public PFRHttpRequestBuilder checkBodyContainsNot(String notContainsThis, boolean appendLogDetails, String customMessage) { 
			this.check(
					new PFRHttpCheck(CheckType.DOES_NOT_CONTAIN)
						.checkBody(notContainsThis)
						.appendLogDetails(appendLogDetails) 
						.messageOnFail(customMessage)
				); 	
			return this; 
	}
	
	/***************************************************************************
	 * Add a check to the list of checks. 
	 * @param notContainsThis the string to be checked
	 * @param appendLogDetails retrieved from PFRContext 
	 ***************************************************************************/ 
	public PFRHttpRequestBuilder checkBodyContainsNot(String notContainsThis, boolean appendLogDetails) { 
			this.check(
					new PFRHttpCheck(CheckType.DOES_NOT_CONTAIN)
						.checkBody(notContainsThis)
						.appendLogDetails(appendLogDetails) 
					); 	
			return this; 
	}
	
	/***************************************************************************
	 * Add a check to the list of checks. 
	 * @param notContainsThis the string to be checked
	 ***************************************************************************/ 
	public PFRHttpRequestBuilder checkBodyContainsNot(String notContainsThis) { 
		this.check(
			new PFRHttpCheck(CheckType.DOES_NOT_CONTAIN)
				.checkBody(notContainsThis) 
			); 	
		return this; 
	}
	
	/***************************************************************************
	 * Add a check to the list of checks. 
	 * @param equalsThis the string to be checked
	 * @param appendLogDetails retrieved from PFRContext 
	 * @param customMessage a custom message instead of the default message
	 ***************************************************************************/ 
	public PFRHttpRequestBuilder checkBodyEquals(String equalsThis, boolean appendLogDetails, String customMessage) { 
			this.check(
					new PFRHttpCheck(CheckType.EQUALS)
						.checkBody(equalsThis)
						.appendLogDetails(appendLogDetails) 
						.messageOnFail(customMessage)
				); 	
			return this; 
	}
	
	/***************************************************************************
	 * Add a check to the list of checks. 
	 * @param equalsThis the string to be checked
	 * @param appendLogDetails retrieved from PFRContext 
	 ***************************************************************************/ 
	public PFRHttpRequestBuilder checkBodyEquals(String equalsThis, boolean appendLogDetails) { 
			this.check(
					new PFRHttpCheck(CheckType.EQUALS)
						.checkBody(equalsThis)
						.appendLogDetails(appendLogDetails) 
					); 	
			return this; 
	}
	
	/***************************************************************************
	 * Add a check to the list of checks. 
	 * @param equalsThis the string to be checked
	 ***************************************************************************/ 
	public PFRHttpRequestBuilder checkBodyEquals(String equalsThis) { 
		this.check(
			new PFRHttpCheck(CheckType.EQUALS)
				.checkBody(equalsThis) 
			); 	
		return this; 
	}
	
	
	/***************************************************************************
	 * Add a check to the list of checks. 
	 * @param regexToMatch the regex to be matched
	 * @param appendLogDetails retrieved from PFRContext 
	 * @param customMessage a custom message instead of the default message
	 ***************************************************************************/ 
	public PFRHttpRequestBuilder checkBodyRegex(String regexToMatch, boolean appendLogDetails, String customMessage) { 
			this.check(
					new PFRHttpCheck(CheckType.MATCH_REGEX)
						.checkBody(regexToMatch)
						.appendLogDetails(appendLogDetails) 
						.messageOnFail(customMessage)
				); 	
			return this; 
	}

	
	/***************************************************************************
	 * Add a check to the list of checks. 
	 * @param regexToMatch the regex to be matched
	 * @param appendLogDetails retrieved from PFRContext 
	 ***************************************************************************/ 
	public PFRHttpRequestBuilder checkBodyRegex(String regexToMatch, boolean appendLogDetails) { 
			this.check(
					new PFRHttpCheck(CheckType.MATCH_REGEX)
						.checkBody(regexToMatch)
						.appendLogDetails(appendLogDetails) 
					); 	
			return this; 
	}
	
	
	/***************************************************************************
	 * Add a check to the list of checks. 
	 * @param regexToMatch the regex to be matched
	 ***************************************************************************/ 
	public PFRHttpRequestBuilder checkBodyRegex(String regexToMatch) { 
		this.check(
			new PFRHttpCheck(CheckType.MATCH_REGEX)
				.checkBody(regexToMatch) 
			); 	
		return this; 
	}
	
	/***************************************************************************
	 * Add a check to the list of checks. 
	 * @param type the check type that should be used for this check
	 * @param containsThis the string to be checked
	 * @param appendLogDetails retrieved from PFRContext 
	 * @param customMessage a custom message instead of the default message
	 ***************************************************************************/ 
	public PFRHttpRequestBuilder checkHeader(CheckType type, String headerName, String containsThis, boolean appendLogDetails, String customMessage) { 
			this.check(
					new PFRHttpCheck(type)
						.checkHeader(headerName, containsThis)
						.appendLogDetails(appendLogDetails) 
						.messageOnFail(customMessage)
				); 	
			return this; 
	}
	
	/***************************************************************************
	 * Add a check to the list of checks. 
	 * @param containsThis the string to be checked
	 * @param appendLogDetails retrieved from PFRContext 
	 * @param customMessage a custom message instead of the default message
	 ***************************************************************************/ 
	public PFRHttpRequestBuilder checkHeaderContains(String headerName, String containsThis, boolean appendLogDetails, String customMessage) { 
			this.check(
					new PFRHttpCheck(CheckType.CONTAINS)
						.checkHeader(headerName, containsThis)
						.appendLogDetails(appendLogDetails) 
						.messageOnFail(customMessage)
				); 	
			return this; 
	}
	
	/***************************************************************************
	 * Add a check to the list of checks. 
	 * @param containsThis the string to be checked
	 * @param appendLogDetails retrieved from PFRContext 
	 ***************************************************************************/ 
	public PFRHttpRequestBuilder checkHeaderContains(String headerName, String containsThis, boolean appendLogDetails) { 
			this.check(
					new PFRHttpCheck(CheckType.CONTAINS)
						.checkHeader(headerName, containsThis)
						.appendLogDetails(appendLogDetails) 
					); 	
			return this; 
	}
	
	/***************************************************************************
	 * Add a check to the list of checks. 
	 * @param containsThis the string to be checked
	 ***************************************************************************/ 
	public PFRHttpRequestBuilder checkHeaderContains(String headerName, String containsThis) { 
		this.check(
			new PFRHttpCheck(CheckType.CONTAINS)
				.checkHeader(headerName, containsThis) 
			); 	
		return this; 
	}
	
	/***************************************************************************
	 * Add a check to the list of checks. 
	 * @param notContainsThis the string to be checked
	 * @param appendLogDetails retrieved from PFRContext 
	 * @param customMessage a custom message instead of the default message
	 ***************************************************************************/ 
	public PFRHttpRequestBuilder checkHeaderContainsNot(String headerName, String notContainsThis, boolean appendLogDetails, String customMessage) { 
			this.check(
					new PFRHttpCheck(CheckType.DOES_NOT_CONTAIN)
						.checkHeader(headerName, notContainsThis)
						.appendLogDetails(appendLogDetails) 
						.messageOnFail(customMessage)
				); 	
			return this; 
	}
	
	/***************************************************************************
	 * Add a check to the list of checks. 
	 * @param notContainsThis the string to be checked
	 * @param appendLogDetails retrieved from PFRContext 
	 ***************************************************************************/ 
	public PFRHttpRequestBuilder checkHeaderContainsNot(String headerName, String notContainsThis, boolean appendLogDetails) { 
			this.check(
					new PFRHttpCheck(CheckType.DOES_NOT_CONTAIN)
						.checkHeader(headerName, notContainsThis)
						.appendLogDetails(appendLogDetails) 
					); 	
			return this; 
	}
	
	/***************************************************************************
	 * Add a check to the list of checks. 
	 * @param notContainsThis the string to be checked
	 ***************************************************************************/ 
	public PFRHttpRequestBuilder checkHeaderContainsNot(String headerName, String notContainsThis) { 
		this.check(
			new PFRHttpCheck(CheckType.DOES_NOT_CONTAIN)
				.checkHeader(headerName, notContainsThis) 
			); 	
		return this; 
	}
	
	/***************************************************************************
	 * Add a check to the list of checks. 
	 * @param equalsThis the string to be checked
	 * @param appendLogDetails retrieved from PFRContext 
	 * @param customMessage a custom message instead of the default message
	 ***************************************************************************/ 
	public PFRHttpRequestBuilder checkHeaderEquals(String headerName, String equalsThis, boolean appendLogDetails, String customMessage) { 
			this.check(
					new PFRHttpCheck(CheckType.EQUALS)
						.checkHeader(headerName, equalsThis)
						.appendLogDetails(appendLogDetails) 
						.messageOnFail(customMessage)
				); 	
			return this; 
	}
	
	/***************************************************************************
	 * Add a check to the list of checks. 
	 * @param equalsThis the string to be checked
	 * @param appendLogDetails retrieved from PFRContext 
	 ***************************************************************************/ 
	public PFRHttpRequestBuilder checkHeaderEquals(String headerName, String equalsThis, boolean appendLogDetails) { 
			this.check(
					new PFRHttpCheck(CheckType.EQUALS)
						.checkHeader(headerName, equalsThis)
						.appendLogDetails(appendLogDetails) 
					); 	
			return this; 
	}
	
	/***************************************************************************
	 * Add a check to the list of checks. 
	 * @param equalsThis the string to be checked
	 ***************************************************************************/ 
	public PFRHttpRequestBuilder checkHeaderEquals(String headerName, String equalsThis) { 
		this.check(
			new PFRHttpCheck(CheckType.EQUALS)
				.checkHeader(headerName, equalsThis) 
			); 	
		return this; 
	}
	
	
	/***************************************************************************
	 * Add a check to the list of checks. 
	 * @param regexToMatch the regex to be matched
	 * @param appendLogDetails retrieved from PFRContext 
	 * @param customMessage a custom message instead of the default message
	 ***************************************************************************/ 
	public PFRHttpRequestBuilder checkHeaderRegex(String headerName, String regexToMatch, boolean appendLogDetails, String customMessage) { 
			this.check(
					new PFRHttpCheck(CheckType.MATCH_REGEX)
						.checkHeader(headerName, regexToMatch)
						.appendLogDetails(appendLogDetails) 
						.messageOnFail(customMessage)
				); 	
			return this; 
	}

	
	/***************************************************************************
	 * Add a check to the list of checks. 
	 * @param regexToMatch the regex to be matched
	 * @param appendLogDetails retrieved from PFRContext 
	 ***************************************************************************/ 
	public PFRHttpRequestBuilder checkHeaderRegex(String headerName, String regexToMatch, boolean appendLogDetails) { 
			this.check(
					new PFRHttpCheck(CheckType.MATCH_REGEX)
						.checkHeader(headerName, regexToMatch)
						.appendLogDetails(appendLogDetails) 
					); 	
			return this; 
	}
	
	
	/***************************************************************************
	 * Add a check to the list of checks. 
	 * @param regexToMatch the regex to be matched
	 ***************************************************************************/ 
	public PFRHttpRequestBuilder checkHeaderRegex(String headerName, String regexToMatch) { 
		this.check(
			new PFRHttpCheck(CheckType.MATCH_REGEX)
				.checkHeader(headerName, regexToMatch) 
			); 	
		return this; 
	}
	
	/***************************************************************************
	 * Add a check to the list of checks. 
	 * @param equalsThis the string to be checked
	 * @param appendLogDetails retrieved from PFRContext 
	 * @param customMessage a custom message instead of the default message
	 ***************************************************************************/ 
	public PFRHttpRequestBuilder checkStatusEquals(int equalsThis, boolean appendLogDetails, String customMessage) { 
			this.check(
					new PFRHttpCheck(CheckType.EQUALS)
						.checkStatus(equalsThis)
						.appendLogDetails(appendLogDetails) 
						.messageOnFail(customMessage)
				); 	
			return this; 
	}
	
	/***************************************************************************
	 * Add a check to the list of checks. 
	 * @param equalsThis the string to be checked
	 * @param appendLogDetails retrieved from PFRContext 
	 ***************************************************************************/ 
	public PFRHttpRequestBuilder checkStatusEquals(int equalsThis, boolean appendLogDetails) { 
			this.check(
					new PFRHttpCheck(CheckType.EQUALS)
						.checkStatus(equalsThis)
						.appendLogDetails(appendLogDetails) 
					); 	
			return this; 
	}
	
	/***************************************************************************
	 * Add a check to the list of checks. 
	 * @param equalsThis the string to be checked
	 ***************************************************************************/ 
	public PFRHttpRequestBuilder checkStatusEquals(int equalsThis) { 
		this.check(
			new PFRHttpCheck(CheckType.EQUALS)
				.checkStatus(equalsThis) 
			); 	
		return this; 
	}
	
	
	
	/***************************************************************************
	 * Build and send the request. Returns a 
	 * PRFHttpResponse or null in case of errors.
	 ***************************************************************************/
	@SuppressWarnings("deprecation")
	public PFRHttpResponse send() {
		
		try {
			
			//---------------------------------
			// Create URL
			String urlWithParams = buildURLwithParams();
			//HttpURLConnection connection = createProxiedURLConnection(urlWithParams);
			//connection.setRequestMethod(method);
			//connection.setInstanceFollowRedirects(true);
			

			HttpUriRequestBase requestBase = new HttpUriRequestBase(method, URI.create(urlWithParams));
			
			if(requestBase != null) {
				
									
				//-----------------------------------
				// Handle POST Body
				if(body != null) {
					
					StringEntity bodyEntity = new StringEntity(body);
					requestBase.setEntity(bodyEntity);
					
//						if(headers.containsKey(HEADER_CONTENT_TYPE)) {
//							connection.setRequestProperty(HEADER_CONTENT_TYPE, headers.get(HEADER_CONTENT_TYPE));
//						}else if(!Strings.isNullOrEmpty(requestBodyContentType)) {
//							connection.setRequestProperty(HEADER_CONTENT_TYPE, requestBodyContentType);
//						}
//						connection.setDoOutput(true);
//						connection.connect();
//						try(OutputStream outStream = connection.getOutputStream()) {
//						    byte[] input = requestBody.getBytes("utf-8");
//						    outStream.write(input, 0, input.length);           
//						}
				}
				
				

				//----------------------------------
				// Create HTTP Client
				
				HttpClientBuilder clientBuilder = HttpClientBuilder.create();
				clientBuilder.setDefaultCookieStore(PFRHttp.cookieStore.get());
				
				clientBuilder.setDefaultRequestConfig(
							 RequestConfig
									.custom()
									.setResponseTimeout(Timeout.of(responseTimeoutMillis, TimeUnit.MILLISECONDS) )
									.build()
						);
				PFRHttp.httpClientAddProxy(clientBuilder, URL);
				PFRHttp.setSSLContext(clientBuilder);

				//----------------------------------
				// Create HTTP Client
				if(disableFollowRedirects) {
					clientBuilder.disableRedirectHandling();
				}
				
			    //----------------------------------
				// Set Auth mechanism
				if(username != null) {
					
					//---------------------------------
					// Credential Provider
					String scheme = requestBase.getUri().getScheme();
					String hostname = requestBase.getUri().getHost();
					int port = requestBase.getUri().getPort();
					HttpHost targetHost = new HttpHost(scheme, hostname, port);

					CredentialsProviderBuilder credProviderBuilder = CredentialsProviderBuilder.create();
					
					RegistryBuilder<AuthSchemeFactory> registryBuilder = RegistryBuilder.<AuthSchemeFactory>create();
					
					switch(this.authMethod) {
					
						//------------------------------
						// Basic 
						case BASIC:
							//PRFHttp.addBasicAuthorizationHeader(headers, username, new String(pwdArray));
							AuthScope authScopeBasic = new AuthScope(targetHost, null, new BasicScheme().getName());
							credProviderBuilder.add(authScopeBasic, username, pwdArray);
							registryBuilder.register(StandardAuthScheme.BASIC, BasicSchemeFactory.INSTANCE);
						break;
						
						//------------------------------
						// Basic 
						case BASIC_HEADER:
							PFRHttp.addBasicAuthorizationHeader(headers, username, new String(pwdArray));
						break;
						
						
						//------------------------------
						// Digest
						case DIGEST:
							AuthScope authScopeDigest = new AuthScope(targetHost, null, new DigestScheme().getName());
							credProviderBuilder.add(authScopeDigest, username, pwdArray);
							registryBuilder.register(StandardAuthScheme.DIGEST, DigestSchemeFactory.INSTANCE);
						break;
						
						//------------------------------
						// NTLM
						case NTLM:
							String ntlmUsername = username;
							String ntlmDomain = null;
							if(username.contains("@")) {
								String[] splitted = username.split("@");
								ntlmUsername = splitted[0];
								ntlmDomain = splitted[1];
							}
							AuthScope authScopeNTLM = new AuthScope(targetHost, null, new NTLMScheme().getName());
							
							NTCredentials ntlmCreds = new NTCredentials(pwdArray, ntlmUsername, ntlmDomain, null);
							credProviderBuilder.add(authScopeNTLM, ntlmCreds);
							registryBuilder.register(StandardAuthScheme.NTLM, NTLMSchemeFactory.INSTANCE);
						break;
							
						//------------------------------
						// KERBEROS (experimental)
						case KERBEROS:
							GSSManager manager = GSSManager.getInstance();
							GSSName name = manager.createName(username, GSSName.NT_USER_NAME);
						    GSSCredential gssCred = manager.createCredential(name,GSSCredential.DEFAULT_LIFETIME, (Oid) null, GSSCredential.INITIATE_AND_ACCEPT);
						    
							AuthScope authScopeKerberos = new AuthScope(targetHost, null, new KerberosScheme().getName());
						
							KerberosCredentials kerbCred = new KerberosCredentials(gssCred);
							credProviderBuilder.add(authScopeKerberos, kerbCred);
							registryBuilder.register(StandardAuthScheme.SPNEGO, new SPNegoSchemeFactory(
												                KerberosConfig.custom()
										                        .setStripPort(KerberosConfig.Option.DEFAULT)
										                        .setUseCanonicalHostname(KerberosConfig.Option.DEFAULT)
										                        .build(),
										                SystemDefaultDnsResolver.INSTANCE))
										        .register(StandardAuthScheme.KERBEROS, KerberosSchemeFactory.DEFAULT);
						break;
						
						default:
						break;
					
					}

					if (this.authMethod != PFRHttpAuthMethod.BASIC_HEADER) {
						//---------------------------------
						// Scheme Factory
						Registry<AuthSchemeFactory> schemeFactoryRegistry = registryBuilder.build();
						
						//---------------------------------
						// Credential Provider
						clientBuilder
							.setDefaultAuthSchemeRegistry(schemeFactoryRegistry)
							.setDefaultCredentialsProvider(credProviderBuilder.build());
					}
					
				}
				
				//-----------------------------------
				// Handle headers
				if(headers != null ) {
					for(Entry<String, String> header : headers.entrySet()) {
						requestBase.addHeader(header.getKey(), header.getValue());
					}
				}

				//-----------------------------------
				// Connect and create response
				CloseableHttpClient httpClient = clientBuilder.build();

				PFRHttpResponse response = new PFRHttpResponse(this, httpClient, requestBase, autoCloseClient);
				return response;
				
			}
		} catch (Throwable e) {
			
			PFRHttp.logger.error("Exception while sending HTTP Request: "+e.getMessage(), e);
		} 
		
		return  new PFRHttpResponse(this);
		
	}
	
	
}