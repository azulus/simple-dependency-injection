package com.villainsoft.core.injection.utils;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

public class ContextListener implements ServletContextListener{
	public void contextDestroyed(ServletContextEvent sce) {}
	public void contextInitialized(ServletContextEvent event) {
		Injector.get().init(event.getServletContext());
	}
}
