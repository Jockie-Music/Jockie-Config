package com.jockie.config.impl.wrapper;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * When a field or method in a config is annotated with
 * {@link Ignore @Ignore} it will not be considered a config
 * property but instead just a user defined variable and will
 * therefore not be auto-populated with a value from the config.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface Ignore {}