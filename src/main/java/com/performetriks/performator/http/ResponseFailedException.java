package com.performetriks.performator.http;

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
