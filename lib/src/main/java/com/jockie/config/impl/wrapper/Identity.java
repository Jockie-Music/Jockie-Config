package com.jockie.config.impl.wrapper;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.jockie.config.IConfig;

/**
 * When a field or method in a config is annotated with
 * {@link Identity @Identity} it will be auto-populated
 * with the backing {@link IConfig}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface Identity {}