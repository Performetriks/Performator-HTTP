package com.performetriks.performator.http;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
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
import org.apache.hc.client5.http.impl.io.BasicHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.routing.DefaultProxyRoutePlanner;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.apache.hc.core5.util.Timeout;
import org.graalvm.polyglot.Value;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.performetriks.performator.base.PFR;
import com.performetriks.performator.http.PFRHttp.PFRHttpSection;
import com.performetriks.performator.http.scriptengine.PFRScripting;
import com.performetriks.performator.http.scriptengine.PFRScriptingContext;
import com.xresch.hsr.base.HSR;
import com.xresch.hsr.stats.HSRRecord;
import com.xresch.hsr.stats.HSRRecord.HSRRecordStatus;
import com.xresch.hsr.utils.HSRText.CheckType;
import com.xresch.hsr.utils.HSRTime.HSRTimeUnit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

/**************************************************************************************************************
 * 
 * @author Reto Scheiwiller, (c) Copyright 2019 
 * @license MIT-License
 **************************************************************************************************************/
public class PFRHttp {
	
	private static Logger logger = (ch.qos.logback.classic.Logger)LoggerFactory.getLogger(PFRHttp.class.getName());
	
	//Use Threadlocal to avoid polyglot multi thread exceptions
	private static ThreadLocal<PFRScriptingContext> javascriptEngine = new ThreadLocal<PFRScriptingContext>();
	
	private static KeyStore cachedKeyStore = null;
	
	private static String proxyPAC = null;
	private static HashMap<String, ArrayList<PFRProxy>> resolvedProxiesCache = new HashMap<>();

	private static PFRHttp instance = new PFRHttp();
	
	// either a http URL or a resourcePath like "com/mycompany/files/script.pac"
	private static InheritableThreadLocal<String> proxyPacFile = new InheritableThreadLocal<>();
	
	private static InheritableThreadLocal<String> keystorePath = new InheritableThreadLocal<>();
	private static InheritableThreadLocal<String> keystorePW = new InheritableThreadLocal<>();
	private static InheritableThreadLocal<String> keystoreManagerPW = new InheritableThreadLocal<>();
	
	
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
			
			if(urlWithPath.endsWith("?") || urlWithPath.endsWith("/")) {
				urlWithPath = urlWithPath.substring(0, urlWithPath.length()-1);
			}
			StringBuilder builder = new StringBuilder(urlWithPath);
			
			builder.append("?");
			
			for(Entry<String,String> param : params.entrySet()) {
				builder.append(encode(param.getKey(), param.getValue()));
			}
			
			return builder.toString();
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
				Response response = null;
				
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
					proxyPAC = response.getResponseBody();
					
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
							PFRProxy cfwProxy = instance.new PFRProxy();
							
							String port;
							
							cfwProxy.type = splitted[0];
							if(splitted.length > 1) {
								String hostport = splitted[1];
								if(hostport.indexOf(":") != -1) {
									cfwProxy.host = hostport.substring(0, hostport.indexOf(":"));
									port = hostport.substring(hostport.indexOf(":")+1);
								}else {
									cfwProxy.host = hostport;
									port = "80";	
								}
								
								try {
									cfwProxy.port = Integer.parseInt(port);
								}catch(Throwable e) {
									logger.error("Error parsing port to integer.", e);
								}
								proxies.add(cfwProxy);
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
			PFRProxy direct = instance.new PFRProxy();
			direct.type = "DIRECT";
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

				if(cfwProxy.type.trim().toUpperCase().equals("DIRECT")) {
					return null;
				}else {
					
					InetSocketAddress address = new InetSocketAddress(cfwProxy.host, cfwProxy.port);
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

					if(cfwProxy.type.trim().toUpperCase().equals("DIRECT")) {
						proxiedConnection = (HttpURLConnection)new URL(url).openConnection();
					}else {
						
						InetSocketAddress address = new InetSocketAddress(cfwProxy.host, cfwProxy.port);
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
	public static void httpClientAddProxy(HttpClientBuilder clientBuilder, String targetURL) {
		
		if(proxyPacFile.get() != null) {
			return; // nothing todo
		}else {
			ArrayList<PFRProxy> proxiesArray = getProxies(targetURL);
			
			//--------------------------------------------------
			// Return direct if no proxies were returned by PAC
			if(proxiesArray.isEmpty()) {
				return; 
			}
			
			//--------------------------------------------------
			// Iterate PAC Proxies until address is resolved
			for(PFRProxy cfwProxy : proxiesArray) {
				
				if(cfwProxy.type.trim().toUpperCase().equals("DIRECT")) {
					return; // no proxy required
				}else {
					
					InetSocketAddress address = new InetSocketAddress(cfwProxy.host, cfwProxy.port);
					if(address.isUnresolved()) { 
						continue;
					};

					
					HttpHost proxy = new HttpHost(cfwProxy.host, cfwProxy.port);

					DefaultProxyRoutePlanner routePlanner = new DefaultProxyRoutePlanner(proxy);
					clientBuilder.setRoutePlanner(routePlanner);
				}
				return ; // done
			}
			
			//-------------------------------------
			// None of the addresses were resolved
			logger
			.warn("The proxy addresses couldn't be resolved.");
		}

		return ;
		
	}
	
	
//	/******************************************************************************************************
//	 * Send a HTTP GET request and returns the result or null in case of error.
//	 * @param url used for the request.
//	 * @return PRFHttpResponse response or null
//	 ******************************************************************************************************/
//	public static PFRHttpResponse sendGETRequest(String url) {
//		return sendGETRequest(url, null, null);
//	}
	
	/******************************************************************************************************
	 * Log details about get and post request
	 * @param requestBody TODO
	 ******************************************************************************************************/
	private static void logFinerRequestInfo(String method, String url, HashMap<String, String> params, HashMap<String, String> headers, String requestBody) {
		if(logger.isEnabledFor(Level.TRACE)) {
			
			String paramsString = (params == null) ? "null" : Joiner.on("&").withKeyValueSeparator("=").join(params);
			String headersString = (headers == null) ? "null" : Joiner.on(",").withKeyValueSeparator("=").join(headers);
			
			logger.trace(
					"================================ Request Details ================================"
					+"PRFHttp-method: "+ method
					+"\nPRFHttp-url: "+ url
					+"\nPRFHttp-params: "+ paramsString
					+"\nPRFHttp-headers: "+ headersString
					+"\nPRFHttp-body: "+ requestBody
					+"================================ End of Details ================================"
					);


		}
	}
	
	/******************************************************************************************************
	 * Send a HTTP GET request and returns the result or null in case of error.
	 * @param url used for the request.
	 * @param params the parameters which should be added to the request or null
	 * @return PRFHttpResponse response or null
	 ******************************************************************************************************/
	public static void addBasicAuthorizationHeader(HashMap<String, String> params, String basicUsername, String basicPassword) {
		
		String valueToEncode = basicUsername + ":" + basicPassword;
		params.put("Authorization", "Basic "+Base64.getEncoder().encodeToString(valueToEncode.getBytes()));	    	
	}
	
	/******************************************************************************************************
	 * Creates a request builder for chained building of requests.
	 * @param url used for the request.
	 ******************************************************************************************************/
	public static PFRHttpRequestBuilder create(String url) {
		return instance.new PFRHttpRequestBuilder(url);	    	
	}
	
	/******************************************************************************************************
	 * Creates a request builder for chained building of requests.
	 * @param url used for the request.
	 ******************************************************************************************************/
	public static PFRHttpRequestBuilder create(String metric, String url) {
		return instance.new PFRHttpRequestBuilder(metric, url);	    	
	}
	
//	/******************************************************************************************************
//	 * Send a HTTP GET request and returns the result or null in case of error.
//	 * @param url used for the request.
//	 * @param params the parameters which should be added to the request or null
//	 * @return PRFHttpResponse response or null
//	 ******************************************************************************************************/
//	public static PFRHttpResponse sendGETRequest(String url, HashMap<String, String> params) {
//		return sendGETRequest(url, params, null);	    	
//	}
	
//	/******************************************************************************************************
//	 * Send a HTTP GET request and returns the result or null in case of error.
//	 * @param url used for the request.
//	 * @param params the parameters which should be added to the request or null
//	 * @param headers the HTTP headers for the request or null
//	 * @return PRFHttpResponse response or null
//	 ******************************************************************************************************/
//	public static PFRHttpResponse sendGETRequest(String url, HashMap<String, String> params, HashMap<String, String> headers) {
//
//		return PFRHttp.newRequestBuilder(url)
//				.GET()
//				.headers(headers)
//				.params(params)
//				.send();
//
//	    	
//	}
		

//	/******************************************************************************************************
//	 * Send a HTTP POST request and returns the result or null in case of error.
//	 * @param url used for the request.
//	 * @param params the parameters which should be added to the requests post body or null
//	 * @param headers the HTTP headers for the request or null
//	 * @return String response
//	 ******************************************************************************************************/
//	public static Response sendPOSTRequest(String url, HashMap<String, String> params, HashMap<String, String> headers) {
//		
//		return PFRHttp.create(url)
//					.POST()
//					.headers(headers)
//					.params(params)
//					.send();
//	    	
//	}

	/******************************************************************************************************
	 * Inner Class for HTTP Checks
	 ******************************************************************************************************/
	public class PFRHttpCheck {
		
		private CheckType checkType;
		private PFRHttpSection section = PFRHttpSection.BODY;
		private String messageOnFail = null;
		
		private String valueToCheck = null;
		
		private String headerName = null;
		
		/***********************************************
		 * 
		 ***********************************************/
		public PFRHttpCheck(CheckType checkType) {
			this.checkType = checkType;
		}
		
		/***********************************************
		 * Set to check the body
		 ***********************************************/
		public PFRHttpCheck checkBody(String valueToCheck) {
			section = PFRHttpSection.BODY;
			this.valueToCheck = valueToCheck;
			
			return this;
		}
		
		/***********************************************
		 * Set to check the header
		 ***********************************************/
		public PFRHttpCheck checkHeader(String headerName, String valueToCheck) {
			section = PFRHttpSection.HEADER;
			this.headerName = headerName;
			this.valueToCheck = valueToCheck;
			
			return this;
		}
		
		/***********************************************
		 * 
		 ***********************************************/
		public PFRHttpCheck messageOnFail(String message) {
			this.messageOnFail = message;
			return this;
		}
		
		/***********************************************
		 * 
		 ***********************************************/
		public boolean check(Response r) {
			
			if(r == null 
			|| r.hasError
			){ return false; }
			
			switch (section) {
				case BODY: 		return checkBody(r);
				case HEADER:	return checkHeader(r);
				default: 		return false;
			}

		}
		
		/***********************************************
		 * 
		 ***********************************************/
		private boolean checkBody(Response r) {
			
			boolean success = HSR.Text.checkTextForContent(checkType, r.body, valueToCheck);
			
			if(!success) { logMessage(r); }
			
			return success;
		}
		
		/***********************************************
		 * 
		 ***********************************************/
		private boolean checkHeader(Response r) {
			
			String headerValue = r.getHeadersAsJson().get(headerName).getAsString();

			boolean success = HSR.Text.checkTextForContent(checkType, headerValue, valueToCheck);
			
			if(!success) { logMessage(r); }
			
			return success;
		}
		
		/***********************************************
		 * 
		 ***********************************************/
		private void logMessage(Response r) {

			String finalMessage = (messageOnFail != null) ? messageOnFail : createDefaultMessage();
			
			r.responseLogger.error(finalMessage);
			
			HSR.addErrorMessage(finalMessage)
				.parent(r.record)
				;
			
		}
		
		
		/***********************************************
		 * 
		 ***********************************************/
		private String createDefaultMessage() {

			return new StringBuilder()
					.append("HTTP response check failed: ")
					.append(section.toString().toLowerCase())
					.append(" ")
					.append( (headerName != null) ? "\""+headerName+"\"" : "")
					.append(checkType.toString())
					.append(" \"")
					.append(valueToCheck)
					.append("\"")
					.toString()
					;
		}
		
	}
	
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

		private ArrayList<PFRHttpCheck> checksList = new ArrayList<>();
		
		private HashMap<String, String> params = new HashMap<>();
		private HashMap<String, String> headers = new HashMap<>();
		
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
			return  buildURL(URL, params);
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
		public Response send() {
			
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
					clientBuilder.setDefaultRequestConfig(
								 RequestConfig
										.custom()
										.setResponseTimeout(Timeout.of(responseTimeoutMillis, TimeUnit.MILLISECONDS) )
										.build()
							);
					httpClientAddProxy(clientBuilder, URL);
					setSSLContext(clientBuilder);

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
					PFRHttp.logFinerRequestInfo(method, URL, params, headers, requestBody);	

					CloseableHttpClient httpClient = clientBuilder.build();

					Response response = instance.new Response(this, httpClient, requestBase, autoCloseClient);
					return response;
					
				}
			} catch (Throwable e) {
				
				logger.error("Exception while sending HTTP Request: "+e.getMessage(), e);
			} 
			
			return  instance.new Response(this);
			
		}
		
		
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
				KeyStore cachedKeyStore = KeyStore.getInstance(keystoreType); // or "PKCS12"
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
	private static void setSSLContext(HttpClientBuilder builder) throws Exception {
		
		//=====================================================
		// Initialize Connection Manager
		//=====================================================

			//-------------------------------
			// Trust anything
			final TrustStrategy acceptingTrustStrategy = (cert, authType) -> true;
		    
			//-------------------------------
			// Create SSL Context Builder
			final SSLContextBuilder sslContextBuilder = SSLContexts.custom().loadTrustMaterial(null, acceptingTrustStrategy); 
			addKeyStore(sslContextBuilder);
			
			//-------------------------------
			// Connection Factory
			final SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContextBuilder.build(), NoopHostnameVerifier.INSTANCE);
			
			final Registry<ConnectionSocketFactory> socketFactoryRegistry = 
		        RegistryBuilder.<ConnectionSocketFactory> create()
		        .register("https", sslsf )
		        .register("http", new PlainConnectionSocketFactory() )
		        .build();
			
			//-------------------------------
			// Connection Manager
			BasicHttpClientConnectionManager trustAllAndClientCertConnectionManager =
						new BasicHttpClientConnectionManager(socketFactoryRegistry);
		
		//=====================================================
		// Add to Builder
		//=====================================================
	    builder.setConnectionManager(trustAllAndClientCertConnectionManager);

	}
	
	/******************************************************************************************************
	 * Inner Class for HTTP Response
	 ******************************************************************************************************/
	public class Response {
		
		private Logger responseLogger = (Logger) LoggerFactory.getLogger(Response.class.getName());
		
		CloseableHttpClient httpClient = null;
		private URL url;
		private String body;
		private int status = 500;
		private long duration = -1;
		
		private Header[] headers;
		private JsonObject headersAsJsonCached;
		
		private HSRRecord record = null;
		private boolean hasError = false;
		private boolean checksSuccess = true;
		private String errorMessage = null;
		
		
		/******************************************************************************************************
		 * empty response in case of errors, done to avoid null pointer exceptions.
		 ******************************************************************************************************/
		protected Response(PFRHttpRequestBuilder request) {
			this.hasError = true;
			this.checksSuccess = true;
		}

		/******************************************************************************************************
		 * 
		 ******************************************************************************************************/
		public Response(PFRHttpRequestBuilder request, CloseableHttpClient httpClient, HttpUriRequestBase requestBase, boolean autoCloseClient) {
			
			this.httpClient = httpClient;
			
			String metric = request.metricName;
			//----------------------------------
			// Get URL
			try {
				url = requestBase.getUri().toURL();
			} catch (Exception e) {
				hasError = true;
				logger.error("URL is malformed:"+e.getMessage(), e);
				return;
			}
			
			//----------------------------------
			// Send Request and Read Response
			long startMillis = System.currentTimeMillis();
			try {
				
				//--------------------------
				// Start Measurement
				if(metric != null) { HSR.start(metric); }
				
					//--------------------------
					// Execute Request
					Boolean success = httpClient.execute(requestBase, new HttpClientResponseHandler<Boolean>() {
						@Override
						public Boolean handleResponse(ClassicHttpResponse response) throws HttpException, IOException {
													
							if(response != null) {
								status = response.getCode();
								headers = response.getHeaders();
								
								HttpEntity entity = response.getEntity();
								if(entity != null) {
									body = EntityUtils.toString(response.getEntity());
								}
	
							}
							return true;
						}
					});
					
				//--------------------------
				// End Measurement	
				if(metric != null) { 
					record = HSR.end(status < 400); 
				}
				
				//--------------------------
				// Do Checks
				for(PFRHttpCheck check : request.checksList) {
					
					checksSuccess &= check.check(this);
					
					if(!checksSuccess) { 
						record.status(HSRRecordStatus.Failed); 
						break;
					}
				}
					
				
			} catch (IOException e) {
				
				if(metric != null) { record = HSR.end(false); }
				
				hasError = true;
				errorMessage = e.getMessage();
				responseLogger.warn("Exception during HTTP request:"+e.getMessage(), e);
				
			}finally {
				long endMillis = System.currentTimeMillis();
				duration = endMillis - startMillis;
				
				if(autoCloseClient) {
					close();
				}
			}
		}
		
		/******************************************************************************************************
		 * 
		 ******************************************************************************************************/
		public void close() {
			if(httpClient != null) {
				httpClient.close(CloseMode.IMMEDIATE);
			}
		}
		
		/******************************************************************************************************
		 * Set a custom status for the record.
		 ******************************************************************************************************/
		public void setStatus(HSRRecordStatus status) {
			if(record != null) {
				record.status(status);
			}
		}
		
		/************************************************************************
		 * Returns true if:
		 * 	- there was no error
		 *  - none of the checks failed
		 *  - all the checks where successful
		 ************************************************************************/
		public boolean isSuccess() {
			
			if(this.getStatus() >= 400
			|| this.hasError()
			|| this.checksSuccessful()
			){ 
				return false; 
			};
			
			return true;
		}
		
		/******************************************************************************************************
		 * Returns true if a check has failed.
		 ******************************************************************************************************/
		public boolean checksSuccessful() {
			return checksSuccess;
		}
		
		/******************************************************************************************************
		 * 
		 ******************************************************************************************************/
		public boolean hasError() {
			return hasError;
		}
		
		
		
		/******************************************************************************************************
		 * 
		 ******************************************************************************************************/
		public String errorMessage() {
			return errorMessage;
		}
		
		/******************************************************************************************************
		 * 
		 ******************************************************************************************************/
		public URL getURL() {
			return url;
		}
		
		/******************************************************************************************************
		 * Check if the response body contains a specific string.
		 * 
		 * @param contains the value that should be contained
		 * @param failMessage message to log if not found, 
		 ******************************************************************************************************/
		public boolean bodyContains(String value, String failMessage) {
			
			if(body == null) { return false; }
			
			if(body.contains(value)) { 
				return true; 
			}else{
				if(failMessage != null ) {	responseLogger.error(failMessage); }
				return false;
			}

		}
		
		/******************************************************************************************************
		 * Get the body content of the response.
		 * @return String or null on error
		 ******************************************************************************************************/
		public String getResponseBody() {
			return body;
		}
		
		/******************************************************************************************************
		 * Get the body content of the response as a JsonObject.
		 * @param url used for the request.
		 * @return JsonObject or null in case of issues
		 ******************************************************************************************************/
		public JsonElement getResponseBodyAsJsonElement(){
			
			//----------------------------------
			// Check Body
			if(Strings.isNullOrEmpty(body)) {
				responseLogger.error("Http Response was empty, cannot convert to a JsonElement.", new Exception());
				return null;
			}
			
			if(!body.trim().startsWith("{")
			&& !body.trim().startsWith("[")
			) {
				String messagePart = (body.length() <= 100) ? body : body.substring(0, 95)+"... (truncated)";
				responseLogger.error("Http Response was not a JsonElement: "+messagePart, new Exception());
				return null;
			}
			
			//----------------------------------
			// Create Object
			JsonElement jsonElement = PFR.JSON.fromJson(body);

			if(jsonElement == null) {
				responseLogger.error("Error occured while converting http response body to JSON Element.", new Exception());
			}
			
			return jsonElement;

		}
		
		/******************************************************************************************************
		 * Get the body content of the response as a JsonObject.
		 * @param url used for the request.
		 * @return JsonObject or null in case of issues
		 ******************************************************************************************************/
		public JsonObject getResponseBodyAsJsonObject(){
			
			//----------------------------------
			// Check Body
			if(Strings.isNullOrEmpty(body)) {
				responseLogger.error("Http Response was empty, cannot convert to a JsonElement.", new Exception());
				return null;
			}
			
			if(!body.trim().startsWith("{")) {
				String messagePart = (body.length() <= 100) ? body : body.substring(0, 95)+"... (truncated)";
				responseLogger.error("Http Response was not a JsonObject: "+messagePart, new Exception());
				return null;
			}
			
			//----------------------------------
			// Create Object
			JsonElement jsonElement = PFR.JSON.fromJson(body);
			JsonObject jsonObject = jsonElement.getAsJsonObject();
			
			if(jsonObject == null) {
				responseLogger.error("Error occured while converting http response body to JSON Object.", new Exception());
			}
			
			return jsonObject;

		}
		
		/******************************************************************************************************
		 * Get the body content of the response as a JsonArray.
		 * @param url used for the request.
		 * @return JsonArray never null, empty array on error
		 ******************************************************************************************************/
		public JsonArray getResponseBodyAsJsonArray(){
			
			//----------------------------------
			// Check Body
			if(Strings.isNullOrEmpty(body)) {
				responseLogger.error("Http Response was empty, cannot convert to JSON.", new Exception());
				return null;
			}
			
			if(!body.trim().startsWith("{")
			&& !body.trim().startsWith("[")
			) {
				String messagePart = (body.length() <= 100) ? body : body.substring(0, 95)+"... (truncated)";
				responseLogger.error("Http Response was not JSON: "+messagePart, new Exception());
				return null;
			}
			
			//----------------------------------
			// CreateArray
			JsonArray jsonArray = new JsonArray();
			
			JsonElement jsonElement = PFR.JSON.fromJson(body);
			if(jsonElement == null || jsonElement.isJsonNull()) {
				return jsonArray;
			}
			
			if(jsonElement.isJsonArray()) {
				jsonArray = jsonElement.getAsJsonArray();
			}else if(jsonElement.isJsonObject()) {
				JsonObject object = jsonElement.getAsJsonObject();
				if(object.get("error") != null) {
					logger.error("Error occured while reading http response: "+object.get("error").toString());
					return jsonArray;
				}else {
					logger.error("Error occured while reading http response:"+PFR.JSON.toString(jsonElement));
				}
			}
			
			return jsonArray;
		}

		/******************************************************************************************************
		 * 
		 ******************************************************************************************************/
		public int getStatus() {
			return status;
		}
		

		/******************************************************************************************************
		 * Returns the approximate duration that was needed for executing and reading the request.
		 ******************************************************************************************************/
		public long getDuration() {
			return duration;
		}
		
		/******************************************************************************************************
		 * 
		 ******************************************************************************************************/
		public Header[] getHeaders() {
			return headers;
		}
		
		/******************************************************************************************************
		 * 
		 ******************************************************************************************************/
		public JsonObject getHeadersAsJson() {
			
			if(headersAsJsonCached == null) {
				JsonObject object = new JsonObject();
				for(Header entry : headers) {
					
					if(entry.getName() != null) {
						object.addProperty(entry.getName(), entry.getValue());
					}
				}
				
				headersAsJsonCached = object;
			}
			
			return headersAsJsonCached;
		}
		
	}
	
	protected class PFRProxy {
		public String type;
		public String host;
		public int port = 80;
		
	}
}
