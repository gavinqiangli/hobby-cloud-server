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
public class SignalSender extends HttpServlet {
    private static final Logger log = Logger.getLogger(SignalSender.class.getName());

    /**
     * Testing Passed OK
     * Same response as Call IRkitNorthBoundRestAPI: postMessagesV2
     */
    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        log.info("EXECUTING TASK FROM FAST QUEUE " + req.getParameter("job_id"));
        String client_key = req.getParameter("client_key");
        String device_id = req.getParameter("device_id");
        String signal_id = req.getParameter("signal_id");

        // Call IRkitNorthBoundRestAPI: postMessagesV2
        // how to call internal end point here?
        
        //Is it possible to use the gapi.client.request to make REST calls to the api I developed using cloud endpoints?
        //I figured out the way to do it. I need to pass the root parameter when creating the request object.
        /*
        gapi.client.request({
            'path': '/resource/v1/get',
            'root': 'http://localhost:8080/_ah/api'
          }).execute(function(response){
            // do something
          });
        
        gapi.client.helloworld.greetings.post({
            'client_key': client_key,
      	  	'device_id': device_id,
      	  	'signal_id': signal_id
          }).execute(function(resp) {
            if (!resp.code) {
              google.devrel.samples.hello.print(resp);
            }
          });
          */

		// what about just copy the code of IRkitNorthBoundRestAPI: postMessagesV2
		// 1. first authenticate the user
		try {
			MyUser user = ObjectifyService.ofy().load().type(MyUser.class).filter("client_key", client_key).first()
					.now();

		} catch (IndexOutOfBoundsException e) {
			throw new IOException("User not found with an client_key: " + client_key);
		}

		// 2. store the Message on Server data store
		// begin data store
		Message messageData;

		// convert string id to long id
		Long ldeviceid = Long.valueOf(device_id);
		Long lsignalid = Long.valueOf(signal_id);

		// construct the JSON String "transparent_message" based on signal_id.
		String transparent_message = "";

		// 2.1 first find the signal data
		Signal signal;
		try {
			signal = ObjectifyService.ofy().load().type(Signal.class).id(lsignalid).now();
		} catch (IndexOutOfBoundsException e) {
			throw new IOException("Signal not found with an signal_id: " + signal_id);
		}

		// 2.2 construct the JSON string
		JSONObject jsonObj = new JSONObject();
		try {
			jsonObj.put("format", signal.format);
			jsonObj.put("freq", signal.freq);
			if (signal.data != null) {
				JSONArray dataArray = new JSONArray();
				for (int value : signal.data) {
					dataArray.put(value);
				}
				jsonObj.put("data", dataArray);
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
		transparent_message = jsonObj.toString();

		// 2.3 now store the message on server data store
		messageData = new Message(client_key, ldeviceid, transparent_message, lsignalid);

		// Use Objectify to save the greeting and now() is used to make the call
		// synchronously as we
		// will immediately get a new page using redirect and we want the data
		// to be present.
		ObjectifyService.ofy().save().entity(messageData).now();
		// end data store

		// 2.4 also save it into PostMessageList Memory
		// get the message id from data store
		// Now the id is available
		PostMessage postmessage;
		postmessage = new PostMessage(messageData.id, System.currentTimeMillis(), client_key, ldeviceid,
				transparent_message, lsignalid);
		IRkitNorthBoundRestAPI.postMessageList.add(postmessage);

		// 3. find the device id who is long polling to the server, from
		// southbound getMessages request
		// first, get the device instance, either from an existing device list,
		// or, create new device into the device list if not existing
		PostDevice postdevice;
		int index = -1;
		for (int i = 0; i < IRkitSouthboundRestAPI.postDeviceList.size(); i++) {
			if (IRkitSouthboundRestAPI.postDeviceList.get(i).id.equals(ldeviceid)) {
				index = i;
				break;
			}
		}
		if (index == -1) { // if device doesn't exist in the list
			try {
				Device device = ObjectifyService.ofy().load().type(Device.class).id(ldeviceid).now();
				postdevice = new PostDevice(device.id, device.hostname, device.device_key, device.client_key);
				IRkitSouthboundRestAPI.postDeviceList.add(postdevice);
			} catch (IndexOutOfBoundsException e) {
				throw new IOException("Device not found with an device_id: " + device_id);
			}
		} else {
			postdevice = IRkitSouthboundRestAPI.postDeviceList.get(index);
		}

		// 4. add the new signal command into postdevice
		postdevice.transparentMessageBuffer.add(postmessage);        
    }
}