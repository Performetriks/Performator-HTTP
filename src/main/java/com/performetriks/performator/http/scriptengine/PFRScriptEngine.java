package com.performetriks.performator.http.scriptengine;


import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PFRScriptEngine {
	
	private ScriptEngine engine = null;
	private static final Logger logger = LoggerFactory.getLogger(PFRScriptEngine.class.getName());
	
	public PFRScriptEngine(ScriptEngine engine) {
		this.engine = engine;
	}
	
	/******************************************************************************************************
	 * Add the defined script to this engine.
	 * 
	 * @param script that should be loaded containing the methods that should be executed.
	 * @param methodName the name of the method to be executed
	 * @param parameters values to be passed to method.
	 ******************************************************************************************************/
	public PFRScriptEngine addScript(String script) {
		try {
			engine.eval(script);
		} catch (ScriptException e) {
			logger.error("An exception occured while executing a javascript: "+e.getMessage(), e);
		}
		return this;
	}
	
	/******************************************************************************************************
	 * Execute a javascript call with defined parameters.
	 * 
	 * @param script that should be loaded containing the methods that should be executed.
	 * @param methodName the name of the method to be executed
	 * @param parameters values to be passed to method.
	 ******************************************************************************************************/
	public Object executeJavascript(String methodName, Object... parameters) {
		
		//-----------------------------------
		// Create Invocable
		Invocable invocableEngine = (Invocable) engine;
		
		//-----------------------------------
		// Execute Script Engine
		try {
			Object result = invocableEngine.invokeFunction(methodName, parameters);
			
			return result;
			
		} catch (NoSuchMethodException e) {
			logger.error("The method '"+methodName+"' doesn't exist. ", e);
		} catch (ScriptException e) {
			logger.error("An exception occured while executing a javascript: "+e.getMessage(), e);
				e.printStackTrace();
		}
		
		return null;
	}
	/******************************************************************************************************
	 * Execute a javascript call with defined parameters.
	 * 
	 * @param script that should be loaded containing the methods that should be executed.
	 * @param methodCallWithParams a string representation of the function call, e.g. "foobar('Test')"
	 ******************************************************************************************************/
	public Object executeJavascript(String methodCallWithParams) {
				
		//-----------------------------------
		// Execute Script Engine
		try {
			Object result = engine.eval(methodCallWithParams);
			
			return result;
			
		} catch (ScriptException e) {
			logger.error("An exception occured while executing a javascript: "+e.getMessage(), e);
			e.printStackTrace();
		}
		
		return null;
	}
	
}
