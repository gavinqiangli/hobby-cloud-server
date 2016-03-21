package com.example.helloendpoints.api;

import com.example.helloendpoints.Constants;
import com.example.helloendpoints.entity.Device;
import com.example.helloendpoints.entity.Message;
import com.example.helloendpoints.entity.MyUser;
import com.example.helloendpoints.entity.Schedule;
import com.example.helloendpoints.entity.Signal;
import com.example.helloendpoints.entity.Temperature;
import com.example.helloendpoints.postdata.PostDevice;
import com.example.helloendpoints.postdata.PostMessage;
import com.example.helloendpoints.postdata.PostSchedule;
import com.example.helloendpoints.postdata.PostSignal;
import com.example.helloendpoints.postdata.PostTemperature;
import com.example.helloendpoints.postdata.PostUser;
import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.response.NotFoundException;
import com.google.appengine.api.users.User;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.ObjectifyService;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Named;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Defines v1 of a IRKit Northbound API, which provides northbound methods.
 */
@Api(name = "northbound", version = "v1", scopes = { Constants.EMAIL_SCOPE }, clientIds = { Constants.WEB_CLIENT_ID,
		Constants.ANDROID_CLIENT_ID, Constants.IOS_CLIENT_ID,
		Constants.API_EXPLORER_CLIENT_ID }, audiences = { Constants.ANDROID_AUDIENCE })
public class IRkitNorthBoundRestAPI {
	

	/**
	 * Saved in the memory, should be cleared after a scheduled time period in order to release memory. 
	 * The schedule shall be selected very carefully, in order not to affect end users. e.g. during midnight when there are much fewer requests.
	 * Still, Problem is how to handle the lost cache data due to such memory release, e.g. actuation commands lost, new signal learnings lost, should we really clear the buffer?
	 * or should we never clear the buffer?
	 */
	public static ArrayList<PostUser> postUserList = new ArrayList<PostUser>();
	public static ArrayList<PostMessage> postMessageList = new ArrayList<PostMessage>();


	/**
	 * Door API For testing whether IRkit WiFi setup is ready and IRKit is connected 
	 * 
	 * POST https://irkitrestapi.appspot.com/_ah/api/northbound/v1/door
	 * POST /1/door
	 * 
	 * After you send your home Wi-Fi accesspoint's information along with a
	 * devicekey to IRKit through IRKit's own accesspoint (POST /wifi), use POST
	 * /1/door to check if IRKit had successfully connected to your home Wi-Fi
	 * accesspoint and to our server on Internet.
	 * 
	 * This request is a long polling request. Our server on Internet will
	 * immediately respond when server confirms IRKit is connected, otherwise
	 * you'll receive an empty response after timeout.
	 * 
	 * curl -i "https://api.getirkit.com/1/door" \ -d
	 * 'clientkey=XXXXXXXXXXXXXXXXXXXXXXXXXX' \ -d
	 * 'deviceid=XXXXXXXXXXXXXXXXXXXXXXXXXXX' HTTP/1.1 200 OK Server:
	 * ngx_openresty Date: Wed, 01 Jan 2014 17:04:59 GMT Content-Type:
	 * application/json; charset=utf-8 Content-Length: 24 Connection: keep-alive
	 * X-Content-Type-Options: nosniff
	 * 
	 * {"hostname":"IRKitXXXX"}
	 * 
	 * Request Parameters 
	 * Name 	Required 	Description 
	 * clientkey o 			see Overview
	 * deviceid o 			see Overview 
	 * 
	 * Response Parameters 
	 * Name 		Description 
	 * hostname 	Bonjour hostname (ex: #{hostname}.local), used by HTTP clients connected to the same Wi-Fi accesspoint.
	 *
	 * @POST("/1/door")
	 * void postDoor(@FieldMap Map<String, String> params, Callback<PostDoorResponse> callback);
	 */
	class PostDoorResponse {
        /*
         * Bonjour を使うことで同じWiFiアクセスポイントに接続したクライアントから #{hostname}.local として接続するために使います。
         */
        public String hostname;
    }
	@ApiMethod(path="door")
	public PostDoorResponse postDoor(@Named("clientkey") String clientkey, @Named("deviceid") String deviceid, PostDoorResponse postDoorResponse) throws NotFoundException {
		
		// shall we authenticate client_key here? shall we authenticate
		// client_key for every API call?
		// 1. first authenticate the user
		try {
			MyUser user = ObjectifyService.ofy().load().type(MyUser.class).filter("client_key", clientkey).first().now();

		} catch (IndexOutOfBoundsException e) {
			throw new NotFoundException("User not found with an clientkey: " + clientkey);
		}
		
		// convert string id to long id 
		Long ldeviceid = Long.valueOf(deviceid);
		
		// find out the device instance from data store
		// if device hasn't done bonjour and hasn't done the southbound API "postDoor" to register its hostname, the hostname field in data store will be empty
		// if device has done bonjour and has done the southbound API "postDoor" to register its hostname, the hostname field in data store shall be real hostname
		try {
			Device device = ObjectifyService.ofy().load().type(Device.class).id(ldeviceid).now();
			postDoorResponse.hostname = device.hostname;
			// this method only has success response
			return postDoorResponse;
		} catch (IndexOutOfBoundsException e) {
			throw new NotFoundException("Device not found with an deviceid: " + deviceid);
		}
	}
	
	
	
	/** Message API
	 * ** API is called by Android client app
	 **/
	
	/**
	 * GET https://irkitrestapi.appspot.com/_ah/api/northbound/v1/messages_v2
	 * @param clientkey
	 * @return
	 * @throws NotFoundException
	 * Client App is doing long polling "get" here, e.g. "get" for every 1s
	 * One user can have multiple irkit devices. 
	 * waitForSignal -> getMessage -> postSignalData is bound/registered per User, i.e.one user can register signal for any of his/her devices
	 * alternative is to register the signal per device
	 * right now it is registered/bound per User
	 */
	@ApiMethod(path="messages_v2")
	public PostSignal getMessagesV2(@Named("clientkey") String clientkey) throws NotFoundException {
		
		// first, get the user instance, either from an existing user list, or
		// create new user into the user list if not existing
		PostUser postuser = null;
		int index = -1;
		for (int i = 0; i < postUserList.size(); i++) {
			if (postUserList.get(i).client_key == clientkey) {
				index = i;
				break;
			}
		}
		if (index == -1) { // if user doesn't exist in the list
			try {
				MyUser user = ObjectifyService.ofy().load().type(MyUser.class).filter("client_key", clientkey).first().now();
				postuser = new PostUser(user.id, user.name, user.passwd, user.client_key, user.api_key, user.email);
				postUserList.add(postuser);
			} catch (IndexOutOfBoundsException e) {
				throw new NotFoundException("User not found with an clientkey: " + clientkey);
			}
		} else {
			postuser = postUserList.get(index);
		}
				
		// for the first time getMessages, newSignalData shall be always initiated "null"
		if (postuser.isInitialGetMessages == true) {
			postuser.newSignalMessage = null;
		}
		
		// for the second and onwards getMessages, don't touch newSignalData
		postuser.isInitialGetMessages = false;
		
		// if newSignalData is captured not "null", getMessages is returned not "null", wait/capture new signal data is done, 
		// getMessages session will be ended, now must reinitialize  
		if (postuser.newSignalMessage != null) {
			postuser.isInitialGetMessages = true;
			
			// need to convert the transparent_message received from southbound
			// postmessages request to the signal format
			// transparent_message format is
			// "{\"format\":\"raw\",\"freq\":38,\"data\":[18031,8755,1190,1190,1190]}"
			JSONObject jsonObj;
			String format = "";
			double freq = 0;
			int[] data = new int[100];
			try {
				jsonObj = new JSONObject(postuser.newSignalMessage.transparent_message);
				format = jsonObj.getString("format");
				freq = jsonObj.getDouble("freq");
				JSONArray data_array = jsonObj.getJSONArray("data");
				for (int i = 0; i < data_array.length(); i++) {
					data[i] = data_array.getInt(i);
				}
			} catch (JSONException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

			String signal_name = ""; // real name shall be given by end
										// user, and then return to server and
										// saved into server database

			Signal signalData = new Signal(null, signal_name, format, freq, data, clientkey);

			// Use Objectify to save the greeting and now() is used to make the
			// call
			// synchronously as we
			// will immediately get a new page using redirect and we want the
			// data
			// to be present.
			ObjectifyService.ofy().save().entity(signalData).now();
			// end data store
			
			PostSignal postsignal = new PostSignal();
			// Now the id is available
			postsignal.id = signalData.id;	// must also pass signal id to client app, in order for client app to give a signal name and pass signal name back to server data store
			postsignal.format = format;
			postsignal.freq = freq;
			postsignal.data = data;

			// success response
			return postsignal;
		}
		
		// failure response
		return null;
	}
	
	/**
	 * Client App is doing long polling "get" here, to receive/capture a newly learned signal data
	 *
	 * Original design
	 * According to Android client request, the server "getMessages" call shall do a callback to the Android client, 
	 * when server receives a "newSignalData" from the Irkit device
	 * 
	 * GET https://irkitrestapi.appspot.com/_ah/api/northbound/v1/messages
	 * GET /1/messages 
	 * Get latest received IR signal. This request is a long
	 * polling request. Provide clear=1 parameter to clear IR signal receive
	 * buffer on server. When IRKit device receives an IR signal, device sends
	 * it over to our server on Internet, and server passes it over as response.
	 * Server will respond with an empty response after timeout.
	 * 
	 * When you want to receive an IR signal, 1st request should add clear=1
	 * parameter, and following requests after timeout should not.
	 * 
	 * Use deviceid and hostname in response to distinguish which IRKit device
	 * received the IR signal in response.
	 *
	 * curl -i
	 * "https://api.getirkit.com/1/messages?clientkey=XXXXXXXXXXXXXXXX&clear=1"
	 * HTTP/1.1 200 OK Server: ngx_openresty Date: Tue, 07 Jan 2014 09:03:59 GMT
	 * Content-Type: application/json; charset=utf-8 Content-Length: 481
	 * Connection: keep-alive ETag: "-19571392" X-Content-Type-Options: nosniff
	 * 
	 * {"message":{"format":"raw","freq":38,"data":[18031,8755,1190,1190,1190,
	 * 3341,1190,3341,1190,3341,1190,1190,1190,3341,
	 * 1190,3341,1190,3341,1190,3341,1190,3341,1190,3341,1190,1190,1190,1190,
	 * 1190,1190,1190,1190,1190,3341,1190,3341,1190,
	 * 1190,1190,3341,1190,1190,1190,1190,1190,1190,1190,1190,1190,1190,1190,
	 * 1190,1190,1190,1190,1190,1190,3341,1190,3341,
	 * 1190,3341,1190,3341,1190,3341,1190,65535,0,9379,18031,4400,1190]},
	 * "hostname":"IRKitD2A4","deviceid":"FBEC7F5148274DADB608799D43175FD1"}
	 *
	 *
	 * Request Parameters 
	 * Name 		Required 	Description 
	 * clientkey 	o 			see Overview
	 * clear 		x 			Clear IR receive buffer on server 
	 * 
	 * Response Parameters 
	 * Name 		Description 
	 * message 		see 赤外線信号を表すJSONについて 
	 * hostname 	IRKit device hostname. Add .local suffix to request IRKit Device HTTP API if client is in the same Wi-Fi network 
	 * deviceid 	see Overview
	 */
	class GetMessagesResponse {
        public PostSignal message;
        public String hostname;
        public String deviceid;
    }
	@ApiMethod(path="messages")
    // void getMessages(@QueryMap Map<String, String> params, Callback<GetMessagesResponse> callback);
	// to do: client app need to change to json format for sending the request
	// to do: check how the callback response works, i.e. how to callback
	public GetMessagesResponse getMessages(@Named("clientkey") String clientkey, @Named("clear") String clear, GetMessagesResponse getMessagesResponse) throws NotFoundException {

		// first, get the user instance, either from an existing user list, or
		// create new user into the user list if not existing
		PostUser postuser = null;
		int index = -1;
		for (int i = 0; i < postUserList.size(); i++) {
			if (postUserList.get(i).client_key == clientkey) {
				index = i;
				break;
			}
		}
		if (index == -1) { // if user doesn't exist in the list
			try {
				MyUser user = ObjectifyService.ofy().load().type(MyUser.class).filter("client_key", clientkey).first().now();
				postuser = new PostUser(user.id, user.name, user.passwd, user.client_key, user.api_key, user.email);
				postUserList.add(postuser);
			} catch (IndexOutOfBoundsException e) {
				throw new NotFoundException("User not found with an clientkey: " + clientkey);
			}
		} else {
			postuser = postUserList.get(index);
		}
		
		// In case of initial polling request, i.e. "clear" param = 1, shall clear the buffered signal on the server
		if (clear == "1") {
			postuser.newSignalMessage = null;
		}
			
		// then, store the newly learned Signal data on Server side data base, if there is one received
		if (postuser.newSignalMessage != null) {
			// begin data store
			Signal signalData;
			
			// need to convert the transparent_message received from southbound postmessages request to the signal format
			// transparent_message format is "{\"format\":\"raw\",\"freq\":38,\"data\":[18031,8755,1190,1190,1190]}"
			JSONObject jsonObj;
			String format = "";
			double freq = 0;
			int[] data = new int[100];
			try {
				jsonObj = new JSONObject(postuser.newSignalMessage.transparent_message);
				format = jsonObj.getString("format");
				freq = jsonObj.getDouble("freq");
				JSONArray data_array = jsonObj.getJSONArray("data");
				for (int i=0; i < data_array.length(); i++) {
					data[i] = data_array.getInt(i);
				}
			} catch (JSONException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}            		

			String signal_name = "";	// real name shall be given by end user, and then return to server and saved into server database			
			
			signalData = new Signal(null, signal_name, format, freq, data, clientkey);

			// Use Objectify to save the greeting and now() is used to make the call
			// synchronously as we
			// will immediately get a new page using redirect and we want the data
			// to be present.
			ObjectifyService.ofy().save().entity(signalData).now();
			// end data store
				
			// prepare for response
			// read signal id from data store
			// Now the id is available
			getMessagesResponse.message.id = signalData.id;	// must also pass signal id to client app, in order for client app to give a signal name and pass signal name back to server data store
			getMessagesResponse.message.format = format;
			getMessagesResponse.message.freq = freq;
			getMessagesResponse.message.data = data;
			getMessagesResponse.deviceid = String.valueOf(postuser.newSignalMessage.device_id);
			// retrieve device_hostname from device database
			// read from data store
			try {
				Device device = ObjectifyService.ofy().load().type(Device.class).id(postuser.newSignalMessage.device_id).now();
				getMessagesResponse.hostname = device.hostname;
				// success response
				return getMessagesResponse;

			} catch (IndexOutOfBoundsException e) {
				throw new NotFoundException("Device not found with an postuser.newSignalMessage.device_id: " + postuser.newSignalMessage.device_id);
			}
		}
		
		// failure response
		getMessagesResponse = null;
		return getMessagesResponse;
	}
	
	
	/**
	 * Client app initiates a command to irkit device, via cloud server
	 *
	 *
	 * POST https://irkitrestapi.appspot.com/_ah/api/northbound/v1/messages 
	 * POST /1/messages Send IR signal through IRKit device identified by deviceid.
	 *
	 * curl -i "https://api.getirkit.com/1/messages" \ -d
	 * 'clientkey=XXXXXXXXXXXXXXXXXXXXXXXXXX' \ -d
	 * 'deviceid=XXXXXXXXXXXXXXXXXXXXXXXXXXX' \ -d
	 * 'message={"format":"raw","freq":38,"data":[18031,8755,1190,1190,1190,3341
	 * ,1190,3341,1190,3341,1190,1190,1190,3341,1190,3341,1190,3341,1190,3341,
	 * 1190,3341,1190,3341,1190,1190,1190,1190,1190,1190,1190,1190,1190,3341,
	 * 1190,3341,1190,1190,1190,3341,1190,1190,1190,1190,1190,1190,1190,1190,
	 * 1190,1190,1190,1190,1190,1190,1190,1190,1190,3341,1190,3341,1190,3341,
	 * 1190,3341,1190,3341,1190,65535,0,9379,18031,4400,1190]}' HTTP/1.1 200 OK
	 * Server: ngx_openresty Date: Tue, 07 Jan 2014 08:52:24 GMT Content-Type:
	 * text/html; charset=utf-8 Content-Length: 0 Connection: keep-alive
	 * X-Content-Type-Options: nosniff
	 *
	 * Request Parameters 
	 * Name 	Required 	Description 
	 * clientkey 	o 		see Overview
	 * deviceid 	o 		see Overview message o see IR signal JSON representation
	 * 
	 * void postMessages(@FieldMap Map<String, String> params, Callback<PostMessagesResponse> callback);
	 * 
	 * Example * 
	 * 
	 * <p>Example: 
	 * <pre><code> 
	 * // Get Internet HTTP API service
	 * 
	 * IRInternetAPIService internetAPI =
	 * IRKit.sharedInstance().getHTTPClient().getInternetAPIService();
	 *
	 * // Request parameters HashMap<String, String> params = new HashMap<>();
	 * params.put("clientkey", "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
	 * params.put("deviceid", "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
	 * params.put("message",
	 * "{\"format\":\"raw\",\"freq\":38,\"data\":[18031,8755,1190,1190,1190, ]}"
	 * );
	 *
	 * // Send POST /1/messages 
	 * internetAPI.postMessages(params, new Callback<IRInternetAPIService.PostMessagesResponse>() {
	 * {@literal @}Override 
	 * public void success(IRInternetAPIService.PostMessagesResponse postMessagesResponse,
	 * Response response) { // Success }
	 *
	 * {@literal @}Override 
	 * public void failure(RetrofitError error) { // Error} }); 
	 * </code></pre></p>
	 */
	class PostMessagesResponse {
    }
	/** Method v1
	 * Client app send the message body. Server is transparently passing message body, from client app to the device.
	 * Signal ID is unknown.
	 * This method is not good for server side data mining.
	 */
	@ApiMethod(path="messages")		
	public PostMessagesResponse postMessages(@Named("clientkey") String clientkey, @Named("deviceid") String deviceid, @Named("message") String message, PostMessagesResponse postMessagesResponse) throws NotFoundException {
		
		// 1. first authenticate the user
		try {
			MyUser user = ObjectifyService.ofy().load().type(MyUser.class).filter("client_key", clientkey).first().now();

		} catch (IndexOutOfBoundsException e) {
			throw new NotFoundException("User not found with an clientkey: " + clientkey);
		}

		// 2. store all server-sent Messages (actuation commands) on Server side data base, for data mining purpose
		// begin data store
		Message newMessage;

		// convert string id to long id
		Long ldeviceid = Long.valueOf(deviceid);

		newMessage = new Message(null, clientkey, ldeviceid, message, -1L);

		// Use Objectify to save the greeting and now() is used to make the call
		// synchronously as we
		// will immediately get a new page using redirect and we want the data
		// to be present.
		ObjectifyService.ofy().save().entity(newMessage).now();
		// end data store

		// also save it into PostMessageList Memory
		// Now the id is available
		PostMessage postmessage;
		postmessage = new PostMessage(newMessage.id, System.currentTimeMillis(), clientkey, ldeviceid, message, -1L);
		postMessageList.add(postmessage);
		
		// 3. find the device id who is long polling to the server, from
		// southbound getMessages request
		// first, get the device instance, either from an existing device list,
		// or, create new device into the device list if not existing
		// convert string id to long id
		PostDevice postdevice = null;
		int index = -1;
		for (int i = 0; i < IRkitSouthboundRestAPI.postDeviceList.size(); i++) {
			if (IRkitSouthboundRestAPI.postDeviceList.get(i).id == ldeviceid) {
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
				throw new NotFoundException("Device not found with an device_id: " + deviceid);
			}
		} else {
			postdevice = IRkitSouthboundRestAPI.postDeviceList.get(index);
		}

		// 4. add the message body transparently into postdevice
		if (postdevice != null && postmessage != null) {
			postdevice.transparentMessageBuffer.add(postmessage);
			// 5. success response. Meanwhile the "IRkitSouthboundRestAPI" shall
			// send the getMessages response to the device, including the
			// post_signal
			return postMessagesResponse;
		}

		// failure response
		postMessagesResponse = null;
		return postMessagesResponse;
	}
	
	/** Method v2
	 * Client app does not send the message body, instead it calls a specific signal id from server, and then server generate and send the message to the device
	 */
	@ApiMethod(path="messages_v2")
	public PostMessagesResponse postMessagesV2(@Named("clientkey") String clientkey, @Named("deviceid") String deviceid, @Named("signalid") String signalid, PostMessagesResponse postMessagesResponse) throws NotFoundException {
		
		// 1. first authenticate the user
		try {	
			MyUser user = ObjectifyService.ofy().load().type(MyUser.class).filter("client_key", clientkey).first().now();
						
		} catch (IndexOutOfBoundsException e) {
			throw new NotFoundException("User not found with an client_key: " + clientkey);
		}	
		
		// 2. store the Message on Server data store
		// begin data store
		Message messageData;

		// convert string id to long id 
		Long ldeviceid = Long.valueOf(deviceid);
		Long lsignalid = Long.valueOf(signalid);

		// construct the JSON String "transparent_message" based on signal_id.
		String transparent_message = "";
		
		// 2.1 first find the signal data
		Signal signal;
		try {
			signal = ObjectifyService.ofy().load().type(Signal.class).id(lsignalid).now();
		} catch (IndexOutOfBoundsException e) {
			throw new NotFoundException("Signal not found with an signal_id: " + signalid);
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
		messageData = new Message(null, clientkey, ldeviceid, transparent_message, lsignalid);	

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
		postmessage = new PostMessage(messageData.id, System.currentTimeMillis(), clientkey, ldeviceid, transparent_message, lsignalid);
		postMessageList.add(postmessage);
		
		
		// 3. find the device id who is long polling to the server, from southbound getMessages request
		// first, get the device instance, either from an existing device list, or, create new device into the device list if not existing
		PostDevice postdevice = null;
		int index = -1;
		for (int i = 0; i < IRkitSouthboundRestAPI.postDeviceList.size(); i++) {
			if (IRkitSouthboundRestAPI.postDeviceList.get(i).id == ldeviceid) {
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
				throw new NotFoundException("Device not found with an device_id: " + deviceid);
			}
		} else {
			postdevice = IRkitSouthboundRestAPI.postDeviceList.get(index);
		}

		// 4. add the new signal command into postdevice
		if(postdevice != null) {
			postdevice.transparentMessageBuffer.add(postmessage);
			// 5. success response. Meanwhile the "IRkitSouthboundRestAPI" shall send the getMessages response to the device, including the post_signal
			return postMessagesResponse;
		}
		
		// failure response
		postMessagesResponse = null;
		return postMessagesResponse;
	}
	
	
	/** Signal API
	 * **
	 **/ 

	public PostSignal getSignalData(@Named("id") Integer id) throws NotFoundException {
		// read from data store
		try {
			Signal signalData = ObjectifyService.ofy().load().type(Signal.class).list().get(id);
			PostSignal postSignalData = new PostSignal(signalData.id, signalData.name, signalData.format, signalData.freq, signalData.data, signalData.client_key);
			return postSignalData;
		} catch (IndexOutOfBoundsException e) {
			throw new NotFoundException("Greeting not found with an index: " + id);
		}
	}

	@ApiMethod(path="get_all_signal")
	public ArrayList<PostSignal> listSignalData() {
		// read from data store
		ArrayList<PostSignal> signalDataList = new ArrayList<PostSignal>();
		List<Signal> list = ObjectifyService.ofy().load().type(Signal.class).list();
		for (Signal signalData : list) {
			signalDataList.add(new PostSignal(signalData.id, signalData.name, signalData.format, signalData.freq, signalData.data, signalData.client_key));
		}
		return signalDataList;
	}
	
	/** 
	 * list all signals for one user
	 * @param clientkey
	 * @return
	 */
	@ApiMethod(path="get_all_signal_for_one_user")
	public ArrayList<PostSignal> listSignalPerUser(@Named("clientkey") String clientkey) {
		// read from data store
		ArrayList<PostSignal> signalDataList = new ArrayList<PostSignal>();
		List<Signal> list = ObjectifyService.ofy().load().type(Signal.class).filter("client_key", clientkey).list();
		for (Signal signalData : list) {
			signalDataList.add(new PostSignal(signalData.id, signalData.name, signalData.format, signalData.freq, signalData.data, signalData.client_key));
		}
		return signalDataList;
	}

	/**
	 * Android Client App post a new signal, for registration of newly learned signal into cloud database
	 * POST https://irkitrestapi.appspot.com/_ah/api/northbound/v1/postsignal/2
	 * @param post_id
	 * @param postSignalData
	 * @return
	 */
	@ApiMethod(name = "signal.post", httpMethod = "post")
	public PostSignal insertSignalData(@Named("post_id") String post_id, PostSignal postSignalData) {
		// begin data store
		Signal signalData;

		signalData = new Signal(null, postSignalData.name, postSignalData.format, postSignalData.freq, postSignalData.data, postSignalData.client_key);

		// Use Objectify to save the greeting and now() is used to make the call
		// synchronously as we
		// will immediately get a new page using redirect and we want the data
		// to be present.
		ObjectifyService.ofy().save().entity(signalData).now();

		// end data store

		PostSignal response = new PostSignal();
		response.name = postSignalData.name;
		response.format = postSignalData.format;
		response.freq = postSignalData.freq;
		response.data = postSignalData.data;

		return response;
	}
	
	/**
	 * to do: client app needs to all this api after giving a new signal name
	 * 
	 * After learning a new signal, end user shall give a name for the signal on client app, 
	 * and then client app shall call server api to store the signal name on server data store
	 * 
	 * POST https://irkitrestapi.appspot.com/_ah/api/northbound/v1/postsignalname/
	 * @param signalid
	 * @param signalname
	 * @return
	 * @throws NotFoundException 
	 */
	@ApiMethod(name = "signalname.post", httpMethod = "post")
	public PostSignal insertSignalName(@Named("signalid") String signalid, @Named("signalname") String signalname) throws NotFoundException {
		
		// read from data store
		try {
			Signal signalData = ObjectifyService.ofy().load().type(Signal.class).id(signalid).now();
			signalData.name = signalname;
			ObjectifyService.ofy().save().entity(signalData).now();	// save the signal name into signal data store
			PostSignal postSignalData = new PostSignal(signalData.id, signalData.name, signalData.format, signalData.freq, signalData.data, signalData.client_key);
			return postSignalData;
		} catch (IndexOutOfBoundsException e) {
			throw new NotFoundException("Signal not found with an signalid: " + signalid);
		}
	}

	
	/** Temperature API
	 * **
	 **/


	public PostTemperature getTemperatureData(@Named("id") Integer id) throws NotFoundException {
		// read from data store
		try {
			Temperature temperatureData = ObjectifyService.ofy().load().type(Temperature.class).list().get(id);
			PostTemperature postTemperatureData = new PostTemperature(temperatureData.irkit_id, temperatureData.signal_name, temperatureData.signal_content);
			return postTemperatureData;
		} catch (IndexOutOfBoundsException e) {
			throw new NotFoundException("temperatureData not found with an index: " + id);
		}
	}

	@ApiMethod(path="get_all_temperature")
	public ArrayList<PostTemperature> listTemperatureData() {
		// read from data store
		ArrayList<PostTemperature> temperatureDataList = new ArrayList<PostTemperature>();
		List<Temperature> list = ObjectifyService.ofy().load().type(Temperature.class).list();
		for (Temperature temperatureData : list) {
			temperatureDataList.add(new PostTemperature(temperatureData.irkit_id, temperatureData.signal_name, temperatureData.signal_content));
		}
		return temperatureDataList;
	}
	
	/**
	 * GET https://irkitrestapi.appspot.com/_ah/api/northbound/v1/get_latest_temperature
	 * @return
	 * @throws NotFoundException
	 */
	@ApiMethod(path="get_latest_temperature")
	public PostTemperature getLatestTemperatureData() throws NotFoundException {
		// read from data store
		try {
			Temperature temperatureData = ObjectifyService.ofy().load().type(Temperature.class).order("-date").first().now();
			PostTemperature postTemperatureData = new PostTemperature(temperatureData.irkit_id, temperatureData.signal_name, temperatureData.signal_content);
			return postTemperatureData;
		} catch (IndexOutOfBoundsException e) {
			throw new NotFoundException("temperatureData not found with an index: latest date");
		}
	}

	/** 
	 * POST https://irkitrestapi.appspot.com/_ah/api/northbound/v1/posttemperaturedata/2
	 * @param post_id
	 * @param postTemperatureData
	 * @return
	 * "2" is post_id
	 */
	@ApiMethod(name = "temperature.post", httpMethod = "post")
	public PostTemperature insertTemperatureData(@Named("post_id") String post_id, PostTemperature postTemperatureData) {

		// begin data store
		Temperature temperatureData;

		temperatureData = new Temperature(null, postTemperatureData.irkit_id, postTemperatureData.signal_name, postTemperatureData.signal_content);

		// Use Objectify to save the greeting and now() is used to make the call
		// synchronously as we
		// will immediately get a new page using redirect and we want the data
		// to be present.
		ObjectifyService.ofy().save().entity(temperatureData).now();

		// end data store

		PostTemperature response = new PostTemperature();
		response.irkit_id = postTemperatureData.irkit_id;
		response.signal_name = postTemperatureData.signal_name;
		response.signal_content = postTemperatureData.signal_content;

		return response;
	}

	
	/** Schedule API
	 * **
	 **/

	public PostSchedule getScheduleData(@Named("id") Integer id) throws NotFoundException {
		// read from data store
		try {
			Schedule scheduleData = ObjectifyService.ofy().load().type(Schedule.class).list().get(id);
			PostSchedule postScheduleData = new PostSchedule(scheduleData.id, scheduleData.client_key, scheduleData.device_id, scheduleData.signal_id, scheduleData.repeat, scheduleData.hour_of_day, scheduleData.minute);
			return postScheduleData;
		} catch (IndexOutOfBoundsException e) {
			throw new NotFoundException("scheduleData not found with an index: " + id);
		}
	}

	@ApiMethod(path="get_all_schedule")
	public ArrayList<PostSchedule> listScheduleData() {
		// read from data store
		ArrayList<PostSchedule> scheduleDataList = new ArrayList<PostSchedule>();
		List<Schedule> list = ObjectifyService.ofy().load().type(Schedule.class).list();
		for (Schedule scheduleData : list) {
			scheduleDataList.add(new PostSchedule(scheduleData.id, scheduleData.client_key, scheduleData.device_id, scheduleData.signal_id, scheduleData.repeat, scheduleData.hour_of_day, scheduleData.minute));
		}
		return scheduleDataList;
	}
	
	@ApiMethod(name = "schedule.post", httpMethod = "post")
	public PostSchedule insertScheduleData(@Named("post_id") String post_id, PostSchedule postScheduleData) {

		// begin data store
		Schedule scheduleData;

		scheduleData = new Schedule(null, postScheduleData.client_key, postScheduleData.device_id, postScheduleData.signal_id, postScheduleData.repeat, postScheduleData.hour_of_day, postScheduleData.minute);

		// Use Objectify to save the greeting and now() is used to make the call
		// synchronously as we
		// will immediately get a new page using redirect and we want the data
		// to be present.
		ObjectifyService.ofy().save().entity(scheduleData).now();

		// end data store

		PostSchedule response = new PostSchedule();
		response.client_key = postScheduleData.client_key;
		response.device_id = postScheduleData.device_id;
		response.signal_id = postScheduleData.signal_id;
		response.repeat = postScheduleData.repeat;
		response.hour_of_day = postScheduleData.hour_of_day;
		response.minute = postScheduleData.minute;

		return response;
	}

	
	/** User API
	 * **
	 **/

	public PostUser getUserData(@Named("id") Integer id) throws NotFoundException {
		// read from data store
		try {
			MyUser userData = ObjectifyService.ofy().load().type(MyUser.class).list().get(id);
			PostUser postUserData = new PostUser(userData.id, userData.name, userData.passwd, userData.client_key, userData.api_key, userData.email);
			return postUserData;
		} catch (IndexOutOfBoundsException e) {
			throw new NotFoundException("User not found with an index: " + id);
		}
	}

	@ApiMethod(path="get_all_user")
	public ArrayList<PostUser> listUserData() {
		// read from data store
		ArrayList<PostUser> userDataList = new ArrayList<PostUser>();
		List<MyUser> list = ObjectifyService.ofy().load().type(MyUser.class).list();
		for (MyUser userData : list) {
			userDataList.add(new PostUser(userData.id, userData.name, userData.passwd, userData.client_key, userData.api_key, userData.email));
		}
		return userDataList;
	}

	@ApiMethod(name = "user.post", httpMethod = "post")
	public PostUser insertUserData(@Named("post_id") String post_id, PostUser postUserData) {

		// begin data store
		MyUser userData;

		userData = new MyUser(null, postUserData.name, postUserData.passwd, postUserData.client_key, postUserData.api_key, postUserData.email);

		// Use Objectify to save the greeting and now() is used to make the call
		// synchronously as we
		// will immediately get a new page using redirect and we want the data
		// to be present.
		ObjectifyService.ofy().save().entity(userData).now();
		// end data store

		PostUser response = new PostUser();
		response.name = postUserData.name;
		response.passwd = postUserData.passwd;
		response.client_key = postUserData.client_key;
		response.api_key = postUserData.api_key;
		response.email = postUserData.email;

		return response;
	}

	
	/** Device API
	 * 
	 **/

	/**
	 * Add a new device to the cloud server data store
	 * 
	 * POST https://irkitrestapi.appspot.com/_ah/api/northbound/v1/devices
	 * POST /1/devices Get devicekey and deviceid.
	 * 
	 * curl -i "https://api.getirkit.com/1/devices" -d
	 * "clientkey=XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX" HTTP/1.1 200 OK Server:
	 * ngx_openresty Date: Tue, 07 Jan 2014 09:30:46 GMT Content-Type:
	 * application/json; charset=utf-8 Content-Length: 94 Connection: keep-alive
	 * X-Content-Type-Options: nosniff
	 * 
	 * {"devicekey":"XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX","deviceid":
	 * "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"}
	 * 
	 * Pass devicekey to IRKit device using Morse code or IRKit itselves Wi-Fi
	 * access point.
	 * 
	 * Request Parameters 
	 * Name 		Required 	Description 
	 * clientkey 	o 			see Overview
	 * 
	 * Response Parameters 
	 * Name 		Description 
	 * devicekey 	see Overview 
	 * deviceid 	see Overview
	 * 
	 * @POST("/1/devices")
	 * void postDevices(@FieldMap Map<String, String> params, Callback<PostDevicesResponse> callback);
	 */
	class PostDevicesResponse {
        public String devicekey;
        public String deviceid;
    }
	@ApiMethod(path="devices")
	public PostDevicesResponse postDevices(@Named("clientkey") String clientkey, PostDevicesResponse postDevicesResponse) throws NotFoundException {
		
		// devicekey is pre-generated by server, and then pushed to device, so that device will have a devicekey to communicate to server
		String devicekey = ""; 
		
		// first authenticate the current user, and create the new device
		try {	
			MyUser user = ObjectifyService.ofy().load().type(MyUser.class).filter("client_key", clientkey).first().now();
			// save the new device into device data store
			String hostname = ""; // hostname is not known by now
			
			Key<Device> newkey = ObjectifyService.factory().allocateId(Device.class); // generate new unique key here, use data store key generator to avoid collision
			devicekey = String.valueOf(newkey.getId());

			Device device = new Device(null, hostname, devicekey, clientkey);
			ObjectifyService.ofy().save().entity(device).now();
						
		} catch (IndexOutOfBoundsException e) {
			throw new NotFoundException("User not found with an clientkey: " + clientkey);
		}	
		
		// then, return the devicekey and deviceid to client app
		postDevicesResponse.devicekey = devicekey;			
		try {
			Long ldeviceid = ObjectifyService.ofy().load().type(Device.class).filter("device_key", devicekey).first().now().id;
			postDevicesResponse.deviceid = String.valueOf(ldeviceid);
			// this method only has success response
			return postDevicesResponse;
		}
		catch (IndexOutOfBoundsException e) {
			throw new NotFoundException("Device not found with an devicekey: " + devicekey);
		}	
	}
	
	
	/**
	 * Key API
	 * 
	 * Bootstrapping phase. Initial key generation and key loading procedure
	 */
	
	/**
	 * Overview 
	 * 
	 * When using IRKit's Internet server, you'll need following 2
	 * request parameters. These are used to know who sent this request, and
	 * which IRKit device it's targetted to.
	 * 
	 * Name 		Description 
	 * clientkey 	key to authenticate itself 
	 * deviceid 	IRKit device identifier. 
	 * 
	 * To get clientkey and deviceid, the're 2 ways.
	 * 
	 * 1. (Which is not used by now, it's not the initial bootstrapping procedure, it's just a more aggressive method to get clientkey and deviceid):
	 * If you already have a Wi-Fi connected IRKit device, use POST /keys
	 * against IRKit device to get a clienttoken, and pass it to our server on
	 * Internet using POST /1/keys. If you already have IRKit, and you want to
	 * use it more aggressively, use this course.
	 * 
	 * 2. (Which is the "setup.png" sequence diagram describes, this is the initial bootstrapping procedure):
	 * If you have an IRKit device not connected to Wi-Fi and not able to use
	 * our official apps (iOS/Android) or SDKs(iOS/Android), for example if you
	 * want to create a SDK for WindowsPhone, use following course.
	 * 
	 * POST /1/clients to get a clientkey, and POST /1/devices to get a
	 * devicekey, deviceid and finally pass devicekey along with your home Wi-Fi
	 * access point's information to IRKit, via it's own Wi-Fi access point.
	 * (See POST /wifi)
	 * 
	 * devicekey is used by IRKit device to authenticate itself.
	 */
	
	/** 
	 * OAuth the user, generate new user - clientkey and apikey, in case of new user login
	 * POST https://irkitrestapi.appspot.com/_ah/api/northbound/v1/user/authed
	 * @param user
	 * @return
	 * @throws NotFoundException
	 * user.getEmail() will return "pipoop@gmail.com", here using openID and oAuth for authentication of users
	 */
	@ApiMethod(name = "user.authed", path = "user/authed")
	public PostUser authedUser(User user) {
		
		// When you declare a parameter of type User in your API method as shown
		// in the snippet above, the API backend framework automatically
		// authenticates the user and enforces the authorized clientIds
		// whitelist, ultimately by supplying the valid User or not. If the
		// request coming in from the client has a valid auth token or is in the
		// list of authorized clientIDs, the backend framework supplies a valid
		// User to the parameter. If the incoming request does not have a valid
		// auth token or if the client is not on the clientIDs whitelist, the
		// framework sets User to null. Your own code must handle both the case
		// where User is null and the case where there is a valid User. If there
		// is no User, for example, you could choose to return a
		// not-authenticated error or perform some other desired action.
		if (user == null) {
			return null;
		}
		
		MyUser userData;
		
		// load the user data
		userData = ObjectifyService.ofy().load().type(MyUser.class).filter("email", user.getEmail()).first().now();
			
		if (userData == null){
			// if the email has NOT been registered into cloud data store, register new user in the cloud server
			
			// begin data store
			String passwd = "";
			Key<MyUser> newkey1 = ObjectifyService.factory().allocateId(MyUser.class); // generate new unique key here, use data store key generator to avoid collision
			String client_key = String.valueOf(newkey1.getId());
			Key<MyUser> newkey2 = ObjectifyService.factory().allocateId(MyUser.class); // generate new unique key here, use data store key generator to avoid collision
			String api_key = String.valueOf(newkey2.getId());
			userData = new MyUser(null, user.getNickname(), passwd, client_key, api_key, user.getEmail());

			// Use Objectify to save the greeting and now() is used to make the call
			// synchronously as we
			// will immediately get a new page using redirect and we want the data to be present.
			ObjectifyService.ofy().save().entity(userData).now();
			// end data store
		}					
		
		// this method only has success response
		PostUser postUserData = new PostUser(userData.id, userData.name, userData.passwd, userData.client_key, userData.api_key, userData.email);
		return postUserData;
	}
	
	/**
	 * Fetch api_key by email
	 *  
	 * POST https://irkitrestapi.appspot.com/_ah/api/northbound/v1/apps
	 * POST /1/apps Get apikey.
	 * 
	 * curl -i -d "email={ your valid email }" "https://api.getirkit.com/1/apps"
	 * HTTP/1.1 200 OK Server: ngx_openresty Date: Wed, 01 Jan 2014 17:04:59 GMT
	 * Content-Type: application/json; charset=utf-8 Content-Length: 92
	 * Connection: keep-alive X-Content-Type-Options: nosniff
	 * 
	 * {"message":
	 * "You will receive an email shortly, please click the URL in it to get an apikey"
	 * }
	 * 
	 * Request Parameters 
	 * Name 	Required 	Description 
	 * email 	o 			Developer email address
	 * 
	 * Response Parameters 
	 * Name 	Description 
	 * message 	-
	 * 
	 * 
	 * @POST("/1/apps") 
	 * void postApps(@FieldMap Map<String, String> params, Callback<PostAppsResponse> callback);
	 */
	class PostAppsResponse {
        public String message;
    }
	@ApiMethod(path="apps")
	public PostAppsResponse postApps(@Named("email") String email, PostAppsResponse postAppsResponse) throws NotFoundException {

		// find out the current user from data store
		try {
			MyUser user = ObjectifyService.ofy().load().type(MyUser.class).filter("email", email).first().now();
			// response
			// to do: need send email here
			postAppsResponse.message = user.api_key;
			// this method only has success response
			return postAppsResponse;
		} catch (IndexOutOfBoundsException e) {
			throw new NotFoundException("User not found with an email: " + email);
		}		
	}
	
	/**
	 * Fetch clientkey by apikey 
	 * 
	 * POST https://irkitrestapi.appspot.com/_ah/api/northbound/v1/clients
	 * POST /1/clients Get a clientkey.
	 * 
	 * curl -i -d "apikey=XXXXXXXXXXXXXXXXXXXXX"
	 * "https://api.getirkit.com/1/clients" HTTP/1.1 200 OK Server:
	 * ngx_openresty Date: Tue, 07 Jan 2014 09:26:52 GMT Content-Type:
	 * application/json; charset=utf-8 Content-Length: 48 Connection: keep-alive
	 * X-Content-Type-Options: nosniff
	 * 
	 * {"clientkey":"XXXXXXXXXXXXXXXXXXXXXXXXXXXXX"}
	 * 
	 * Request Parameters 
	 * Name 	Required 	Description 
	 * apikey 	o 			see POST /1/apps
	 * 
	 * Response Parameters 
	 * Name 	Description 
	 * clientkey see Overview
	 * 
	 * @POST("/1/clients") 
	 * void postClients(@FieldMap Map<String, String> params, Callback<PostClientsResponse> callback);
	 * 
	 */
	class PostClientsResponse {
        public String clientkey;
    }
	@ApiMethod(path="clients")
	public PostClientsResponse postClients(@Named("apikey") String apikey, PostClientsResponse postClientsResponse) throws NotFoundException {
		
		// find out the current user from data store
		try {
			MyUser user = ObjectifyService.ofy().load().type(MyUser.class).filter("api_key", apikey).first().now();
			postClientsResponse.clientkey = user.client_key;
			// this method only has success response
			return postClientsResponse;
		} catch (IndexOutOfBoundsException e) {
			throw new NotFoundException("User not found with an apikey: " + apikey);
		}		
	}
	
	
	/**
	 * This method is part of the "Overview" Option1 above, i.e. not used by now
	 * 
	 * Fetch deviceid by devicekey
	 * devicekey = clienttoken here
	 * 
	 * There is another function of this method: to change the existing owner (user) of a device!
	 * If clientkey and device.client_key is different, new clientkey will be applied and saved into data store => i.e. device user is changed!
	 * 
	 * POST https://irkitrestapi.appspot.com/_ah/api/northbound/v1/keys
	 * POST /1/keys
	 * 
	 * Get deviceid. If optionally provide clientkey request parameter, provided
	 * clientkey and deviceid's relationship will be saved on server.
	 * 
	 * With deviceid, you can send IR signals using POST /1/messages and receive
	 * IR signals using GET /1/messages.
	 * 
	 * curl -i -d "clienttoken=XXXXXXXXXXXXXXXXXXXXX"
	 * "https://api.getirkit.com/1/keys" HTTP/1.1 200 OK Server: ngx_openresty
	 * Date: Tue, 07 Jan 2014 08:46:06 GMT Content-Type: application/json;
	 * charset=utf-8 Content-Length: 94 Connection: keep-alive
	 * X-Content-Type-Options: nosniff
	 * 
	 * {"deviceid":"XXXXXXXXXXXXXXXXXXXXXXXXXXXXXX","clientkey":
	 * "XXXXXXXXXXXXXXXXXXXXXXX"}
	 * 
	 * Request Parameters 
	 * Name 		Required 	Description 
	 * clienttoken 	o 			Get one from POST /keys 
	 * clientkey 	x 			see Overview. If optionally provided, Internet server will remember relationship between clientkey and IRKit device.
	 * 
	 * Response JSON keys 
	 * Name 		Description 
	 * deviceid 	see Overview 
	 * clientkey 	see Overview
	 * 
	 * @POST("/1/keys")
	 * void postKeys(@FieldMap Map<String, String> params, Callback<PostKeysResponse> callback);
	 */
	class PostKeysResponse {
        public String deviceid;
        public String clientkey;
    }
	@ApiMethod(path="keys")
	public PostKeysResponse postKeys(@Named("clienttoken") String clienttoken, @Named("client_key") String client_key, PostKeysResponse postKeysResponse) throws NotFoundException {

		// clienttoken = devicekey here, see Southbound API "postKeys" implementation.
		
		// first authenticate the user, in case "clientkey" is provided in the request
		if (!client_key.isEmpty()) {
			try {
				MyUser user = ObjectifyService.ofy().load().type(MyUser.class).filter("client_key", client_key).first().now();

			} catch (IndexOutOfBoundsException e) {
				throw new NotFoundException("User not found with an clientkey: " + client_key);
			}
		}
				
		// find out the current device from data store
		try {
			Device device = ObjectifyService.ofy().load().type(Device.class).filter("device_key", clienttoken).first().now();
			postKeysResponse.deviceid = String.valueOf(device.id);
			postKeysResponse.clientkey = device.client_key;		
			// here, if clientkey and device.client_key is different, new clientkey will be applied and saved into data store => i.e. device user is changed!
			if (!client_key.isEmpty() && client_key != device.client_key) {
				postKeysResponse.clientkey = client_key;
				device.client_key = client_key;
				ObjectifyService.ofy().save().entity(device).now(); // save the new device-user relationship into data store!
			}
			// this method only has success response
			return postKeysResponse;
		} catch (IndexOutOfBoundsException e) {
			throw new NotFoundException("Device not found with a clienttoken: " + clienttoken);
		}

	}
	
}



	
	

		
	



