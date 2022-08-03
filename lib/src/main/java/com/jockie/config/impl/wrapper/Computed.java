package com.jockie.config.impl.wrapper;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * When a default method in an interface config is annotated
 * with {@link Computed @Computed} the method will only be called once on
 * initialization and the value will be cached for all subsequent
 * calls.
 * <br><br>
 * This means you can dynamically create a Map or similar from the
 * existing config data which will only be created once, making it
 * perfect for performance.
 * <br><br>
 * Example
 * <pre>
 * public List&#60;String&#62; getKeys();
 * 
 * &#64;Computed
 * public default Map&#60;String, String&#62; getKeyMap() {
 * 	return this.getKeys().stream()
 * 		.map((key) -> key.split(":"))
 * 		.collect(Collectors.toMap((parts) -> parts[0], (parts) -> parts[1]));
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Computed {}