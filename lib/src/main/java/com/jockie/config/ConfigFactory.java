package com.jockie.config;

import java.util.Collections;
import java.util.Map;
import java.util.Properties;

import com.jockie.config.impl.EnvironmentVariablesConfig;
import com.jockie.config.impl.MapConfig;
import com.jockie.config.impl.PropertiesConfig;
import com.jockie.config.impl.SystemPropertiesConfig;

public class ConfigFactory {
	
	public static final MapConfig EMPTY = new MapConfig(Collections.emptyMap());
	
	public static MapConfig empty() {
		return ConfigFactory.EMPTY;
	}
	
	public static EnvironmentVariablesConfig environmentVariables() {
		return EnvironmentVariablesConfig.INSTANCE;
	}
	
	public static SystemPropertiesConfig systemProperties() {
		return new SystemPropertiesConfig();
	}
	
	public static IConfig systemProperties(String prefix) {
		return new SystemPropertiesConfig().getConfig(prefix, ConfigFactory.empty());
	}
	
	public static MapConfig fromProperties(Properties properties) {
		return new PropertiesConfig(properties);
	}
	
	public static MapConfig fromMap(Map<String, ?> map) {
		return new MapConfig(map);
	}
}