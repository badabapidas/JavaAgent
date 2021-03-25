package net.bytebuddy;

import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.matcher.ElementMatchers;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.Collections;
import java.util.concurrent.Callable;

public class BootstrapAgent {
	public static void main(String[] args) throws Exception {
		premain(null, ByteBuddyAgent.install());
		HttpURLConnection urlConnection = (HttpURLConnection) new URL("http://www.google.com").openConnection();
		System.out.println(urlConnection.getRequestMethod());
	}

	public static void premain(String arg, Instrumentation instrumentation) throws Exception {
		File tempDirectory = Files.createTempDirectory("tmp").toFile();
		ClassInjector.UsingInstrumentation
				.of(tempDirectory, ClassInjector.UsingInstrumentation.Target.BOOTSTRAP, instrumentation)
				.inject(Collections.singletonMap(new TypeDescription.ForLoadedType(MyInterceptor.class),
						ClassFileLocator.ForClassLoader.read(MyInterceptor.class)));

		new AgentBuilder.Default().ignore(ElementMatchers.nameStartsWith("net.bytebuddy."))
				.with(new AgentBuilder.InjectionStrategy.UsingInstrumentation(instrumentation, tempDirectory))
				.type(ElementMatchers.nameEndsWith(".HttpURLConnection"))
				.transform((builder, typeDescription, classLoader, module) -> builder
						.method(ElementMatchers.named("getRequestMethod"))
						.intercept(MethodDelegation.to(MyInterceptor.class)))
				.installOn(instrumentation);
	}

	public static class MyInterceptor {
		public static String intercept(@SuperCall Callable<String> zuper) throws Exception {
			System.out.println("Intercepted!");
			return zuper.call();
		}
	}
}