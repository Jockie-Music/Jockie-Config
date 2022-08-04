package com.jockie.config.impl.wrapper;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * When a field or method in a config is annotated with
 * {@link Parent @Parent} it will be auto-populated with
 * the config object's parent config object.
 * 
 * This works recursively, returning null if there is
 * no matching parent for the defined type.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface Parent {}