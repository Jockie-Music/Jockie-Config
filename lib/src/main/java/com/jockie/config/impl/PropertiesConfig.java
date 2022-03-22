package com.jockie.config.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class PropertiesConfig extends MapConfig {
	
	public PropertiesConfig(Properties properties) {
		super(PropertiesConfig.toMap(properties));
	}
	
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Map<String, Object> toMap(Properties properties) {
		return new HashMap<>((Map) properties);
	}
}