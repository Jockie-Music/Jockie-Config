package com.jockie.config.impl.wrapper;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Objects;

public class DefaultValueProxy implements InvocationHandler {
	
	private static int getMajorVersion() {
		String version = System.getProperty("java.version");
		if(version.startsWith("1.")) {
			return Integer.parseInt(version.substring(2, 3));
		}
		
		int majorVersionIndex = version.indexOf(".");
		if(majorVersionIndex != -1) {
			return Integer.parseInt(version.substring(0, majorVersionIndex));
		}
		
		return Integer.parseInt(version);
	}
	
	private static final int JAVA_MAJOR_VERSION = DefaultValueProxy.getMajorVersion();
	
	protected final Class<?> proxiedInterface;
	
	public DefaultValueProxy(Class<?> interfaze) {
		this.proxiedInterface = Objects.requireNonNull(interfaze);
		
		if(!this.proxiedInterface.isInterface()) {
			throw new IllegalArgumentException(interfaze + " is not an interface");
		}
	}
	
	@Override
	public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
		if(!method.isDefault()) {
			throw new UnsupportedOperationException("Method: " + method + ", for interface: " + this.proxiedInterface + ", does not have a default implementation");
		}
		
		return this.callDefault(proxy, method, arguments);
	}
	
	public Object callDefault(Object proxy, Method method, Object[] arguments) throws Throwable {
		if(JAVA_MAJOR_VERSION > 8) {
			/* Java 9+ */
			return MethodHandles.lookup()
				.findSpecial(this.proxiedInterface, method.getName(), MethodType.methodType(method.getReturnType(), method.getParameterTypes()), this.proxiedInterface)
				.bindTo(proxy)
				.invokeWithArguments(arguments);
		}
		
		/* Java 8 -> 15; Does not work in 16 (illegal access) */
		Constructor<Lookup> constructor = Lookup.class.getDeclaredConstructor(Class.class);
		constructor.setAccessible(true);
		
		return constructor.newInstance(this.proxiedInterface)
			.in(this.proxiedInterface)
			.unreflectSpecial(method, this.proxiedInterface)
			.bindTo(proxy)
			.invokeWithArguments(arguments);
	}
}