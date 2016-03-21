package com.example.helloendpoints;
import com.example.helloendpoints.entity.Device;
import com.example.helloendpoints.entity.Irkit;
import com.example.helloendpoints.entity.Message;
import com.example.helloendpoints.entity.MyUser;
import com.example.helloendpoints.entity.Schedule;
import com.example.helloendpoints.entity.Signal;
import com.example.helloendpoints.entity.Temperature;
import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.ObjectifyFactory;
import com.googlecode.objectify.ObjectifyService;

import javax.servlet.ServletContextListener;
import javax.servlet.ServletContextEvent;

/**
 * OfyHelper, a ServletContextListener, is setup in web.xml to run before a JSP is run.  This is
 * required to let JSP's access Ofy.
 **/
public class OfyHelper implements ServletContextListener {
  public void contextInitialized(ServletContextEvent event) {
    // This will be invoked as part of a warmup request, or the first user request if no warmup
    // request.
    ObjectifyService.register(Irkit.class);
    ObjectifyService.register(Schedule.class);
    ObjectifyService.register(Signal.class);
    ObjectifyService.register(Temperature.class);
    ObjectifyService.register(MyUser.class);
    ObjectifyService.register(Message.class);
    ObjectifyService.register(Device.class);


  }

  public void contextDestroyed(ServletContextEvent event) {
    // App Engine does not currently invoke this method.
  }
}