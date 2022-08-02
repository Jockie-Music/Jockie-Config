package com.jockie.config.impl.field;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.jockie.config.ConfigFactory;
import com.jockie.config.IConfig;
import com.jockie.config.utility.DataTypeUtility;

/*
 * TODO: Handle super classes all the way up to this
 * 
 * TODO: More detailed error messages which contain at what key/field and which class something went wrong
 * 
 * TODO: Can we allow "with" to be used without extending AbstractFieldConfig for inner classes by calling the super class's?
 * 		* It becomes problematic when creating a non-static inner class of one which does not extend AbstractFieldConfig because
 * 		  there's no good way to pass the instance forward, the class will not be initialized until all of the methods have been called already.
 * 		  
 * 		  It would be possible if we had something like "context(this)" at the start of the class but how is that any better than just extending
 * 		  AbstractFieldConfig, it's not really any better.
 * 
 * 		  Another solution might be to use byte-buddy or some other ASM based solution to add some additional stuff at runtime but I am not a big
 * 		  fan of requiring runtime changes to classes for that. If we end up doing some ASM based solution we can even entirely replace the "with"
 * 		  call and just use final fields with the default value and then retransform the entire class to properly populate the values.
 */
/**
 * NOTE: All List, Set and Map fields will be immutable
 * <br>
 * NOTE: Fields are technically not guaranteed to be in the order they are defined and may differ for different JVMs which would break this implementation
 */
public abstract class AbstractFieldConfig {
	
	private static final ThreadLocal<IConfig> CURRENT_CONFIG = new ThreadLocal<>();
	
	private final boolean requireFinal;
	private final Naming naming;
	
	private final IConfig config;
	private BlockingQueue<Field> queue = new LinkedBlockingQueue<>();
	
	@SuppressWarnings("unchecked")
	private <T> T cast(Object value) {
		return (T) value;
	}
	
	private String getName(Field field) {
		Name nameAnnotation = field.getAnnotation(Name.class);
		if(nameAnnotation != null) {
			return nameAnnotation.value();
		}
		
		return this.naming.convert(field.getName());
	}
	
	private Field nextField() {
		Field field = this.queue.poll();
		if(field == null) {
			throw new IllegalStateException("There were more calls to with/require than fields available");
		}
		
		return field;
	}
	
	private <T> T defaultValue(Class<?> type) {
		if(AbstractFieldConfig.class.isAssignableFrom(type)) {
			return this.cast(AbstractFieldConfig.createInternal(this, ConfigFactory.empty(), type));
		}
		
		if(List.class.isAssignableFrom(type)) {
			return this.cast(Collections.emptyList());
		}
		
		if(Set.class.isAssignableFrom(type)) {
			return this.cast(Collections.emptySet());
		}
		
		if(Map.class.isAssignableFrom(type)) {
			return this.cast(Collections.emptyMap());
		}
		
		if(DataTypeUtility.isPrimitiveNumber(type)) {
			return this.cast(DataTypeUtility.convertNumber(0, type));
		}
		
		if(type == boolean.class) {
			return this.cast(false);
		}
		
		return null;
	}
	
	private <T> T getValue(Field field, String name) {
		return this.cast(AbstractFieldConfig.getValue(this, field.getGenericType(), field.getType(), this.config, name));
	}
	
	private <T> T getSpecialValue(Field field) {
		if(field.getAnnotation(Identity.class) != null) {
			if(!field.getType().isAssignableFrom(this.config.getClass())) {
				throw new IllegalStateException("Field: " + field.getName() + ", is defined with @Identity but is not assignable from the config of type: " + this.config.getClass());
			}
			
			return this.cast(this.config);
		}
		
		return null;
	}
	
	protected final <T> T require() {
		Field field = this.nextField();
		
		T specialValue = this.getSpecialValue(field);
		if(specialValue != null) {
			return specialValue;
		}
		
		String name = this.getName(field);
		if(!this.config.has(name)) {
			throw new IllegalStateException("Missing required field: " + name);
		}
		
		return this.getValue(field, name);
	}
	
	protected final <T> T with() {
		Field field = this.nextField();
		
		T specialValue = this.getSpecialValue(field);
		if(specialValue != null) {
			return specialValue;
		}
		
		String name = this.getName(field);
		if(!this.config.has(name)) {
			return this.defaultValue(field.getType());
		}
		
		return this.getValue(field, name);
	}
	
	/* TODO: Add suport for maps etc */
	protected final <T> T with(IConfig config) {
		Field field = this.nextField();
		
		T specialValue = this.getSpecialValue(field);
		if(specialValue != null) {
			return specialValue;
		}
		
		String name = this.getName(field);
		if(!this.config.has(name)) {
			return this.cast(AbstractFieldConfig.createInternal(this, config, field.getType()));
		}
		
		return this.getValue(field, name);
	}
	
	protected final <T> T with(T defaultValue) {
		Field field = this.nextField();
		
		T specialValue = this.getSpecialValue(field);
		if(specialValue != null) {
			return specialValue;
		}
		
		String name = this.getName(field);
		if(!this.config.has(name)) {
			Class<?> type = field.getType();
			if(DataTypeUtility.isNumber(type)) {
				/* Ensure the defaultValue is the correct type, this is to ensure type safety between different number types */
				return this.cast(DataTypeUtility.convertNumber((Number) defaultValue, field.getType()));
			}
			
			return defaultValue;
		}
		
		return this.getValue(field, name);
	}
	
	protected final <T extends Number> T with(long defaultValue) {
		return this.cast(this.with((Object) defaultValue));
	}
	
	protected final <T extends Number> T with(int defaultValue) {
		return this.cast(this.with((Object) defaultValue));
	}
	
	protected final <T extends Number> T with(double defaultValue) {
		return this.cast(this.with((Object) defaultValue));
	}
	
	protected final <T extends Number> T with(float defaultValue) {
		return this.cast(this.with((Object) defaultValue));
	}
	
	private Config getConfigAnnotation() {
		Class<?> parent = this.getClass();
		do {
			Config config = parent.getAnnotation(Config.class);
			if(config != null) {
				return config;
			}
		}while((parent = parent.getEnclosingClass()) != null);
		
		return null;
	}
	
	public AbstractFieldConfig() {
		this.config = CURRENT_CONFIG.get();
		if(this.config == null) {
			throw new IllegalStateException("Missing config, this must be created through the ConfigFactory#create");
		}
		
		Config annotation = this.getConfigAnnotation();
		this.naming = annotation != null ? annotation.naming() : Naming.CAMEL_CASE;
		this.requireFinal = annotation != null ? annotation.requireFinal() : true;
		
		this.addFieldsToQueue();
	}
	
	private void addFieldsToQueue() {
		Class<?> type = this.getClass();
		for(Field field : type.getDeclaredFields()) {
			if(Modifier.isStatic(field.getModifiers())) {
				continue;
			}
			
			/* Exists for non-static nested classes which references the parent class, for instance, this$0 */
			if(field.isSynthetic()) {
				continue;
			}
			
			if(field.getAnnotation(Ignore.class) != null) {
				continue;
			}
			
			if(this.requireFinal && !Modifier.isFinal(field.getModifiers())) {
				throw new IllegalStateException("Field: " + field.getName() + ", is not final, all fields must be final to ensure the config is immutable, set @Config#requireFinal to false if this is not desired");
			}
			
			this.queue.add(field);
		}
	}
	
	private static List<?> getList(Object instance, Type elementType, IConfig config, String name) {
		if(AbstractFieldConfig.isFieldConfig(instance, (Class<?>) elementType)) {
			List<Object> result = new ArrayList<>();
			
			List<IConfig> configs = config.getList(name, IConfig.class);
			for(IConfig fieldConfig : configs) {
				result.add(AbstractFieldConfig.createInternal(instance, fieldConfig, (Class<?>) elementType));
			}
			
			return result;
		}
		
		return config.getList(name, (Class<?>) elementType);
	}
	
	private static Set<?> getSet(Object instance, Type parameterType, IConfig config, String name) {
		return new HashSet<>(AbstractFieldConfig.getList(instance, parameterType, config, name));
	}
	
	private static Map<?, ?> getMap(Object instance, Type keyType, Type valueType, IConfig config, String name) {
		if(AbstractFieldConfig.isFieldConfig(instance, (Class<?>) valueType)) {
			Map<Object, Object> result = new HashMap<>();
			
			Map<?, IConfig> map = config.getMap(name, (Class<?>) keyType, IConfig.class);
			for(Entry<?, IConfig> entry : map.entrySet()) {
				result.put(entry.getKey(), AbstractFieldConfig.createInternal(instance, entry.getValue(), (Class<?>) valueType));
			}
			
			return result;
		}
		
		return config.getMap(name, (Class<?>) keyType, (Class<?>) valueType);
	}
	
	private static Object getValue(Object instance, Type parameterType, Class<?> parameterClass, IConfig config, String name) {
		Type[] types;
		if(parameterType instanceof ParameterizedType) {
			types = ((ParameterizedType) parameterType).getActualTypeArguments();
		}else{
			types = new Type[parameterClass.getTypeParameters().length];
			for(int i = 0; i < types.length; i++) {
				types[i] = Object.class;
			}
		}
		
		if(parameterClass == List.class) {
			return Collections.unmodifiableList(AbstractFieldConfig.getList(instance, types[0], config, name));
		}
		
		if(parameterClass == Set.class) {
			return Collections.unmodifiableSet(AbstractFieldConfig.getSet(instance, types[0], config, name));
		}
		
		if(parameterClass == Map.class) {
			return Collections.unmodifiableMap(AbstractFieldConfig.getMap(instance, types[0], types[1], config, name));
		}
		
		if(AbstractFieldConfig.isFieldConfig(instance, parameterClass)) {
			return AbstractFieldConfig.createInternal(instance, config.get(name, IConfig.class), parameterClass);
		}
		
		return config.get(name, parameterClass);
	}
	
	public static AbstractFieldConfig createInternal(Object instance, IConfig config, Class<?> type) {
		AbstractFieldConfig fieldConfig = AbstractFieldConfig.createInstance(instance, config, type);
		if(!fieldConfig.queue.isEmpty()) {
			throw new IllegalStateException("Not all fields were processed, make sure every field calls with/require or is marked with @Ignore");
		}
		
		fieldConfig.queue = null;
		return fieldConfig;
	}
	
	private static AbstractFieldConfig createInstance(Object instance, IConfig config, Class<?> type) {
		Constructor<?> constructor = AbstractFieldConfig.findConstructor(instance, type);
		constructor.setAccessible(true);
		
		try {
			if(constructor.getParameterCount() == 0) {
				CURRENT_CONFIG.set(config);
				try {
					return (AbstractFieldConfig) constructor.newInstance();
				}finally{
					CURRENT_CONFIG.remove();
				}
			}
			
			Class<?> firstParameter = constructor.getParameters()[0].getType();
			if(constructor.getParameterCount() == 1) {
				if(IConfig.class.isAssignableFrom(firstParameter)) {
					return (AbstractFieldConfig) constructor.newInstance(config);
				}
			}
			
			if(instance == null) {
				throw new IllegalStateException("Missing parent class: " + firstParameter);
			}
			
			if(constructor.getParameterCount() == 1) {
				CURRENT_CONFIG.set(config);
				try {
					return (AbstractFieldConfig) constructor.newInstance(instance);
				}finally{
					CURRENT_CONFIG.remove();
				}
			}else{
				return (AbstractFieldConfig) constructor.newInstance(instance, config);
			}
		}catch(InstantiationException | IllegalAccessException | IllegalArgumentException e) {
			throw new RuntimeException("Something unexpected happenend", e);
		}catch(InvocationTargetException e) {
			/* TODO: This should only be caused by a misconfigured config class, make a better error message */
			throw new RuntimeException("Failed to create config: " + type, e);
		}
	}
	
	private static Constructor<?> findConstructor(Object instance, Class<?> type) {
		for(Constructor<?> constructor : type.getDeclaredConstructors()) {
			Parameter[] parameters = constructor.getParameters();
			if(parameters.length == 0) {
				return constructor;
			}
			
			if(parameters.length == 1 && instance.getClass() == parameters[0].getType()) {
				return constructor;
			}
		}
		
		return null;
	}
	
	private static boolean isFieldConfig(Object instance, Class<?> clazz) {
		if(!AbstractFieldConfig.class.isAssignableFrom(clazz)) {
			return false;
		}
		
		return AbstractFieldConfig.findConstructor(instance, clazz) != null;
	}
}