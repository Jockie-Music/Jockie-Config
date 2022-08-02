package com.jockie.config.impl.field;

public enum Naming {
	/** camelCase */
	CAMEL_CASE,
	/** PascalCase */
	PASCAL_CASE,
	/** snake_case */
	SNAKE_CASE,
	/** lowercase */
	LOWER_CASE;
	
	/**
	 * <b>NOTE:</b> This method assumes the input is in camelCase
	 * 
	 * @param value the content in camelCase which will be converted to the {@link Naming} format
	 * 
	 * @return the content in the new {@link Naming} format
	 */
	public String convert(String value) {
		switch(this) {
			case CAMEL_CASE: return value;
			case LOWER_CASE: return value.toLowerCase();
			case PASCAL_CASE: return Character.toUpperCase(value.charAt(0)) + value.substring(1);
			case SNAKE_CASE: {
				StringBuilder result = new StringBuilder();
				for(int i = 0; i < value.length(); i++) {
					char character = value.charAt(i);
					if(Character.isUpperCase(character)) {
						result.append('_');
						result.append(Character.toLowerCase(character));
					}else{
						result.append(character);
					}
				}
				
				return result.toString();
			}
		}
		
		throw new UnsupportedOperationException(this.name());
	}
}