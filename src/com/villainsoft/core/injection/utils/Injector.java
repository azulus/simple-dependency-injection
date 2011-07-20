package com.villainsoft.core.injection.utils;

import java.io.File;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;

import javax.servlet.ServletContext;

import org.scannotation.AnnotationDB;

import com.villainsoft.core.injection.common.annotations.Component;
import com.villainsoft.core.injection.common.annotations.Controller;
import com.villainsoft.core.injection.common.annotations.Inject;
import com.villainsoft.core.injection.common.annotations.Repository;
import com.villainsoft.core.injection.common.annotations.Service;

public class Injector {
	private Logger log = Logger.getLogger(Injector.class.getName());

	private static class Loader {
		private static Injector INSTANCE = new Injector();
	}

	private Map<Class<?>, List<Object>> types = new HashMap<Class<?>, List<Object>>();
	private Map<Class<?>, Class<?>> interfaceMapping = new HashMap<Class<?>, Class<?>>();
	private Map<String, Class<?>> interfaceMappingByString = new HashMap<String, Class<?>>();
	private Map<Class<?>, Object> instances = new HashMap<Class<?>, Object>();
	private ServletContext servletContext;

	private Injector() {
	}

	public static Injector get() {
		return Loader.INSTANCE;
	}

	public static <K> K get(Class<K> clazz) {
		return (K) (get().instances.get(get().interfaceMapping.get(clazz)));
	}

	public static Object get(String className) {
		return get().instances.get(get().interfaceMappingByString
				.get(className));
	}

	public ServletContext getServletContext() {
		return this.servletContext;
	}

	public void init(ServletContext servletContext) {
		this.servletContext = servletContext;

		List<URL> urls = getURLs(servletContext);
		try {

			AnnotationDB db = new AnnotationDB();

			db.scanArchives(urls.toArray(new URL[] {}));
			System.out.println("scanned classpath");

			Map<String, Set<String>> annotationIndex = db.getAnnotationIndex();

			List<Class<?>> classes = new ArrayList<Class<?>>();
			List<String> classList = new ArrayList<String>();
			Class<?>[] annotationTypes = new Class<?>[] { Component.class,
					Service.class, Repository.class, Controller.class };

			Map<Class<?>, List<Class<?>>> tempTypes = new HashMap<Class<?>, List<Class<?>>>();

			for (int a = 0; a < annotationTypes.length; a++) {
				tempTypes.put(annotationTypes[a], new ArrayList<Class<?>>());

				Set<String> classNames = annotationIndex.get(annotationTypes[a]
						.getName());
				if (classNames != null) {
					Iterator<String> classNameIterator = classNames.iterator();
					while (classNameIterator.hasNext()) {
						String clazzName = classNameIterator.next();
						try {
							System.out.println(clazzName);
							Class clazz = Class.forName(clazzName);
							if (!classes.contains(clazz)) {
								tempTypes.get(annotationTypes[a]).add(clazz);
								classes.add(clazz);
							}
						} catch (Throwable t) {
							// t.printStackTrace();
							System.out.println("Failed instantiating "
									+ clazzName);
							t.printStackTrace();
						}
					}
				}
			}

			boolean instantiatedSomething;
			do {
				instantiatedSomething = false;

				List<Class<?>> tempList = new ArrayList<Class<?>>();
				tempList.addAll(classes);

				Iterator<Class<?>> classIterator = tempList.iterator();
				while (classIterator.hasNext()) {
					Class<?> clazz = classIterator.next();
					Constructor[] constructors = clazz.getConstructors();

					boolean found = false;
					Object obj = null;

					for (int a = 0; !found && a < constructors.length; a++) {
						if (constructors[a].getAnnotation(Inject.class) != null) {
							found = true;

							Class<?>[] types = constructors[a]
									.getParameterTypes();
							List<Object> params = new ArrayList<Object>();
							boolean foundParams = true;
							for (int b = 0; foundParams && b < types.length; b++) {
								if (interfaceMapping.containsKey(types[b])) {
									params.add(instances.get(interfaceMapping
											.get(types[b])));
								} else {
									foundParams = false;
								}
							}
							if (foundParams) {
								obj = constructors[a].newInstance(params
										.toArray(new Object[] {}));
							}
						}
					}

					if (!found) {
						obj = clazz.newInstance();
					}

					if (obj != null) {
						System.out.println("Instantiated "
								+ obj.getClass().getName());
						instantiatedSomething = true;

						Class<?>[] interfaces = clazz.getInterfaces();

						instances.put(clazz, obj);
						interfaceMapping.put(clazz, clazz);
						interfaceMappingByString.put(clazz.getSimpleName(),
								clazz);
						for (int a = 0; a < interfaces.length; a++) {
							interfaceMapping.put(interfaces[a], clazz);
							interfaceMappingByString.put(
									interfaces[a].getSimpleName(), clazz);
						}

						classes.remove(clazz);
					}
				}

			} while (instantiatedSomething);

			Iterator<Class<?>> missed = classes.iterator();
			while (missed.hasNext()) {
				System.out.println("Unable to instantiate "
						+ missed.next().getName());
			}

			Iterator<Entry<Class<?>, List<Class<?>>>> typeAnnotations = tempTypes
					.entrySet().iterator();
			while (typeAnnotations.hasNext()) {
				Entry<Class<?>, List<Class<?>>> entry = typeAnnotations.next();
				List<Object> objects = new ArrayList<Object>();
				Iterator<Class<?>> typeIterator = entry.getValue().iterator();
				while (typeIterator.hasNext()) {
					Class<?> type = typeIterator.next();
					objects.add(instances.get(type));
				}
				types.put(entry.getKey(), objects);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public Collection<Object> getByType(Class<?> type) {
		return types.get(type);
	}

	public static List<URL> getURLs(final ServletContext servletContext) {
		List<URL> returnUrls = new ArrayList<URL>();
		
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		while(loader != null) {
			if( loader instanceof URLClassLoader) {
				URL[] urls = ((URLClassLoader) loader).getURLs();
				if( urls != null ) Collections.addAll(returnUrls, urls);
			}
			loader = loader.getParent();
		}
		
		String javaClassPath = System.getProperty("java.class.path");
		if( javaClassPath != null ) {
			String[] pathParts = javaClassPath.split(File.pathSeparator);
			for(int a=0; a<pathParts.length; ++a) {
				try{
					returnUrls.add(new File(pathParts[a]).toURI().toURL());
				}catch(Throwable t){}
			}
		}
		
		Iterator iterator = servletContext.getResourcePaths("/WEB-INF/lib").iterator();
		while(iterator.hasNext()) {
			try{
				returnUrls.add(servletContext.getResource((String) iterator.next()));
			}catch(Throwable t){}
		}
		
		try{
			File file = new File(servletContext.getRealPath("/WEB-INF/classes"));
			if(file.exists()) returnUrls.add( file.toURL() );
		}catch(Throwable t){}
		
		return returnUrls;
	}
}
