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

public class InterfaceConfigImpl {
	
	/**
	 * Appended to the proxy interfaces, only used to easily
	 * determine if a proxy was created by us.
	 */
	private static interface InternalConfigImpl {}
	
	public static <T> T createInternal(IConfig config, Class<T> interfaze) {
		return InterfaceConfigImpl.createInternal(config, null, interfaze);
	}
	
	@SuppressWarnings("unchecked")
	public static <T> T createInternal(IConfig config, InterfaceConfigImpl parent, Class<T> interfaze) {
		if(!interfaze.isInterface()) {
			throw new IllegalArgumentException(interfaze + " is not an interface");
		}
		
		InterfaceConfigImpl impl = new InterfaceConfigImpl(parent, interfaze, config);
		return (T) impl.proxy;
	}
	
	private static Config getConfigAnnotation(Class<?> clazz) {
		Class<?> parent = clazz;
		do {
			Config config = parent.getAnnotation(Config.class);
			if(config != null) {
				return config;
			}
		}while((parent = parent.getEnclosingClass()) != null);
		
		return null;
	}
	
	private static class DelegateInvocationHandler implements InvocationHandler {
		
		private InvocationHandler handler;
		
		public DelegateInvocationHandler(InvocationHandler handler) {
			this.handler = handler;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			return this.handler.invoke(proxy, method, args);
		}
	}
	
	private static class Handler extends DefaultValueProxy {
		
		private final InterfaceConfigImpl impl;
		
		public Handler(InterfaceConfigImpl impl) {
			super(impl.proxiedInterface);
			
			this.impl = impl;
		}
		
		@Override
		public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
			/* 
			 * Allows the interface to extend IConfig which can be useful,
			 * if they for some reason want to use IConfig#get or similar.
			 */
			if(method.getDeclaringClass().equals(IConfig.class)) {
				return method.invoke(this.impl.config, arguments);
			}
			
			String methodName = method.getName();
			
			/* TODO: Is there anything else we need to implement for it? */
			if(method.getDeclaringClass().equals(Object.class)) {
				if(methodName.equals("toString")) {
					return this.impl.proxiedInterface.getSimpleName() + this.impl.valueByName.toString();
				}
				
				if(methodName.equals("hashCode")) {
					return this.impl.valueByName.hashCode();
				}
				
				if(methodName.equals("equals")) {
					return arguments[0] == this;
				}
			}
			
			Map<String, Object> values = this.impl.valueByMethod;
			if(method.getParameterCount() == 0 && values.containsKey(methodName)) {
				return values.get(methodName);
			}
			
			return super.invoke(proxy, method, arguments);
		}
	}
	
	/* 
	 * Technically we only need the top level enclosing class
	 * (as in where it was created from and not actual enclosing class)
	 * but it might be good to have a reference to the parent
	 * if we ever decide to do anything with this in the future.
	 */
	private final InterfaceConfigImpl parent;
	
	private final Class<?> proxiedInterface;
	
	private final IConfig config;
	
	/* 
	 * TODO: Not sure if it makes more sense to use the Method reference as the key,
	 * not really sure what the side effects of that is nor if that would result in
	 * worse performance, so I am just going to go with a simple String key and a
	 * "Method#getParameterCount == 0" check to determine the method.
	 */
	private final Map<String, Object> valueByMethod;
	
	/**
	 * This only contains actual config properties, excluding all
	 * {@link Computed @Computed}, {@link Parent @Parent} and {@link Identity @Identity}
	 */
	private final Map<String, Object> valueByName;
	
	private final Handler handler;
	private final DelegateInvocationHandler invocationHandler;
	private final Object proxy;
	
	/* Inherit the annotation from the enclosing class */
	private Config getConfigAnnotation() {
		return InterfaceConfigImpl.getConfigAnnotation(this.proxiedInterface);
	}
	
	public InterfaceConfigImpl(Class<?> interfaze, IConfig config) {
		this(null, interfaze, config);
	}
	
	public InterfaceConfigImpl(InterfaceConfigImpl parent, Class<?> proxiedInterface, IConfig config) {
		this.parent = parent;
		
		this.proxiedInterface = proxiedInterface;
		
		this.handler = new Handler(this);
		this.invocationHandler = new DelegateInvocationHandler(this.handler);
		this.proxy = this.proxy(this.invocationHandler);
		
		this.config = Objects.requireNonNull(config);
		
		this.valueByMethod = new HashMap<>();
		this.valueByName = new HashMap<>();
		
		this.computeValues();
	}
	
	private Object proxy(InvocationHandler handler) {
		return Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), new Class[] { this.proxiedInterface, InternalConfigImpl.class }, handler);
	}
	
	private List<?> getList(Type elementType, IConfig config, String name) {
		if(this.isConfig((Class<?>) elementType)) {
			List<Object> result = new ArrayList<>();
			
			List<IConfig> configs = config.getList(name, IConfig.class);
			for(IConfig fieldConfig : configs) {
				result.add(InterfaceConfigImpl.createInternal(fieldConfig, this, (Class<?>) elementType));
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
				result.put(entry.getKey(), InterfaceConfigImpl.createInternal(entry.getValue(), this, (Class<?>) valueType));
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
			return InterfaceConfigImpl.createInternal(config.get(name, IConfig.class), this, parameterClass);
		}
		
		return config.get(name, parameterClass);
	}
	
	private Set<Class<?>> getEnclosingClasses() {
		Set<Class<?>> enclosing = new HashSet<>();
		
		InterfaceConfigImpl config = this;
		do {
			enclosing.add(config.proxiedInterface);
		}while((config = config.parent) != null);
		
		return enclosing;
	}
	
	/* TODO: Should this check for extending IConfig as well? */
	/*
	 * TODO: There are downsides to this approach of identifying config interfaces,
	 * 
	 * if you have a structure like
	 * interface Bot {
	 * 		interface User {}
	 * 		
	 * 		interface PremiumBot {
	 * 			User user();
	 * 		}
	 * }
	 * and then try to create a config of only the PremiumBot you will find that User
	 * is not considered to be a config, the easy fix to this for the end user is just
	 * to annotate Bot with @Config but is there any better solution to this?
	 * Should we just aggresively consider everything a config?
	 */
	private boolean isConfig(Class<?> type) {
		if(!type.isInterface()) {
			return false;
		}
		
		Set<Class<?>> enclosing = this.getEnclosingClasses();
		
		Class<?> parent = type;
		do {
			if(parent.getAnnotation(Config.class) != null) {
				return true;
			}
			
			if(enclosing.contains(parent)) {
				return true;
			}
		}while((parent = parent.getEnclosingClass()) != null);
		
		return false;
	}
	
	/* 
	 * TODO: Convert anonymous classes into the interface
	 */
	private Object convertDefaultValue(Naming naming, Class<?> type, Object value) {
		if(value == null) {
			return value;
		}
		
		if(!this.isConfig(type)) {
			return value;
		}
		
		/* This means it's already a proxied config */
		if(InternalConfigImpl.class.isInstance(value)) {
			return value;
		}
		
		/*
		 * This means you can more easily deal with default values
		 * in interfaces when you re-use the same one multiple times.
		 * 
		 * Example:
		 * 
		 * interface Ratelimits {
		 * 	default Ratelimit route() {
		 * 		return new Ratelimit.Impl(5, TimeUnit.SECONDS.toMillis(5));
		 * 	}
		 * 
		 * 	default Ratelimit anotherRoute() {
		 * 		return new Ratelimit.Impl(10, TimeUnit.SECONDS.toMillis(5));
		 * 	}
		 * }
		 * 
		 * interface Ratelimit {
		 * 
		 * 	class Impl implements Ratelimit {
		 * 		private final long count;
		 * 		private final long time;
		 * 
		 * 		public Impl(long count, long time) {
		 * 			this.count = count;
		 * 			this.time = time;
		 * 		}
		 * 
		 * 		@Override
		 * 		public long count() {
		 * 			return this.count;
		 * 		}
		 * 
		 * 		@Override
		 * 		public long time() {
		 * 			return this.time;
		 * 		}
		 * 	}
		 * 
		 * 	long count();
		 * 	long time();
		 * }
		 */
		Map<String, Object> values = new HashMap<>();
		for(Method method : type.getMethods()) {
			if(!this.isPropertyMethod(method)) {
				continue;
			}
			
			try {
				values.put(this.getName(naming, method), method.invoke(value));
			}catch(IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				throw new RuntimeException("Failed to compute value for method: " + method + ", in class: " + value.getClass(), e);
			}
		}
		
		return InterfaceConfigImpl.createInternal(ConfigFactory.fromMap(values), this, type);
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
			return InterfaceConfigImpl.createInternal(ConfigFactory.empty(), this, type);
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
	
	private Object getParentOfType(Class<?> type) {
		InterfaceConfigImpl parent = this;
		do {
			Object proxy = parent.proxy;
			if(type.isInstance(proxy)) {
				return proxy;
			}
		}while((parent = parent.parent) != null);
		
		return null;
	}
	
	private Object computeValue(Object instance, Method method, String name) {		
		Class<?> returnType = method.getReturnType();
		
		if(method.getAnnotation(Parent.class) != null) {
			/* TODO: Should this fail if it can not find one or should it just return null? */
			return this.getParentOfType(returnType);
		}
		
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
	
	private boolean isIdentityMethod(Method method) {
		if(method.getAnnotation(Identity.class) != null) {
			if(method.getParameterCount() > 0) {
				throw new IllegalStateException("Method: " + method + ", is defined with @Identity but is not a getter");
			}
			
			if(!method.getReturnType().isAssignableFrom(this.config.getClass())) {
				throw new IllegalStateException("Method: " + method + ", is defined with @Identity but is not assignable from the config of type: " + this.config.getClass());
			}
			
			return true;
		}
		
		return false;
	}
	
	private boolean isParentMethod(Method method) {
		if(method.getAnnotation(Parent.class) != null) {
			if(!this.isGetter(method)) {
				throw new IllegalStateException("Method: " + method + ", is defined with @Parent but is not a getter");
			}
			
			return true;
		}
		
		return false;
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
	
	private boolean isPropertyMethod(Method method) {
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
	
	private final void computeValues() {
		Config annotation = this.getConfigAnnotation();
		
		Naming naming;
		if(annotation != null) {
			naming = annotation.naming();
		}else{
			naming = Naming.CAMEL_CASE;
		}
		
		Set<Method> propertyMethods = new HashSet<>();
		Set<Method> computeMethods = new HashSet<>();
		
		for(Method method : this.proxiedInterface.getMethods()) {
			if(Modifier.isStatic(method.getModifiers())) {
				continue;
			}
			
			/* 
			 * TODO: Should this be included in the toString implementation (valueByName)?
			 * It seems like it's unnecessary to include the backing IConfig as that can
			 * contain a lot of additional values which are not relevant. Depending on how
			 * the end user uses it some of the values may be relevant but it might make
			 * more sense to include @Computed values in that case.
			 * 
			 * We want to keep the toString implementation to only the most relevant values,
			 * which I think is just the actual config properties and not any additional stuff.
			 */
			if(this.isIdentityMethod(method)) {
				this.valueByMethod.put(method.getName(), this.config);
				continue;
			}
			
			if(this.isParentMethod(method)) {
				this.valueByMethod.put(method.getName(), this.getParentOfType(method.getReturnType()));
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
			
			if(this.isPropertyMethod(method)) {
				propertyMethods.add(method);
				continue;
			}
		}
		
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
		this.invocationHandler.handler = (proxy, method, arguments) -> {
			if(propertyMethods.remove(method)) {
				String name = this.getName(naming, method);
				Object value = this.convertDefaultValue(naming, method.getReturnType(), this.computeValue(proxy, method, name));
				
				this.valueByMethod.put(method.getName(), value);
				this.valueByName.put(name, value);
				
				return value;
			}
			
			if(computeMethods.remove(method)) {
				Object value = method.invoke(proxy);
				this.valueByMethod.put(method.getName(), this.convertDefaultValue(naming, method.getReturnType(), value));
				return value;
			}
			
			return this.handler.invoke(proxy, method, arguments);
		};
		
		for(Method method : new ArrayList<>(propertyMethods)) {
			if(!propertyMethods.remove(method)) {
				continue;
			}
			
			String name = this.getName(naming, method);
			Object value = this.convertDefaultValue(naming, method.getReturnType(), this.computeValue(this.proxy, method, name));
			
			this.valueByMethod.put(method.getName(), value);
			this.valueByName.put(name, value);
		}
		
		for(Method method : new ArrayList<>(computeMethods)) {
			if(!computeMethods.remove(method)) {
				continue;
			}
			
			Object value;
			try {
				value = this.convertDefaultValue(naming, method.getReturnType(), method.invoke(this.proxy));
			}catch(IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				throw new RuntimeException("Failed to compute the value", e);
			}
			
			this.valueByMethod.put(method.getName(), value);
		}
		
		this.invocationHandler.handler = this.handler;
	}
}