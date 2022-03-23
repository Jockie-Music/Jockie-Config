package com.jockie.config.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.text.StringSubstitutor;

import com.jockie.config.IConfig;
import com.jockie.config.utility.DataTypeUtility;
import com.jockie.config.utility.MapUtility;

public class MapConfig implements IConfig {
	
	protected final Map<String, Object> map;
	
	protected MapConfig(Map<String, ?> config, boolean update, boolean clone, boolean expand) {
		Map<String, Object> map = DataTypeUtility.cast(Objects.requireNonNull(config));
		
		if(!update) {
			this.map = map;
			
			return;
		}
		
		/* 
		 * TODO: There's a lot of cloning mostly the same map/collection objects,
		 * deepClone, expandClone, substituteClone, deepCloneUnmodifiable, it should not be
		 * too big of a deal since you're not meant to be creating configs thousands of times
		 * per second but it's worth considering as a potential point of optimization in the future
		 * if it's ever necessary.
		 */
		
		/* Clones all maps and collections */
		if(clone) {
			map = MapUtility.deepClone(map);
		}
		
		/* Converts all dot paths to full paths, "x.y=1" becomes "{x: {y: 1}}" */
		if(expand) {
			map = MapUtility.expandClone(map);
		}
		
		/* Clones all the maps and collections and returns them as unmodifiable */
		map = MapUtility.deepCloneUnmodifiable(map);
		
		this.map = map;
	}
	
	protected MapConfig(Map<String, ?> config, boolean update) {
		this(config, update, true, true);
	}
	
	public MapConfig(Map<String, ?> config) {
		this(config, true);
	}
	
	@SuppressWarnings("unchecked")
	protected IConfig convertToConfig(Object value) {
		if(value instanceof Map) {
			return new MapConfig((Map<String, ?>) value, false);
		}
		
		throw new IllegalArgumentException("Unable to convert value: " + value + " (of type: " + (value != null ? value.getClass() : null) + "), to: " + IConfig.class);
	}
	
	protected Object convertValue(Object value, Class<?> type) {	
		if(type == IConfig.class) {
			return this.convertToConfig(value);
		}
		
		return DataTypeUtility.convert(value, type);
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public <T> List<T> getList(String key, Class<T> elementType, List<T> defaultValue) {
		List<Object> list = this.get(key, List.class);
		if(list == null) {
			return defaultValue;
		}
		
		List<T> result = new ArrayList<>(list.size());
		for(Object object : list) {
			result.add((T) this.convertValue(object, elementType));
		}
		
		return result;
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public <K, V> Map<K, V> getMap(String key, Class<K> keyType, Class<V> valueType, Map<K, V> defaultValue) {
		Map<Object, Object> map = this.get(key, Map.class);
		if(map == null) {
			return defaultValue;
		}
		
		Map<K, V> result = new HashMap<>(map.size());
		for(Entry<Object, Object> entry : map.entrySet()) {
			K newKey = (K) this.convertValue(entry.getKey(), keyType);
			V newValue = (V) this.convertValue(entry.getValue(), valueType);
			
			result.put(newKey, newValue);
		}
		
		return result;
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public boolean has(String key) {
		Map<String, Object> root = this.map;
		
		String[] parts = key.split("\\.");
		for(int i = 0; i < parts.length; i++) {
			String part = parts[i];
			if(!root.containsKey(part)) {
				return false;
			}
			
			if(i == parts.length - 1) {
				return true;
			}
			
			Object object = root.get(part);
			if(!(object instanceof Map)) {
				return false;
			}
			
			root = (Map<String, Object>) object;
		}
		
		return false;
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public <T> T get(String key, Class<T> type, T defaultValue) {
		Map<String, Object> root = this.map;
		
		String[] path = key.split("\\.");
		for(int i = 0; i < path.length; i++) {
			String part = path[i];
			if(!root.containsKey(part)) {
				return defaultValue;
			}
			
			Object object = root.get(part);
			if(i == path.length - 1) {
				return (T) this.convertValue(object, type);
			}
			
			if(!(object instanceof Map)) {
				return defaultValue;
			}
			
			root = (Map<String, Object>) object;
		}
		
		return defaultValue;
	}
	
	@Override
	public IConfig merge(IConfig... configs) {
		for(IConfig config : configs) {
			if(!(config instanceof MapConfig)) {
				throw new UnsupportedOperationException();
			}
		}
		
		Map<String, Object> result = MapUtility.deepClone(this.map);
		for(IConfig config : configs) {
			Map<String, Object> other = MapUtility.deepClone(((MapConfig) config).map);
			MapUtility.deepMerge(result, other, false);
		}
		
		/* Already cloned and expanded previously */
		return new MapConfig(result, true, false, false);
	}
	
	@Override
	public IConfig resolve(IConfig config) {
		StringSubstitutor substitutor = new StringSubstitutor(new ConfigStringLookup(config));
		
		/* Replaces all template variables "${x}" with the real values */
		return new MapConfig(MapUtility.substituteClone(this.map, substitutor), true, false, false);
	}
	
	/**
	 * @return the backing map, which is deeply unmodifiable
	 * (all maps and collections stored in the map are unmodifiable)
	 */
	@Override
	public Map<String, Object> asMap() {
		return this.map;
	}
	
	@Override
	public Set<String> keys() {
		return this.map.keySet();
	}
	
	@Override
	public String toString() {
		/* 
		 * TODO: Should we return the config in a JSON format?
		 * 
		 * Reasoning behind making it JSON is that it's easier to pretty-format
		 * and more "well known"
		 */
		return this.map.toString();
	}
}