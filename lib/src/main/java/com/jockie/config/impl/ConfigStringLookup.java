package com.jockie.config.impl;

import org.apache.commons.text.lookup.StringLookup;

import com.jockie.config.IConfig;

public class ConfigStringLookup implements StringLookup {
	
	protected final IConfig config;
	
	public ConfigStringLookup(IConfig config) {
		this.config = config;
	}
	
	@Override
	public String lookup(String key) {
		return this.config.getString(key);
	}
}