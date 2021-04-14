/*
 *  BluSunrize
 *  Copyright (c) 2021
 *
 *  This code is licensed under "Blu's License of Common Sense"
 *  Details can be found in the license file in the root folder of this project
 */

package blusunrize.immersiveengineering.common.util.compat.computers.generic;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Primitives;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;

public class ComputerCallback<T>
{
	private final List<ArgumentType> userArguments;
	private final Function<Object, Object[]> wrapReturnValue;
	private final MethodHandle caller;
	private final String name;
	private final boolean isAsync;

	private ComputerCallback(
			Callback<T> owner, Method method, LuaTypeConverter converters
	) throws IllegalAccessException
	{
		this.caller = MethodHandles.lookup().unreflect(method).bindTo(owner);
		Function<Object, Object[]> wrapResult;
		Class<?> resultType = method.getReturnType();
		isAsync = method.getAnnotation(ComputerCallable.class).isAsync();
		Preconditions.checkState(isAsync||!resultType.equals(EventWaiterResult.class));
		if(Object[].class.equals(resultType))
			wrapResult = o -> (Object[])o;
		else if(void.class.equals(resultType))
			wrapResult = $ -> new Object[0];
		else
			wrapResult = o -> new Object[]{o};
		this.wrapReturnValue = wrapResult.compose(converters.getConverter(resultType));
		this.name = owner.renameMethod(method.getName());
		Class<?>[] allArguments = method.getParameterTypes();
		Preconditions.checkState(allArguments.length > 0);
		Preconditions.checkState(allArguments[0].equals(CallbackEnvironment.class));
		List<ArgumentType> userArguments = new ArrayList<>(allArguments.length-1);
		for(int i = 1; i < allArguments.length; ++i)
			userArguments.add(new ArgumentType(method, i));
		this.userArguments = ImmutableList.copyOf(userArguments);
	}

	public String getName()
	{
		return name;
	}

	public static <T> List<ComputerCallback<? super T>>
	getInClass(Callback<T> provider, LuaTypeConverter converters) throws IllegalAccessException
	{
		List<ComputerCallback<? super T>> callbacks = new ArrayList<>();
		for(Method m : provider.getClass().getMethods())
			if(m.isAnnotationPresent(ComputerCallable.class))
				callbacks.add(new ComputerCallback<>(provider, m, converters));
		for(Callback<? super T> extra : provider.getAdditionalCallbacks())
			callbacks.addAll(getInClass(extra, converters));
		Set<String> names = new HashSet<>();
		for(ComputerCallback<?> cb : callbacks)
			if(!names.add(cb.getName()))
				throw new RuntimeException("Duplicate method name "+cb.getName());
		return callbacks;
	}

	public Object[] invoke(Object[] arguments, CallbackEnvironment<T> env) throws Throwable
	{
		if(arguments.length!=this.userArguments.size())
			throw new RuntimeException(
					"Unexpected number of arguments: Expected "+this.userArguments.size()+", got "+arguments.length
			);
		Object[] realArguments = new Object[arguments.length+1];
		System.arraycopy(arguments, 0, realArguments, 1, arguments.length);
		realArguments[0] = env;
		for(int i = 0; i < arguments.length; ++i)
		{
			int realIndex = i+1;
			ArgumentType expectedType = this.userArguments.get(i);
			realArguments[realIndex] = expectedType.transform(arguments[i]);
		}
		return wrapReturnValue.apply(caller.invokeWithArguments(realArguments));
	}

	public boolean isAsync()
	{
		return isAsync;
	}

	private static Number fixNumber(Double fromLua, Class<?> correctType)
	{
		if(correctType==Double.class)
			return fromLua;
		else if(correctType==Float.class)
			return fromLua.floatValue();
		else if(correctType==Byte.class)
			return fromLua.byteValue();
		else if(correctType==Short.class)
			return fromLua.shortValue();
		else if(correctType==Integer.class)
			return fromLua.intValue();
		else if(correctType==Long.class)
			return fromLua.longValue();
		else
			return fromLua;
	}

	private static class ArgumentType
	{
		private final Class<?> type;
		private final boolean isIndex;
		private final int indexForError;

		private ArgumentType(Method method, int argIndex)
		{
			Class<?> actualType = method.getParameterTypes()[argIndex];
			if(actualType.isPrimitive())
				this.type = Primitives.wrap(actualType);
			else
				this.type = actualType;
			Annotation[] annotations = method.getParameterAnnotations()[argIndex];
			this.isIndex = Arrays.stream(annotations)
					.map(Annotation::annotationType)
					.anyMatch(c -> c==IndexArgument.class);
			if(this.isIndex)
				Preconditions.checkState(this.type==Integer.class);
			this.indexForError = argIndex;
		}

		public Object transform(Object userInput)
		{
			if(!userInput.getClass().equals(type))
			{
				if(Number.class.isAssignableFrom(type)&&userInput instanceof Double)
					userInput = fixNumber((Double)userInput, type);
				else
					throw new RuntimeException(
							"Unexpected argument type at argument "+indexForError+": Expected "+
									type.getSimpleName()+", got "+userInput.getClass().getSimpleName()
					);
			}
			if(isIndex)
				return ((Integer)userInput)-1;
			else
				return userInput;
		}
	}
}
