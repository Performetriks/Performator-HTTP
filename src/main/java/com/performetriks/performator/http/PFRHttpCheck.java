package com.performetriks.performator.http;

import com.performetriks.performator.http.PFRHttp.PFRHttpSection;
import com.xresch.hsr.base.HSR;
import com.xresch.hsr.utils.HSRText.CheckType;

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
	public boolean check(PFRHttpResponse r) {
		
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
	private void logMessage(PFRHttpResponse r) {

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