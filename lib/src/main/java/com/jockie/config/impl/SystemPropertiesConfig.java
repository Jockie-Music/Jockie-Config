package com.jockie.config.impl;

public class SystemPropertiesConfig extends PropertiesConfig {
	
	public SystemPropertiesConfig() {
		super(System.getProperties());
	}
}