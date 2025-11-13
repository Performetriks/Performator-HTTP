package com.performetriks.performator.http;

import com.performetriks.performator.base.PFRContext;
import com.performetriks.performator.http.PFRHttp.PFRHttpSection;
import com.xresch.hsr.base.HSR;
import com.xresch.hsr.utils.HSRText.CheckType;

/***************************************************************************
 * 
 * Copyright Owner: Performetriks GmbH, Switzerland
 * License: Eclipse Public License v2.0
 * 
 * @author Reto Scheiwiller
 * 
 ***************************************************************************/
public class PFRHttpCheck {
	
	private CheckType checkType;
	private PFRHttpSection section = PFRHttpSection.BODY;
	private String messageOnFail = null;
	
	private String valueToCheck = null;
	
	private String headerName = null;
	private boolean appendLogDetails = true;
	
	/***********************************************
	 * 
	 ***********************************************/
	public PFRHttpCheck(CheckType checkType) {
		this.checkType = checkType;
	}
	
	/***********************************************
	 * Set if logDetails retrieved from PFRContext
	 * should be appended to log messages when a check
	 * fails. 
	 ***********************************************/
	public PFRHttpCheck appendLogDetails(boolean appendLogDetails) {
		this.appendLogDetails = appendLogDetails;
		return this;
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
	 * Set to check the body
	 ***********************************************/
	public PFRHttpCheck checkStatus(int valueToCheck) {
		section = PFRHttpSection.STATUS;
		this.valueToCheck = ""+valueToCheck;
		
		return this;
	}
	
	/***********************************************
	 * Define a custom log message for when the check fails
	 ***********************************************/
	public PFRHttpCheck messageOnFail(String message) {
		this.messageOnFail = message;
		return this;
	}
	
	/***********************************************
	 * 
	 ***********************************************/
	public boolean check(PFRHttpResponse r) {
		
		if(r == null 
		|| r.hasError
		){ return false; }
		
		switch (section) {
			case BODY: 		return checkBody(r);
			case HEADER:	return checkHeader(r);
			case STATUS:	return checkStatus(r);
			default: 		return false;
		}

	}
	
	/***********************************************
	 * 
	 ***********************************************/
	private boolean checkBody(PFRHttpResponse r) {
		
		boolean success = HSR.Text.checkTextForContent(checkType, r.body, valueToCheck);
		
		if(!success) { logMessage(r); }
		
		return success;
	}
	
	/***********************************************
	 * 
	 ***********************************************/
	private boolean checkHeader(PFRHttpResponse r) {
		
		String headerValue = r.getHeadersAsJson().get(headerName).getAsString();

		boolean success = HSR.Text.checkTextForContent(checkType, headerValue, valueToCheck);
		
		if(!success) { logMessage(r); }
		
		return success;
	}
	
	/***********************************************
	 * 
	 ***********************************************/
	private boolean checkStatus(PFRHttpResponse r) {
		
		boolean success = HSR.Text.checkTextForContent(checkType, r.getStatus()+"", valueToCheck);
		
		if(!success) { logMessage(r); }
		
		return success;
	}
	
	/***********************************************
	 * 
	 ***********************************************/
	private void logMessage(PFRHttpResponse r) {

		String finalMessage = (messageOnFail != null) ? messageOnFail : createDefaultMessage();
		
		if(appendLogDetails) {
			finalMessage += PFRContext.logDetailsString();
		}
		
		r.responseLogger.error(finalMessage);
		
		HSR.addErrorMessage(finalMessage).parent(r.record);
		
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