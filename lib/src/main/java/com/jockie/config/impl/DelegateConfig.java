package com.jockie.config.impl;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jockie.config.IConfig;

public class DelegateConfig implements IConfig {
	
	protected final IConfig delegate;
	
	public DelegateConfig(IConfig config) {
		this.delegate = config;
	}
	
	@Override
	public IConfig merge(IConfig... configs) {
		return this.delegate.merge(configs);
	}
	
	@Override
	public IConfig resolve(IConfig config) {
		return this.delegate.resolve(config);
	}
	
	@Override
	public Map<String, Object> asMap() {
		return this.delegate.asMap();
	}
	
	@Override
	public Set<String> keys() {
		return this.delegate.keys();
	}
	
	@Override
	public boolean has(String key) {
		return this.delegate.has(key);
	}
	
	@Override
	public <T> T get(String key, Class<T> type, T defaultValue) {
		return this.delegate.get(key, type, defaultValue);
	}
	
	@Override
	public <T> List<T> getList(String key, Class<T> elementType, List<T> defaultValue) {
		return this.delegate.getList(key, elementType, defaultValue);
	}
	
	@Override
	public <K, V> Map<K, V> getMap(String key, Class<K> keyType, Class<V> valueType, Map<K, V> defaultValue) {
		return this.delegate.getMap(key, keyType, valueType, defaultValue);
	}
}