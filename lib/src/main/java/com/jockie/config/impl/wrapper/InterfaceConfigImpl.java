package com.jockie.config.impl.wrapper;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

import com.jockie.config.ConfigFactory;
import com.jockie.config.IConfig;
import com.jockie.config.utility.DataTypeUtility;

public class InterfaceConfigImpl extends DefaultValueProxy {
	
	@SuppressWarnings("unchecked")
	public static <T> T createInternal(IConfig config, Class<T> interfaze) {
		if(!interfaze.isInterface()) {
			throw new IllegalArgumentException(interfaze + " is not an interface");
		}
		
		return (T) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), new Class[] { interfaze }, new InterfaceConfigImpl(interfaze, config));
	}
	
	private final IConfig config;
	private final Map<String, Object> values;
	
	/* Inherit the annotation from the enclosing class */
	private Config getConfigAnnotation() {
		Class<?> parent = this.interfaze;
		do {
			Config config = parent.getAnnotation(Config.class);
			if(config != null) {
				return config;
			}
		}while((parent = parent.getEnclosingClass()) != null);
		
		return null;
	}
	
	public InterfaceConfigImpl(Class<?> interfaze, IConfig config) {
		super(interfaze);
		
		this.config = Objects.requireNonNull(config);
		this.values = this.build();
	}
	
	private Object proxy(InvocationHandler handler) {
		return Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), new Class[] { this.interfaze }, handler);
	}
	
	private List<?> getList(Type elementType, IConfig config, String name) {
		if(this.isConfig((Class<?>) elementType)) {
			List<Object> result = new ArrayList<>();
			
			List<IConfig> configs = config.getList(name, IConfig.class);
			for(IConfig fieldConfig : configs) {
				result.add(InterfaceConfigImpl.createInternal(fieldConfig, (Class<?>) elementType));
			}
			
			return result;
		}
		
		return config.getList(name, (Class<?>) elementType);
	}
	
	private Set<?> getSet(Type parameterType, IConfig config, String name) {
		return new HashSet<>(this.getList(parameterType, config, name));
	}
	
	private Map<?, ?> getMap(Type keyType, Type valueType, IConfig config, String name) {
		if(this.isConfig((Class<?>) valueType)) {
			Map<Object, Object> result = new HashMap<>();
			
			Map<?, IConfig> map = config.getMap(name, (Class<?>) keyType, IConfig.class);
			for(Entry<?, IConfig> entry : map.entrySet()) {
				result.put(entry.getKey(), InterfaceConfigImpl.createInternal(entry.getValue(), (Class<?>) valueType));
			}
			
			return result;
		}
		
		return config.getMap(name, (Class<?>) keyType, (Class<?>) valueType);
	}
	
	private Object getValue(Type parameterType, Class<?> parameterClass, IConfig config, String name) {
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
			return Collections.unmodifiableList(this.getList(types[0], config, name));
		}
		
		if(parameterClass == Set.class) {
			return Collections.unmodifiableSet(this.getSet(types[0], config, name));
		}
		
		if(parameterClass == Map.class) {
			return Collections.unmodifiableMap(this.getMap(types[0], types[1], config, name));
		}
		
		if(this.isConfig(parameterClass)) {
			return InterfaceConfigImpl.createInternal(config.get(name, IConfig.class), parameterClass);
		}
		
		return config.get(name, parameterClass);
	}
	
	/* TODO: Should this check for extending IConfig as well? */
	private boolean isConfig(Class<?> type) {
		if(!type.isInterface()) {
			return false;
		}
		
		if(type.getAnnotation(Config.class) != null) {
			return true;
		}
		
		if(this.interfaze.equals(type.getEnclosingClass())) {
			return true;
		}
		
		return false;
	}
	
	private Object defaultValue(Class<?> type) {
		if(List.class.isAssignableFrom(type)) {
			return Collections.emptyList();
		}
		
		if(Set.class.isAssignableFrom(type)) {
			return Collections.emptySet();
		}
		
		if(Map.class.isAssignableFrom(type)) {
			return Collections.emptyMap();
		}
		
		if(this.isConfig(type)) {
			return InterfaceConfigImpl.createInternal(ConfigFactory.empty(), type);
		}
		
		if(DataTypeUtility.isPrimitiveNumber(type)) {
			return DataTypeUtility.convertNumber(0, type);
		}
		
		if(type == boolean.class) {
			return false;
		}
		
		return null;
	}
	
	private String getBeanName(String name) {
		if(name.length() > 3 && name.startsWith("get")) {
			return Character.toLowerCase(name.charAt(3)) + name.substring(4);
		}
		
		if(name.length() > 2 && name.startsWith("is")) {
			return Character.toLowerCase(name.charAt(2)) + name.substring(3);
		}
		
		return name;
	}
	
	private String getName(Naming naming, Method method) {
		Name name = method.getAnnotation(Name.class);
		if(name != null) {
			return name.value();
		}
		
		return naming.convert(this.getBeanName(method.getName()));
	}
	
	private Object computeValue(Naming naming, Object instance, Method method) {
		if(method.getAnnotation(Identity.class) != null) {
			return this.config;
		}
		
		Class<?> returnType = method.getReturnType();
		
		String name = this.getName(naming, method);
		if(this.config.has(name)) {
			return this.getValue(method.getGenericReturnType(), returnType, this.config, name);
		}
		
		if(!method.isDefault()) {
			return this.defaultValue(returnType);
		}
		
		try {
			return method.invoke(instance);
		}catch(IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new RuntimeException("Failed to get default value", e);
		}
	}
	
	private boolean isGetter(Method method) {
		/* Would anyone ever have this? and why? is there anything we need to support? */
		if(method.getReturnType() == void.class || method.getReturnType() == Void.class) {
			return false;
		}
		
		if(method.getParameterCount() > 0) {
			return false;
		}
		
		return true;
	}
	
	private boolean isComputeMethod(Method method) {
		if(method.getAnnotation(Computed.class) != null) {
			if(!this.isGetter(method)) {
				throw new IllegalStateException("Method: " + method + ", is defined with @Computed but is not a getter");
			}
			
			if(!method.isDefault()) {
				throw new IllegalStateException("Method: " + method + ", is defined with @Computed but does not have a default implementation");
			}
			
			return true;
		}
		
		return false;
	}
	
	private boolean isConfigMethod(Method method) {
		Class<?> returnType = method.getReturnType();
		if(method.getAnnotation(Identity.class) != null) {
			if(method.getParameterCount() > 0) {
				throw new IllegalStateException("Method: " + method + ", is defined with @Identity but is not a getter");
			}
			
			if(!returnType.isAssignableFrom(this.config.getClass())) {
				throw new IllegalStateException("Method: " + method + ", is defined with @Identity but is not assignable from the config of type: " + this.config.getClass());
			}
			
			return true;
		}
		
		if(!this.isGetter(method)) {
			if(!method.isDefault()) {
				throw new IllegalStateException("Method: " + method + ", is not a getter and there is no default implementation");
			}
			
			return false;
		}
		
		if(method.getAnnotation(Ignore.class) != null) {
			if(!method.isDefault()) {
				throw new IllegalStateException("Method: " + method + ", is defined with @Ignore but does not have a default implementation");
			}
			
			return false;
		}
		
		return true;
	}
	
	private final Map<String, Object> build() {
		Config annotation = this.getConfigAnnotation();
		
		Naming naming;
		if(annotation != null) {
			naming = annotation.naming();
		}else{
			naming = Naming.CAMEL_CASE;
		}
		
		Set<Method> methods = new HashSet<>();
		Set<Method> computeMethods = new HashSet<>();
		
		for(Method method : this.interfaze.getMethods()) {
			if(Modifier.isStatic(method.getModifiers())) {
				continue;
			}
			
			/* 
			 * These should run as late as possible, note that they may not be called last,
			 * that would normally happen if one of the config property methods call a computed method
			 */
			if(this.isComputeMethod(method)) {
				computeMethods.add(method);
				continue;
			}
			
			if(this.isConfigMethod(method)) {
				methods.add(method);
				continue;
			}
		}
		
		Map<String, Object> values = new HashMap<>();
		
		/*
		 * This is necessary to ensure that only a single value is created from a method,
		 * due to the fact that you can call other methods from the default implementation
		 * and because the JVM does not give us the Methods in an order where we know one
		 * will be called before the other we have to use this implementation to only create
		 * one value.
		 * 
		 * This can be tested by having a method return a random value then having one or more
		 * other methods calling it, after loading the config you will see that some of the values
		 * are different without this.
		 */
		Object instance = this.proxy((proxy, method, arguments) -> {
			Object value;
			if(methods.remove(method)) {
				value = this.computeValue(naming, proxy, method);
			}else if(computeMethods.remove(method)) {
				value = method.invoke(proxy);
			}else{
				return this.invoke(values, proxy, method, arguments);
			}
			
			values.put(method.getName(), value);
			return value;
		});
		
		for(Method method : new ArrayList<>(methods)) {
			if(!methods.remove(method)) {
				continue;
			}
			
			values.put(method.getName(), this.computeValue(naming, instance, method));
		}
		
		for(Method method : new ArrayList<>(computeMethods)) {
			if(!computeMethods.remove(method)) {
				continue;
			}
			
			try {
				values.put(method.getName(), method.invoke(instance));
			}catch(IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				throw new RuntimeException("Failed to compute the value", e);
			}
		}
		
		return values;
	}
	
	private Object invoke(Map<String, Object> values, Object proxy, Method method, Object[] arguments) throws Throwable {
		/* 
		 * Allows the interface to extend IConfig which can be useful,
		 * if they for some reason want to use IConfig#get or similar.
		 */
		if(method.getDeclaringClass().equals(IConfig.class)) {
			return method.invoke(this.config, arguments);
		}
		
		String methodName = method.getName();
		
		/* TODO: Is there anything else we need to implement for it? */
		if(method.getDeclaringClass().equals(Object.class)) {
			if(methodName.equals("toString")) {
				return values.toString();
			}
			
			if(methodName.equals("hashCode")) {
				return values.hashCode();
			}
			
			if(methodName.equals("equals")) {
				return arguments[0] == this;
			}
		}
		
		if(method.getParameterCount() == 0 && values.containsKey(methodName)) {
			return values.get(methodName);
		}
		
		return super.invoke(proxy, method, arguments);
	}
	
	@Override
	public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
		return this.invoke(this.values, proxy, method, arguments);
	}
}