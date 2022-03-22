package com.jockie.config.impl.field;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Name {
	
	/* 
	 * TODO: Add the ability to have multiple names,
	 * where it checks them in order and uses the first one found.
	 */
	public String value();
	
}