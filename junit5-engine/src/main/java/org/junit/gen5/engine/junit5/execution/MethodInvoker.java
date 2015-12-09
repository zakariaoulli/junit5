/*
 * Copyright 2015 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.junit.gen5.engine.junit5.execution;

import static java.util.stream.Collectors.*;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;

import org.junit.gen5.api.extension.MethodParameterResolver;
import org.junit.gen5.api.extension.ParameterResolutionException;
import org.junit.gen5.api.extension.TestExtensionContext;
import org.junit.gen5.commons.util.Preconditions;
import org.junit.gen5.commons.util.ReflectionUtils;

/**
 * {@code MethodInvoker} encapsulates the invocation of a method,
 * including support for dynamic resolution of method parameters via
 * {@link MethodParameterResolver MethodParameterResolvers}.
 *
 * @since 5.0
 */
public class MethodInvoker {

	private final Method method;
	private final TestExtensionContext context;
	private final TestExtensionRegistry extensionRegistry;

	public MethodInvoker(Method method, TestExtensionContext context, TestExtensionRegistry extensionRegistry) {
		Preconditions.notNull(method, "method must not be null");
		Preconditions.notNull(context, "context must not be null");
		Preconditions.notNull(extensionRegistry, "extensionRegistry must not be null");
		this.method = method;
		this.context = context;
		this.extensionRegistry = extensionRegistry;
	}

	public Object invoke() {
		return ReflectionUtils.invokeMethod(this.method, context.getTestInstance(), resolveParameters());
	}

	/**
	 * Resolve the array of parameters for the configured method.
	 *
	 * @param testExecutionContext the current test execution context
	 * @return the array of Objects to be used as parameters in the method
	 * invocation; never {@code null} though potentially empty
	 * @throws ParameterResolutionException
	 */
	private Object[] resolveParameters() throws ParameterResolutionException {
		// @formatter:off
		return Arrays.stream(this.method.getParameters())
				.map(param -> resolveParameter(param))
				.toArray(Object[]::new);
		// @formatter:on
	}

	private Object resolveParameter(Parameter parameter) {
		try {
			// @formatter:off
			List<MethodParameterResolver> matchingResolvers = extensionRegistry.getExtensions(MethodParameterResolver.class)
					.filter(resolver -> resolver.supports(parameter, context))
					.collect(toList());
			// @formatter:on

			if (matchingResolvers.size() == 0) {
				throw new ParameterResolutionException(
					String.format("No MethodParameterResolver registered for parameter [%s] in method [%s].", parameter,
						this.method.toGenericString()));
			}
			if (matchingResolvers.size() > 1) {
				// @formatter:off
				String resolverNames = matchingResolvers.stream()
						.map(resolver -> resolver.getClass().getName())
						.collect(joining(", "));
				// @formatter:on
				throw new ParameterResolutionException(String.format(
					"Discovered multiple competing MethodParameterResolvers for parameter [%s] in method [%s]: %s",
					parameter, this.method.toGenericString(), resolverNames));
			}
			return matchingResolvers.get(0).resolve(parameter, context);
		}
		catch (Exception ex) {
			if (ex instanceof ParameterResolutionException) {
				throw (ParameterResolutionException) ex;
			}
			throw new ParameterResolutionException(String.format("Failed to resolve parameter [%s] in method [%s]",
				parameter, this.method.toGenericString()), ex);
		}
	}

}
