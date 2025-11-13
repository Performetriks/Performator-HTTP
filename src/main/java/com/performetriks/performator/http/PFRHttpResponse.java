package com.performetriks.performator.http;

import java.io.IOException;
import java.net.URL;

import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.io.CloseMode;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.performetriks.performator.base.PFR;
import com.xresch.hsr.base.HSR;
import com.xresch.hsr.stats.HSRRecord;
import com.xresch.hsr.stats.HSRRecord.HSRRecordStatus;

import ch.qos.logback.classic.Logger;

/******************************************************************************************************
 * Inner Class for HTTP Response
 ******************************************************************************************************/
public class PFRHttpResponse {
	
	Logger responseLogger = (Logger) LoggerFactory.getLogger(PFRHttpResponse.class.getName());
	
	private PFRHttpRequestBuilder request;
	CloseableHttpClient httpClient = null;
	private URL url;
	String body;
	private int status = 500;
	private long duration = -1;
	
	private Header[] headers;
	private JsonObject headersAsJsonCached;
	
	HSRRecord record = null;
	boolean hasError = false;
	private boolean checksSuccessful = true;
	private String errorMessage = null;
	
	
	/******************************************************************************************************
	 * empty response in case of errors, done to avoid null pointer exceptions.
	 ******************************************************************************************************/
	protected PFRHttpResponse(PFRHttpRequestBuilder request) {
		this.hasError = true;
		this.checksSuccessful = false;
	}

	/******************************************************************************************************
	 * 
	 ******************************************************************************************************/
	public PFRHttpResponse(PFRHttpRequestBuilder request, CloseableHttpClient httpClient, HttpUriRequestBase requestBase, boolean autoCloseClient) {
		
		this.request = request;
		this.httpClient = httpClient;
		
		String metric = request.metricName;
		//----------------------------------
		// Get URL
		try {
			url = requestBase.getUri().toURL();
		} catch (Exception e) {
			hasError = true;
			PFRHttp.logger.error("URL is malformed:"+e.getMessage(), e);
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
				
				checksSuccessful &= check.check(this);
				
				if(!checksSuccessful) { 
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
			
			if(PFRHttp.debugLogAll.get()
			|| PFRHttp.debugLogFail.get() && !this.isSuccess()) {
				printDebugLog();
			}
				
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
	public void printDebugLog() {
		
		String paramsString = (request.params == null) ? "null" : Joiner.on("&").withKeyValueSeparator("=").join(request.params);
		String headersString = (request.headers == null) ? "null" : Joiner.on(",").withKeyValueSeparator("=").join(request.headers);
		
		StringBuilder builder = new StringBuilder();
		builder.append("\n====================================== Debug Log ======================================");
		builder.append("\nMetric Name: "+request.metricName);
		builder.append("\n---------------- REQUEST ----------------");
		builder.append("\nURL:         "+request.URL);
		builder.append("\nMethod:      "+request.method);
		builder.append("\nParams:      "+paramsString);
		builder.append("\nHeaders:     "+headersString);
		builder.append("\nBody:\n"+request.requestBody);
		builder.append("\n---------------- RESPONSE ----------------");
		builder.append("\nStatus:     "+this.getStatus());
		builder.append("\nChecks OK:  "+this.checksSuccessful());
		builder.append("\nHas Error:  "+this.hasError());
		builder.append("\nError:      "+this.errorMessage());
		builder.append("\nDuration:   "+this.getDuration());
		builder.append("\nHeaders:    "+this.getHeadersAsJson().toString());
		builder.append("\nBody:\n"+this.body);

		builder.append("\n========================================================================================");
		
		PFRHttp.logger.error(builder.toString());
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
		|| !this.checksSuccessful()
		){ 
			return false; 
		};
		
		return true;
	}
	
	/******************************************************************************************************
	 * If the response has an error, this method will throw an exception. This can be used to work with 
	 * try-catch mechanisms to simplify code structures.
	 * The response that failed can be retrieved with ResponseFailedException.getResponse().
	 ******************************************************************************************************/
	public PFRHttpResponse throwOnFail() throws ResponseFailedException {
		
		if(!isSuccess()) {
			throw new ResponseFailedException(this);
		}
		
		return this;
	}
	
	/******************************************************************************************************
	 * Returns true if a check has failed.
	 ******************************************************************************************************/
	public boolean checksSuccessful() {
		return checksSuccessful;
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
				PFRHttp.logger.error("Error occured while reading http response: "+object.get("error").toString());
				return jsonArray;
			}else {
				PFRHttp.logger.error("Error occured while reading http response:"+PFR.JSON.toString(jsonElement));
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