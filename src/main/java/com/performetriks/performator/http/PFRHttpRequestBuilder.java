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
import com.xresch.hsr.utils.HSRText.CheckType;
import com.xresch.hsr.utils.HSRTime.HSRTimeUnit;

/******************************************************************************************************
	 * Inner Class for HTTP Response
	 ******************************************************************************************************/
	public class PFRHttpRequestBuilder {
		
		private static final String HEADER_CONTENT_TYPE = "Content-Type";
		private PFRHttpAuthMethod authMethod = PFRHttpAuthMethod.BASIC;
		private String username = null;
		private char[] pwdArray = null;
		
		String metricName = null;
		String method = "GET";
		String URL = null;
		String requestBody = null;
		
		String requestBodyContentType = "plain/text; charset=UTF-8";
		private boolean autoCloseClient = true;
		long responseTimeoutMillis = HSRTimeUnit.m.toMillis(10); //default timeout of  10 minutes

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
		
		/***********************************************
		 * Set method to GET
		 ***********************************************/
		public PFRHttpRequestBuilder GET() {
			method = "GET";
			return this;
		}
		
		/***********************************************
		 * Set method to POST
		 ***********************************************/
		public PFRHttpRequestBuilder POST() {
			method = "POST";
			return this;
		}
		
		/***********************************************
		 * Add a parameter to the request.
		 ***********************************************/
		public PFRHttpRequestBuilder param(String name, String value) {
			params.put(name, value);
			return this;
		}
		
		
		/***********************************************
		 * Adds a map of parameters
		 ***********************************************/
		public PFRHttpRequestBuilder params(Map<String, String> paramsMap) {
			
			if(paramsMap == null) { return this; }
			
			this.params.putAll(paramsMap);
			return this;
			
		}
		
		/***********************************************
		 * Add a header
		 ***********************************************/
		public PFRHttpRequestBuilder header(String name, String value) {
			headers.put(name, value);
			return this;
		}
		


		
		/***********************************************
		 * Adds a map of headers
		 ***********************************************/
		public PFRHttpRequestBuilder headers(Map<String, String> headerMap) {
			
			if(headerMap == null) { return this; }
			
			this.headers.putAll(headerMap);
			return this;
			
		}
		
		/***********************************************
		 * Toggle autoCloseClient
		 ***********************************************/
		public PFRHttpRequestBuilder autoCloseClient(boolean autoCloseClient) {
			this.autoCloseClient = autoCloseClient;
			return this;
		}
		
		/***********************************************
		 * Get autoCloseClient
		 ***********************************************/
		public boolean autoCloseClient() {
			return this.autoCloseClient;
		}
		
		/***********************************************
		 * Set Basic Authentication
		 ***********************************************/
		public PFRHttpRequestBuilder setAuthCredentialsBasic(String username, String password) {
			return setAuthCredentials(PFRHttpAuthMethod.BASIC, username, password);
		}
		
		/***********************************************
		 * Set authentication credentials
		 ***********************************************/
		public PFRHttpRequestBuilder setAuthCredentials(PFRHttpAuthMethod authMethod, String username, String password) {
		
			this.authMethod = (authMethod != null ) ? authMethod : PFRHttpAuthMethod.BASIC;
			this.username = username;
			this.pwdArray = (password != null ) ? password.toCharArray() : "".toCharArray();

			return this;
		}
		
		/***********************************************
		 * Add a request Body
		 ***********************************************/
		public PFRHttpRequestBuilder body(String content) {
			this.requestBody = content;
			return this;
		}
		
		/***********************************************
		 * Add a request Body
		 ***********************************************/
		public PFRHttpRequestBuilder body(String contentType, String content) {
			this.requestBodyContentType = contentType;
			this.header(HEADER_CONTENT_TYPE, contentType);
			this.requestBody = content;
			return this;
		}
		
		/***********************************************
		 * Add a request Body in JSON format UTF-8 encoding
		 ***********************************************/
		public PFRHttpRequestBuilder bodyJSON(String content) {
			return this.body("application/json; charset=UTF-8", content);
		}
		
		/***********************************************
		 * Add a response timeout.
		 ***********************************************/
		public PFRHttpRequestBuilder timeout(long responseTimeoutMillis) {
			this.responseTimeoutMillis = responseTimeoutMillis;
			return this;
		}
		
		/***********************************************
		 * Add a request Body in JSON format UTF-8 encoding
		 ***********************************************/
		public String buildURLwithParams() {
			return  PFRHttp.buildURL(URL, params);
		}
		
		/***********************************************
		 * Add a check to the list of checks.
		 * @return instance of chaining
		 ***********************************************/
		public PFRHttpRequestBuilder checkBodyContains(String value) {
			this.check(new PFRHttpCheck(CheckType.CONTAINS).checkBody(value) );
			return this;
		}
		
		/***********************************************
		 * Add a check to the list of checks.
		 * @return instance of chaining
		 ***********************************************/
		public PFRHttpRequestBuilder checkBody(CheckType type, String value) {
			
			this.check( new PFRHttpCheck(type).checkBody(value) );
			return this;
		}
		
		/***********************************************
		 * Add a check to the list of checks.
		 * @return instance of chaining
		 ***********************************************/
		public void check(PFRHttpCheck check) {
			checksList.add(check);
		}
		
		/***********************************************
		 * Build and send the request. Returns a 
		 * PRFHttpResponse or null in case of errors.
		 ***********************************************/
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
					if(requestBody != null) {
						
						StringEntity bodyEntity = new StringEntity(requestBody);
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