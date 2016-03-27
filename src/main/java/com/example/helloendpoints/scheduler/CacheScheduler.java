package com.example.helloendpoints.scheduler;

import java.io.IOException;
import java.util.Calendar;
import java.util.List;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.example.helloendpoints.entity.MyUser;
import com.example.helloendpoints.entity.Schedule;
import com.google.api.server.spi.response.NotFoundException;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.api.datastore.Query.FilterOperator;

import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.googlecode.objectify.ObjectifyService;

import static com.google.appengine.api.taskqueue.TaskOptions.Builder.*;


/**
 * 
 * @author eqiglii
 * 
 *         Now let’s write the code behind the /cron endpoint. The code will
 *         check current time, retrieve all scheduled tasks which have next run
 *         either in the past or right now and execute them. If the task is
 *         supposed to be run several times we will reduce the remaining run
 *         counter, calculate the next execution time and save the changes. If
 *         the task has no more runs we delete the entity from the data-store:
 * 
 *         The handler executes a query against the data-store to retrieve due
 *         scheduled tasks. Note that it uses ancestor query to ensure that
 *         return values are consistent. Then we iterate through retrieved
 *         scheduled tasks and execute processJob method. The processJob
 *         introduces AppEngine default queue which we use for executing our
 *         scheduled tasks in the background.
 *
 */

@SuppressWarnings("serial")
public class CacheScheduler extends HttpServlet {
    private static final Logger log = Logger.getLogger(CacheScheduler.class
            .getName());

    public void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        log.info("Running System schedule check, loop every 30 minute");
        log.info("Current time: " + System.currentTimeMillis());

		processJob();
       
    }

    private void processJob() {
        Queue queue = QueueFactory.getDefaultQueue();
        // "job id" shall be unique here to identify a job
        String job_id = String.valueOf(System.currentTimeMillis());
        queue.add(withUrl("/cachecleaner").param("job_id", job_id));
        
		// You cannot call a Google Cloud Endpoint from a push queue. Instead,
		// you should issue a request to a target that is served by a handler
		// that's specified in your app's configuration file or in a dispatch
		// file. That handler then calls the appropriate endpoint class and
		// method.
        
        //Note: You cannot call a Google Cloud Endpoint directly from a push task queue or a cron job.

	  }
}

