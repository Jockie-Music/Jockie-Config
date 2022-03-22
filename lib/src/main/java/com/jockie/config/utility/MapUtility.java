package com.jockie.config.utility;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.text.StringSubstitutor;

public class MapUtility {
	
	@SuppressWarnings("unchecked")
	public static <T> Collection<T> substituteClone(Collection<T> collection, StringSubstitutor substitutor) {
		Collection<T> result = new ArrayList<>(collection.size());
		for(T value : collection) {
			if(value instanceof Map) {
				result.add((T) MapUtility.substituteClone((Map<?, ?>) value, substitutor));
			}else if(value instanceof Collection) {
				result.add((T) MapUtility.substituteClone((Collection<?>) value, substitutor));
			}else if(value instanceof String) {
				result.add((T) substitutor.replace((String) value));
			}else{
				result.add(value);
			}
		}
		
		return result;
	}
	
	/* 
	 * TODO: Should we substitute the keys as well?
	 * Allowing keys to be substituted as well could enable some interesting use-cases, definitely worth considering
	 * 
	 * TODO: Add support to substitute entire maps and collections, currently we can only substitute strings
	 */
	@SuppressWarnings("unchecked")
	public static <K, V> Map<K, V> substituteClone(Map<K, V> map, StringSubstitutor substitutor) {
		Map<K, V> result = new HashMap<>(map.size());
		for(Entry<K, V> entry : map.entrySet()) {
			K key = entry.getKey();
			V value = entry.getValue();
			if(value instanceof Map) {
				result.put(key, (V) MapUtility.substituteClone((Map<?, ?>) value, substitutor));
			}else if(value instanceof Collection) {
				result.put(key, (V) MapUtility.substituteClone((Collection<?>) value, substitutor));
			}else if(value instanceof String) {
				result.put(key, (V) substitutor.replace((String) value));
			}else{
				result.put(key, value);
			}
		}
		
		return result;
	}
	
	@SuppressWarnings("unchecked")
	public static <T> Collection<T> deepClone(Collection<T> collection) {
		List<T> result = new ArrayList<>();
		for(T value : collection) {
			if(value instanceof Map) {
				result.add((T) MapUtility.deepClone((Map<?, ?>) value));
			}else if(value instanceof Collection) {
				result.add((T) MapUtility.deepClone((Collection<?>) value));
			}else{
				result.add(value);
			}
		}
		
		return result;
	}
	
	@SuppressWarnings("unchecked")
	public static <K, V> Map<K, V> deepClone(Map<K, V> map) {
		Map<K, V> result = new HashMap<>();
		for(Entry<K, V> entry : map.entrySet()) {
			K key = entry.getKey();
			V value = entry.getValue();
			if(value instanceof Map) {
				result.put(key, (V) MapUtility.deepClone((Map<?, ?>) value));
			}else if(value instanceof Collection) {
				result.put(key, (V) MapUtility.deepClone((Collection<?>) value));
			}else{
				result.put(key, value);
			}
		}
		
		return result;
	}
	
	@SuppressWarnings("unchecked")
	public static <T> Collection<T> deepCloneUnmodifiable(Collection<T> collection) {
		List<T> result = new ArrayList<>();
		for(T value : collection) {
			if(value instanceof Map) {
				result.add((T) MapUtility.deepCloneUnmodifiable((Map<?, ?>) value));
			}else if(value instanceof Collection) {
				result.add((T) MapUtility.deepCloneUnmodifiable((Collection<?>) value));
			}else{
				result.add(value);
			}
		}
		
		return Collections.unmodifiableList(result);
	}
	
	@SuppressWarnings("unchecked")
	public static <K, V> Map<K, V> deepCloneUnmodifiable(Map<K, V> map) {
		Map<K, V> result = new HashMap<>();
		for(Entry<K, V> entry : map.entrySet()) {
			K key = entry.getKey();
			V value = entry.getValue();
			if(value instanceof Map) {
				result.put(key, (V) MapUtility.deepCloneUnmodifiable((Map<?, ?>) value));
			}else if(value instanceof Collection) {
				result.put(key, (V) MapUtility.deepCloneUnmodifiable((Collection<?>) value));
			}else{
				result.put(key, value);
			}
		}
		
		return Collections.unmodifiableMap(result);
	}
	
	public static Map<String, Object> expandClone(Map<String, ?> map) {
		return MapUtility.expandClone(map, "\\.");
	}
	
	@SuppressWarnings("unchecked")
	public static Map<String, Object> expandClone(Map<String, ?> map, String splitBy) {
		Map<String, Object> result = new HashMap<>();
		for(Entry<String, ?> entry : map.entrySet()) {
			String key = entry.getKey();
			
			Object value = entry.getValue();
			if(value instanceof Map) {
				value = MapUtility.expandClone((Map<String, ?>) value, splitBy);
			}
			
			String[] parts = key.split(splitBy);
			if(parts.length == 1) {
				result.put(key, value);
				
				continue;
			}
			
			Map<String, Object> currentMap = result;
			for(int i = 0; i < parts.length; i++) {
				String part = parts[i];
				if(i != parts.length - 1) {
					Object currentValue = currentMap.get(part);
					if(!(currentValue instanceof Map)) {
						currentMap.put(part, currentValue = new HashMap<>());
					}
					
					currentMap = (Map<String, Object>) currentValue;
				}else{
					Object currentValue = currentMap.get(part);
					if(!(currentValue instanceof Map)) {
						currentMap.put(part, value);
					}
				}
			}
		}
		
		return result;
	}
	
	@SuppressWarnings("unchecked")
	public static <K, V> Map<K, V> deepMerge(Map<K, V> first, Map<?, ?> second, boolean preferFirst) {
		for(Entry<?, ?> entry : second.entrySet()) {
			Object key = entry.getKey();
			V secondValue = (V) entry.getValue();
			
			if(!first.containsKey(key)) {
				first.put((K) key, secondValue);
				
				continue;
			}
			
			V firstValue = first.get(key);
			if(firstValue instanceof Map && secondValue instanceof Map) {
				MapUtility.deepMerge((Map<?, ?>) firstValue, (Map<?, ?>) secondValue, preferFirst);
				
				continue;
			}
			
			/* Prefer the one with the map */
			if(firstValue instanceof Map) {
				preferFirst = true;
			}else if(secondValue instanceof Map) {
				preferFirst = false;
			}
			
			if(preferFirst) {
				first.put((K) key, firstValue);
			}else{
				first.put((K) key, secondValue);
			}
		}
		
		return first;
	}
}