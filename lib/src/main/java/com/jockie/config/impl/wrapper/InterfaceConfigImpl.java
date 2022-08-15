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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.jockie.config.ConfigFactory;
import com.jockie.config.IConfig;
import com.jockie.config.utility.DataTypeUtility;

/* TODO: Better error messages, for instance, include which property had issues */
/* 
 * TODO: Should we automatically convert all default List/Set/Map values to unmodifiable?
 * 
 * It would probably be a good idea to do so to keep to our goal of immutability,
 * we might also want to create new instances of all the default values as we can't
 * really know where they came from.
 * 
 * If we decide to do this we should keep track of the references via something like
 * IdentityHashMap to ensure that the same values are still the same, we don't want to
 * prevent people from comparing references.
 */
public class InterfaceConfigImpl {
	
	/**
	 * Appended to the proxy interfaces, only used to easily
	 * determine if a proxy was created by us.
	 */
	/* 
	 * TODO: This causes issues with other package protected configs,
	 * either we make this public or we figure out an alternative solution.
	 * 
	 * The configs can never be package private because we need to call the default method
	 * and we will always get the "IllegalAccessError: tried to access class ... from class ...Proxy"
	 * exception if we try to, I am not sure if there's any way for us to solve that.
	 */
	private static interface InternalConfigImpl {
		
		/*
		 * The name of this is purposefully unconventional to avoid
		 * potential overlaps with the configs, if anyone calls one
		 * of their config values this that's on them :)
		 */
		public InterfaceConfigImpl __INTERNAL_IMPL__();
		
	}
	
	/**
	 * Used when getting a value from a Map with {@link Map#getOrDefault} to avoid
	 * redundant lookups by using {@link Map#containsKey} and {@link Map#get}.
	 */
	private static final Object EMPTY = new Object();
	
	public static <T> T createInternal(IConfig config, Class<T> interfaze) {
		return InterfaceConfigImpl.createInternal(config, null, null, interfaze, true);
	}
	
	private static <T> T createInternal(IConfig config, InterfaceConfigImpl parent, Class<T> interfaze) {
		return InterfaceConfigImpl.createInternal(config, null, parent, interfaze);
	}
	
	private static <T> T createInternal(IConfig config, Object wrappedObject, InterfaceConfigImpl parent, Class<T> interfaze) {
		/* computeValues will be called in postLoadValue */
		return InterfaceConfigImpl.createInternal(config, wrappedObject, parent, interfaze, false);
	}
	
	@SuppressWarnings("unchecked")
	private static <T> T createInternal(IConfig config, Object wrappedObject, InterfaceConfigImpl parent, Class<T> interfaze, boolean computeValues) {
		InterfaceConfigImpl impl = new InterfaceConfigImpl(parent, interfaze, config, wrappedObject);
		if(computeValues) {
			impl.computeValues();
		}
		
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
			Class<?> declaringClass = method.getDeclaringClass();
			
			/* 
			 * Allows the interface to extend IConfig which can be useful,
			 * if they for some reason want to use IConfig#get or similar.
			 */
			if(declaringClass.equals(IConfig.class)) {
				return method.invoke(this.impl.config, arguments);
			}
			
			String methodName = method.getName();
			if(declaringClass.equals(InternalConfigImpl.class)) {
				return this.impl;
			}
			
			/* TODO: Is there anything else we need to implement for it? */
			if(declaringClass.equals(Object.class)) {
				if(methodName.equals("toString")) {
					return this.impl.proxiedInterface.getSimpleName() + this.impl.valueByName.toString();
				}
				
				if(methodName.equals("hashCode")) {
					/* TODO: Should this use "System.identityHashCode(proxy)" */
					return this.impl.valueByName.hashCode();
				}
				
				if(methodName.equals("equals")) {
					return arguments[0] == this;
				}
			}
			
			Map<String, Object> values = this.impl.valueByMethod;
			if(method.getParameterCount() == 0) {
				Object value = values.getOrDefault(methodName, EMPTY);
				if(value != EMPTY) {
					return value;
				}
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
	private final Object wrappedObject;
	
	/* 
	 * TODO: Not sure if it makes more sense to use the Method reference as the key,
	 * not really sure what the side effects of that is nor if that would result in
	 * worse performance, so I am just going to go with a simple String key and a
	 * "Method#getParameterCount == 0" check to determine the method.
	 */
	/* TODO: Can we use an IdentityHashMap for this? */
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
	
	private InterfaceConfigImpl(Class<?> interfaze, IConfig config) {
		this(null, interfaze, config, null);
	}
	
	private InterfaceConfigImpl(InterfaceConfigImpl parent, Class<?> proxiedInterface, IConfig config) {
		this(parent, proxiedInterface, config, null);
	}
	
	private InterfaceConfigImpl(InterfaceConfigImpl parent, Class<?> proxiedInterface, IConfig config, Object wrappedObject) {
		if(!proxiedInterface.isInterface()) {
			throw new IllegalArgumentException(proxiedInterface + " is not an interface");
		}
		
		this.parent = parent;
		
		this.proxiedInterface = proxiedInterface;
		
		this.handler = new Handler(this);
		this.invocationHandler = new DelegateInvocationHandler(this.handler);
		this.proxy = this.proxy(this.invocationHandler);
		
		this.config = Objects.requireNonNull(config);
		this.wrappedObject = wrappedObject;
		
		this.valueByMethod = new HashMap<>();
		this.valueByName = new HashMap<>();
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
	
	/* TODO: Support Collections and Maps */
	private Object convertDefaultValue(Class<?> type, Object value) {
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
		 * TODO: Test if this works properly for records.
		 * 
		 * See example below.
		 */
		/*
		interface Ratelimits {
			default Ratelimit route() {
				return new Ratelimit.Impl(5, TimeUnit.SECONDS.toMillis(5));
			}
			
			default Ratelimit anotherRoute() {
				return new Ratelimit.Impl(10, TimeUnit.SECONDS.toMillis(5));
			}
		}
		
		interface Ratelimit {
		
			class Impl implements Ratelimit {
				private final long count;
				private final long time;
		
				public Impl(long count, long time) {
					this.count = count;
					this.time = time;
				}
		
				@Override
				public long count() {
					return this.count;
				}
		
				@Override
				public long time() {
					return this.time;
				}
			}
		
			long count();
			long time();
		}
		*/
		return InterfaceConfigImpl.createInternal(ConfigFactory.empty(), value, this, type);
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
		if(this.config.has(name)) {
			return this.getValue(method.getGenericReturnType(), returnType, this.config, name);
		}
		
		/*
		 * We only want to compute the default value if it's a proxy instance
		 * because we also pass the wrappedObject instance here, which would cause
		 * it to not call the method
		 */
		if(!method.isDefault() && Proxy.class.isAssignableFrom(instance.getClass())) {
			return this.defaultValue(returnType);
		}
		
		try {
			return method.invoke(instance);
		}catch(IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new RuntimeException("Failed to get the default value for " + method, e);
		}
	}
	
	private void postLoadValue(Object object) {
		if(object instanceof InternalConfigImpl) {
			/*
			 * We compute the values after adding the proxy object to the stored values,
			 * this allows us to use self-referencing configs (through their parent),
			 * see example below.
			 */
			/*
			public interface ParentConfig {
				
				public interface ChildConfig {
					
					@Parent
					public ParentConfig getParent();
					
					public default int getValue() {
						ChildConfig defaultChild = this.getParent().getDefaultChild();
						if(defaultChild == this) {
							return 5;
						}
						 
						return defaultChild.getValue();
					}
				}
				
				public ChildConfig getDefaultChild();
				public ChildConfig getChild();
			}
			*/
			
			((InternalConfigImpl) object).__INTERNAL_IMPL__().computeValues();
			
			return;
		}
		
		if(object instanceof Collection) {
			for(Object value : (Collection<?>) object) {
				this.postLoadValue(value);
			}
			
			return;
		}
		
		if(object instanceof Map) {
			for(Object value : ((Map<?, ?>) object).values()) {
				this.postLoadValue(value);
			}
			
			return;
		}
	}
	
	private boolean isGetter(Method method) {
		/* Would anyone ever have this? and why? is there anything we need to support? */
		if(DataTypeUtility.isVoid(method.getReturnType())) {
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
	
	private void computeValues() {
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
			/* TODO: Is there anything we can do with static methods? */
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
				/* TODO: Should this fail if it can not find one or should it just return null? */
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
		
		Map<String, Method> wrappedMethods;
		if(this.wrappedObject != null) {
			/*
			 * This is somewhat "dangerous" (it just deviates from the behaviour of everything else)
			 * as we can't ensure each method is only called once, unfortunately I don't really see any
			 * way around it, we would just have to inform the user of the behaviour and to suggest they
			 * only return the default values directly and don't do anything fancy like calling other methods
			 * defined in the class, as long as the methods are not defined in the class you will still be able
			 * to call methods in the config interface.
			 */
			wrappedMethods = Arrays.stream(this.wrappedObject.getClass().getDeclaredMethods())
				.filter(this::isGetter)
				.collect(Collectors.toMap(Method::getName, Function.identity()));
		}else{
			wrappedMethods = Collections.emptyMap();
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
				Object instance;
				if(wrappedMethods.containsKey(method.getName())) {
					instance = this.wrappedObject;
				}else{
					instance = proxy;
				}
				
				String name = this.getName(naming, method);
				Object value = this.convertDefaultValue(method.getReturnType(), this.computeValue(instance, method, name));
				
				this.valueByMethod.put(method.getName(), value);
				this.valueByName.put(name, value);
				
				this.postLoadValue(value);
				return value;
			}
			
			if(computeMethods.remove(method)) {
				Object value = method.invoke(proxy);
				this.valueByMethod.put(method.getName(), this.convertDefaultValue(method.getReturnType(), value));
				
				this.postLoadValue(value);
				return value;
			}
			
			return this.handler.invoke(proxy, method, arguments);
		};
		
		for(Method method : new ArrayList<>(propertyMethods)) {
			if(!propertyMethods.remove(method)) {
				continue;
			}
			
			Object instance;
			if(wrappedMethods.containsKey(method.getName())) {
				instance = this.wrappedObject;
			}else{
				instance = this.proxy;
			}
			
			String name = this.getName(naming, method);
			Object value = this.convertDefaultValue(method.getReturnType(), this.computeValue(instance, method, name));
			
			this.valueByMethod.put(method.getName(), value);
			this.valueByName.put(name, value);
			
			this.postLoadValue(value);
		}
		
		for(Method method : new ArrayList<>(computeMethods)) {
			if(!computeMethods.remove(method)) {
				continue;
			}
			
			Object value;
			try {
				value = this.convertDefaultValue(method.getReturnType(), method.invoke(this.proxy));
			}catch(IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				throw new RuntimeException("Failed to compute the value for " + method, e);
			}
			
			this.valueByMethod.put(method.getName(), value);
			
			this.postLoadValue(value);
		}
		
		this.invocationHandler.handler = this.handler;
	}
}