package com.jockie.config.impl;

public class EnvironmentVariablesConfig extends MapConfig {

	public static final EnvironmentVariablesConfig INSTANCE = new EnvironmentVariablesConfig();
	
	private EnvironmentVariablesConfig() {
		super(System.getenv());
	}
	
//	/* TODO: lower-case keys? */
//	private static Map<String, Object> create(String splitBy) {
//		Map<String, Object> config = new HashMap<>(System.getenv());
//		if(splitBy == null || splitBy.isEmpty()) {
//			return config;
//		}
//		
//		return MapUtility.expand(config, splitBy);
//	}
//	
//	public EnvironmentVariablesConfig() {
//		this(null);
//	}
//	
//	public EnvironmentVariablesConfig(String splitBy) {
//		super(EnvironmentVariablesConfig.create(splitBy));
//	}
}