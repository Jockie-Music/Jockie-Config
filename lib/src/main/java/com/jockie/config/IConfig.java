package com.jockie.config;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jockie.config.utility.DataTypeUtility;

public interface IConfig {
	
	/**
	 * Merge multiple configs, they are merged in the order specified,
	 * meaning a value specified in the first config will be overwritten
	 * by the second config if present
	 */
	public IConfig merge(IConfig... configs);
	
	/**
	 * Resolves all of the substituted values, for instance, "${x}"
	 */
	public IConfig resolve(IConfig config);
	
	/**
	 * @see #resolve(IConfig)
	 */
	public default IConfig resolve() {
		return this.resolve(this);
	}
	
	public Map<String, Object> asMap();
	public Set<String> keys();
	
	public boolean has(String key);
	
	public <T> T get(String key, Class<T> type, T defaultValue);
	
	public <T> List<T> getList(String key, Class<T> elementType, List<T> defaultValue);
	
	public default <T> Set<T> getSet(String key, Class<T> elementType, Set<T> defaultValue) {
		List<T> list = this.getList(key, elementType, null);
		if(list == null) {
			return defaultValue;
		}
		
		return new HashSet<>(list);
	}
	
	public <K, V> Map<K, V> getMap(String key, Class<K> keyType, Class<V> valueType, Map<K, V> defaultValue);
	
	public default <T> T get(String key, Class<T> type) {
		return this.get(key, type, DataTypeUtility.getDefaultValue(type));
	}
	
	@SuppressWarnings("unchecked")
	public default <T> T get(String key, T defaultValue) {
		return this.get(key, (Class<T>) (defaultValue != null ? defaultValue.getClass() : null), defaultValue);
	}
	
	public default <T> T get(String key) {
		return this.get(key, null, (T) null);
	}
	
	public default <K, V> Map<K, V> getMap(String key, Class<K> keyType, Class<V> valueType) {
		return this.getMap(key, keyType, valueType, Collections.emptyMap());
	}
	
	public default <V> Map<String, V> getMap(String key, Class<V> valueType, Map<String, V> defaultValue) {
		return this.getMap(key, String.class, valueType, defaultValue);
	}
	
	public default <V> Map<String, V> getMap(String key, Class<V> valueType) {
		return this.getMap(key, String.class, valueType, Collections.emptyMap());
	}
	
	public default <T> List<T> getList(String key, Class<T> elementType) {
		return this.getList(key, elementType, Collections.emptyList());
	}
	
	public default <T> Set<T> getSet(String key, Class<T> elementType) {
		return this.getSet(key, elementType, Collections.emptySet());
	}
	
	public default IConfig getConfig(String key) {
		return this.get(key, IConfig.class);
	}
	
	public default IConfig getConfig(String key, IConfig defaultValue) {
		return this.get(key, IConfig.class, defaultValue);
	}
	
	public default <T extends Enum<T>> T getEnum(String key, Class<T> type) {
		return this.get(key, type);
	}
	
	@SuppressWarnings("unchecked")
	public default <T extends Enum<T>> T getEnum(String key, T defaultValue) {
		if(defaultValue == null) {
			/*
			 * This is because we are unable to get the class from a null value meaning
			 * we have no idea which Enum to read it as
			 */
			throw new IllegalArgumentException("The default value may not be null for an Enum value if no class is provided, use #getEnum(String, Class<T>, T) instead");
		}
		
		return this.getEnum(key, (Class<T>) defaultValue.getClass(), defaultValue);
	}
	
	public default <T extends Enum<T>> T getEnum(String key, Class<T> type, T defaultValue) {
		return this.get(key, type, defaultValue);
	}
	
	public default String getString(String key) {
		return this.get(key, String.class);
	}
	
	public default String getString(String key, String defaultValue) {
		return this.get(key, String.class, defaultValue);
	}
	
	public default long getLong(String key) {
		return this.get(key, long.class);
	}
	
	public default long getLong(String key, long defaultValue) {
		return this.get(key, long.class, defaultValue);
	}
	
	public default int getInt(String key) {
		return this.get(key, int.class);
	}
	
	public default int getInt(String key, int defaultValue) {
		return this.get(key, int.class, defaultValue);
	}
	
	public default boolean getBoolean(String key) {
		return this.get(key, boolean.class);
	}
	
	public default boolean getBoolean(String key, boolean defaultValue) {
		return this.get(key, boolean.class, defaultValue);
	}
}