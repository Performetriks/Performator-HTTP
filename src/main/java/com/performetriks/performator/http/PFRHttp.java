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
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map.Entry;

import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.cookie.BasicClientCookie;
import org.apache.hc.client5.http.impl.io.BasicHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.routing.DefaultProxyRoutePlanner;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.graalvm.polyglot.Value;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.performetriks.performator.http.scriptengine.PFRScripting;
import com.performetriks.performator.http.scriptengine.PFRScriptingContext;
import com.xresch.hsr.base.HSR;
import com.xresch.hsr.utils.HSRTime.HSRTimeUnit;

import ch.qos.logback.classic.Logger;

/***************************************************************************
 * 
 * Copyright Owner: Performetriks GmbH, Switzerland
 * License: Eclipse Public License v2.0
 * 
 * @author Reto Scheiwiller
 * 
 ***************************************************************************/
public class PFRHttp {
	
	static Logger logger = (ch.qos.logback.classic.Logger)LoggerFactory.getLogger(PFRHttp.class.getName());
				

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
	
	static InheritableThreadLocal<BasicCookieStore> cookieStore = new InheritableThreadLocal<>() { 
		@Override
	    protected BasicCookieStore initialValue() {
	        return new BasicCookieStore();
	    }
	};
	
	private static InheritableThreadLocal<Long> defaultResponseTimeoutMillis =  new InheritableThreadLocal<>() { 
		@Override
	    protected Long initialValue() {
			//default timeout of  3 minutes
	        return HSRTimeUnit.m.toMillis(3);
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
	 * Set the default response timeout used for all the requests of the current user(thread).
	 ******************************************************************************************************/
	public static void defaultResponseTimeout(long millis) {
		defaultResponseTimeoutMillis.set(millis);
	}
	
	/******************************************************************************************************
	 * <b>Scope:</b> Propagated (Inheritable Thread Local) <br>
	 * Returns the default response timeout for all the requests of the current user(thread).
	 ******************************************************************************************************/
	public static long defaultResponseTimeout() {
		return defaultResponseTimeoutMillis.get();
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
	public static void setSSLContext(HttpClientBuilder builder) throws Exception {
		
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
	
	protected class PFRProxy {
		public String type;
		public String host;
		public int port = 80;
		
	}
}
