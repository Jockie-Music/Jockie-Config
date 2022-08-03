package com.jockie.config.impl.wrapper;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Config {
	
	/**
	 * @return whether or not all fields in the class should be
	 * required to be final, to ensure the class (config) is immutable.
	 * 
	 * If a non-final field is found when this is set to true it will throw
	 * an exception during the creation of the class.
	 */
	public boolean requireFinal() default true;
	
	/**
	 * @return the naming type of the properties, this is used to properly convert
	 * a getter method or field name to the property name, for instance, using
	 * {@link Naming#SNAKE_CASE} would convert "getApiKey" and "apiKey" to "api_key"
	 */
	public Naming naming() default Naming.CAMEL_CASE;
	
}