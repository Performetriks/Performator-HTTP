package com.performetriks.performator.http;

import com.performetriks.performator.http.PFRHttp.Response;

public class ResponseFailedException extends Throwable {

	private static final long serialVersionUID = 1L;
	
	private Response response;
	
	public ResponseFailedException(Response r) {
		response = r;
	}
	
	public Response getResponse() {
		return response;
	}
}
