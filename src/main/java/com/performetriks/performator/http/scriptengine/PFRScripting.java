package com.performetriks.performator.http.scriptengine;

import java.util.List;

import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;

/***************************************************************************
 * 
 * Copyright Owner: Performetriks GmbH, Switzerland
 * License: Eclipse Public License v2.0
 * 
 * @author Reto Scheiwiller
 * 
 ***************************************************************************/
public class PFRScripting {
	
	private static final Logger logger = (ch.qos.logback.classic.Logger)LoggerFactory.getLogger(PFRScripting.class.getName());
	
	private static ScriptEngineManager manager = new ScriptEngineManager();
	
	/******************************************************************************************************
	 * Print all available scripting engines. 
	 ******************************************************************************************************/
	public static void printAvailableEngines() {

	    List<ScriptEngineFactory> factories = manager.getEngineFactories();
	   
	    for (ScriptEngineFactory factory : factories){
			System.out.println("ScriptEngineFactory Info");
			String engName = factory.getEngineName();
			String engVersion = factory.getEngineVersion();
			String langName = factory.getLanguageName();
			String langVersion = factory.getLanguageVersion();
			System.out.printf("\tScript Engine: %s (%s, %s, %s)\n", engName, engVersion, langName, langVersion);
			List<String> engNames = factory.getNames();
			for (String name : engNames)
			{
			   System.out.printf("\tEngine Alias: %s\n", name);
			}
			System.out.printf("\tLanguage: %s (%s)\n", langName, langVersion);
	    }
	}

	/******************************************************************************************************
	 *  Create a new Javascript engine.
	 * 
	 * @param the name of the engine to use, e.g. Nashorn. Use printAvailableEngines() for a list of 
	 * available engines.
	 ******************************************************************************************************/
	public static PFRScriptingContext createContext(String language) {
		
		//-----------------------------------
		// Create Engine

		Context context = Context.newBuilder(language)
		        .allowHostClassLookup(s -> true)
		        .allowHostAccess(HostAccess.ALL)
		        .build();

		return new PFRScriptingContext(language, context);
		
	}
	/******************************************************************************************************
	 *  Create a new Javascript engine.
	 * 
	 * @param the name of the engine to use, e.g. Nashorn. Use printAvailableEngines() for a list of 
	 * available engines.
	 ******************************************************************************************************/
	public static PFRScriptingContext createJavascriptContext() {
		
		return createContext("js");
		
	}
	
	/******************************************************************************************************
	 *  Create a new Javascript engine.
	 * 
	 * @param the name of the engine to use, e.g. Nashorn. Use printAvailableEngines() for a list of 
	 * available engines.
	 ******************************************************************************************************/
	public static PFRScriptingContext createPolyglotJavascript(Object objectToBind) {
		
		return createPolyglotWithAdditionalBindings("js", objectToBind);
		
	}
	/******************************************************************************************************
	 * Execute a javascript call with defined parameters.
	 * 
	 * @param the name of the engine to use, e.g. Nashorn. Use printAvailableEngines() for a list of 
	 * available engines.
	 ******************************************************************************************************/
	private static PFRScriptingContext createPolyglotWithAdditionalBindings(String language, Object objectToBind) {
		
		//-----------------------------------
		// Create Engine

		PFRScriptingContext polyglot = createContext(language);

		polyglot.getBindings().putMember(objectToBind.getClass().getSimpleName(), objectToBind);

		return polyglot;
		
		
	}
	
}
