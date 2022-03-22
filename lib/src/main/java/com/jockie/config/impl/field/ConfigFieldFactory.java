package com.jockie.config.impl.field;

import com.jockie.config.IConfig;

public class ConfigFieldFactory {
	
    @SuppressWarnings("unchecked")
	public static <T> T create(IConfig config, Class<T> clazz) {
        return (T) AbstractFieldConfig.createInternal(null, config, clazz);
    }
}