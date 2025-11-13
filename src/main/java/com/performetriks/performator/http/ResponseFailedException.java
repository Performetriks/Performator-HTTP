package com.performetriks.performator.http;

/***************************************************************************
 * 
 * Copyright Owner: Performetriks GmbH, Switzerland
 * License: Eclipse Public License v2.0
 * 
 * @author Reto Scheiwiller
 * 
 ***************************************************************************/
public class ResponseFailedException extends Throwable {

	private static final long serialVersionUID = 1L;
	
	private PFRHttpResponse response;
	
	public ResponseFailedException(PFRHttpResponse r) {
		response = r;
	}
	
	public PFRHttpResponse getResponse() {
		return response;
	}
}
