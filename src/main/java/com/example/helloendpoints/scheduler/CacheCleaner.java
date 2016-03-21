package com.example.helloendpoints.scheduler;

import java.io.IOException;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.example.helloendpoints.api.IRkitNorthBoundRestAPI;
import com.example.helloendpoints.api.IRkitSouthboundRestAPI;
import com.example.helloendpoints.entity.Device;
import com.example.helloendpoints.entity.Message;
import com.example.helloendpoints.entity.MyUser;
import com.example.helloendpoints.entity.Signal;
import com.example.helloendpoints.postdata.PostDevice;
import com.example.helloendpoints.postdata.PostMessage;
import com.google.api.server.spi.response.NotFoundException;
import com.googlecode.objectify.ObjectifyService;

/**
 * 
 * @author eqiglii
 * 
 *         Use the default AppEngine queue for executing scheduled tasks
 * 
 *         AppEngine provides functionality to execute code in the background.
 *         The easiest way is to use the default push queue. Basically, you tell
 *         the default push queue what endpoint should handle the task and
 *         AppEngine will execute that endpoint in background. You can also
 *         provide parameters that will be populated as HTTP request parameters.
 *         No additional configuration is needed, simply instantiate the queue
 *         as in our test code.
 * 
 *         In the code above we call endpoint /sender with parameter job_id
 *         whose value is always job_id. Obviously, this is not a very useful
 *         parameter as it stands. However, in future I will change this
 *         parameter to provide enough information to the background task
 *         handler to carry out execution of the task. For example, the
 *         parameter could provide a key to an entity that contains users which
 *         should be notified.
 * 
 *         The code for the /sender handler:
 *         
 *         As you see, it does not do much but it provides the almighty log statement which we will use to confirm that our code is working as we want it to.
 *
 */

@SuppressWarnings("serial")
public class CacheCleaner extends HttpServlet {
    private static final Logger log = Logger.getLogger(CacheCleaner.class.getName());

    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        log.severe("EXECUTING TASK FROM DEFAULT QUEUE: " + req.getParameter("job_id"));
        
        // clean the cache
		// every 30min to clear cache. Learning signal cache, actuation command
		// cache. Minor risk of lost learning signals, actuation commands. Do a
		// quick scanning check when clearing cache to skip the ones who have
        // learning signals and actuation commands?
	    IRkitNorthBoundRestAPI.postMessageList.clear();	// store the actuation commands, with server-defined signal id
        IRkitNorthBoundRestAPI.postUserList.clear();	// store the new learned signals
        IRkitSouthboundRestAPI.postDeviceList.clear();	// store the actuation commands, in raw message format, which is understood by device and passed onto device
    }
}