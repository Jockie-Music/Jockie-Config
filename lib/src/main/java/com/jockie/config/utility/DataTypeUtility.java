package com.jockie.config.utility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class DataTypeUtility {
	
	public static boolean isLong(Class<?> type) {
		return type == Long.class || type == long.class;
	}
	
	public static boolean isInteger(Class<?> type) {
		return type == Integer.class || type == int.class;
	}
	
	public static boolean isShort(Class<?> type) {
		return type == Short.class || type == short.class;
	}
	
	public static boolean isByte(Class<?> type) {
		return type == Byte.class || type == byte.class;
	}
	
	public static boolean isFloat(Class<?> type) {
		return type == Float.class || type == float.class;
	}
	
	public static boolean isDouble(Class<?> type) {
		return type == Double.class || type == double.class;
	}
	
	public static boolean isBoolean(Class<?> type) {
		return type == Boolean.class || type == boolean.class;
	}
	
	public static boolean isCharacter(Class<?> type) {
		return type == Character.class || type == char.class;
	}
	
	public static Class<?> getBoxedClass(Class<?> type) {
		if(!type.isPrimitive()) return type;
		
		if(type == long.class) return Long.class;
		if(type == int.class) return Integer.class;
		if(type == short.class) return Short.class;
		if(type == byte.class) return Byte.class;
		if(type == float.class) return Float.class;
		if(type == double.class) return Double.class;
		if(type == boolean.class) return Boolean.class;
		if(type == char.class) return Character.class;
		if(type == void.class) return Void.class;
		
		return type;
	}
	
	public static boolean isNumber(Class<?> type) {
		return isLong(type) || isInteger(type) || isShort(type)
			|| isByte(type) || isFloat(type) || isDouble(type);
	}
	
	@SuppressWarnings("unchecked")
	public static <T> T getDefaultValue(Class<?> type) {
		if(type.isPrimitive()) {
			if(DataTypeUtility.isNumber(type)) {
				return (T) DataTypeUtility.convertNumber(0, type);
			}
			
			if(DataTypeUtility.isBoolean(type)) {
				return (T) Boolean.FALSE;
			}
		}
		
		return null;
	}
	
	public static Object convertNumber(Number number, Class<?> type) {
		if(isLong(type)) {
			return number.longValue();
		}else if(isInteger(type)) {
			return number.intValue();
		}else if(isShort(type)) {
			return number.shortValue();
		}else if(isByte(type)) {
			return number.byteValue();
		}else if(isFloat(type)) {
			return number.floatValue();
		}else if(isDouble(type)) {
			return number.doubleValue();
		}
		
		throw new IllegalArgumentException("Unable to convert value: " + number + " (of type: " + number.getClass() + "), to: " + type);
	}
	
	/**
	 * Leading and trailing spaces will be removed and empty values will not be included
	 * in the result
	 */
	public static List<String> parseList(String value) {
		int length = value.length();
		
		/* 
		 * TODO: Do we want to allow conversion to a list without the start and end character?
		 * 
		 * I do think it makes sense to allow this, it would make it a lot easier to convert
		 * a single entity to a list for instance or just convert a comma seperated string to
		 * a list, I am not sure if there are any downsides for allowing this.
		 */
		if(length < 2 || value.charAt(0) != '[' || value.charAt(length - 1) != ']') {
			throw new IllegalArgumentException("That is not a valid list, a list must start with [ and end with ]");
		}
		
		List<String> list = new ArrayList<>();
		
		/* TODO: Add support for quoted values and nested lists */
		int start = -1, end = -1;
		for(int i = 1; i < length - 1; i++) {
			char character = value.charAt(i);
			switch(character) {
				case ' ': continue;
				case ',': {
					if(start != -1 && end != -1) {
						list.add(value.substring(start, end + 1));
					}
					
					start = -1;
					end = -1;
					
					continue;
				}
			}
			
			end = i;
			
			if(start == -1) {
				start = i;
			}
		}
		
		if(start != -1 && end != -1) {
			list.add(value.substring(start, end + 1));
		}
		
		return list;
	}
	
	@SuppressWarnings("unchecked")
	public static <T> T cast(Object object) {
		return (T) object;
	}
	
	public static <T extends Enum<T>> T parseEnum(Class<T> enumClass, String string) {
		try {
			/* 
			 * Allows the enum name to be used in a more friendly way, allowing it to be
			 * in lowercase or separated by a dash or space instead of underscore, this
			 * makes the assumption that every enum follows the uppercase naming convetion,
			 * which may not always be the case, so we might want to have some alternative
			 * for that.
			 */
			return Enum.valueOf(enumClass, string.toUpperCase()
				.replace(" ", "_")
				.replace("-", "_"));
		}catch(IllegalArgumentException e) {
			String possibleValues = Arrays.stream(enumClass.getEnumConstants())
				.map(Enum::name)
				.collect(Collectors.joining(", "));
			
			throw new IllegalArgumentException("Unable to convert value: " + string + " (of type: " + string.getClass() + "), to: " + enumClass + ", possible values: " + possibleValues, e);
		}
	}
	
	public static Number parseNumber(String value) {
		if(value.indexOf('.') == -1) {
			return Long.valueOf(value);
		}
		
		return Double.valueOf(value);
	}
	
	public static boolean parseBoolean(String string) {
		string = string.toLowerCase();
		if(string.equals("true") || string.equals("yes")) {
			return true;
		}
		
		if(string.equals("false") || string.equals("no")) {
			return false;
		}
		
		throw new IllegalArgumentException("Unable to convert value: " + string + " (of type: " + string.getClass() + "), to: " + boolean.class + ", possible values: true, false, yes, no");
	}
	
	/* TODO: Replace this with converter classes for the ability to easily extend it */
	public static Object convert(Object value, Class<?> type) {
		if(value == null || type == null) {
			return value;
		}
		
		Class<?> valueType = value.getClass();
		if(type.isAssignableFrom(valueType)) {
			return value;
		}
		
		/* Auto-boxing can handle it for us */
		if(valueType == DataTypeUtility.getBoxedClass(type)) {
			return value;
		}
		
		if(type == String.class) {
			if(value instanceof Number) {
				return value.toString();
			}
			
			if(value instanceof Boolean) {
				return value.toString();
			}
			
			/* TODO: Allow lists and maps to be converted to string? */
			
			throw new IllegalArgumentException("Unable to convert value: " + value + " (of type: " + valueType + "), to: " + type);
		}
		
		if(value instanceof Number) {
			return DataTypeUtility.convertNumber((Number) value, type);
		}
		
		if(value instanceof String) {
			String string = (String) value;
			
			if(DataTypeUtility.isNumber(type)) {
				return DataTypeUtility.convertNumber(DataTypeUtility.parseNumber(string), type);
			}
			
			if(DataTypeUtility.isBoolean(type)) {
				return DataTypeUtility.parseBoolean(string);
			}
			
			if(type.isEnum()) {
				return DataTypeUtility.parseEnum(DataTypeUtility.cast(type), string);
			}
			
			if(type == List.class) {
				return DataTypeUtility.parseList(string);
			}
			
			/* TODO: Add support for maps? */
		}
		
		throw new IllegalArgumentException("Unable to convert value: " + value + " (of type: " + valueType + "), to: " + type);
	}
}