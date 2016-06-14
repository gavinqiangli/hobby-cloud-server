package com.example.helloendpoints.api;

import com.example.helloendpoints.Constants;
import com.example.helloendpoints.entity.Device;
import com.example.helloendpoints.entity.Irkit;
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
import java.util.UUID;
import java.util.logging.Logger;

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

	private static final Logger log = Logger.getLogger(IRkitNorthBoundRestAPI.class.getName());

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
	 * 
	 * Testing Passed OK
	 * POST https://irkitrestapi.appspot.com/_ah/api/northbound/v1/door?clientkey=8010001&deviceid=5649391675244544
	 * Returned 200OK Response
	 * { "hostname": "IRKitXXXX",}
	 */
	class PostDoorResponse {
        /*
         * Bonjour を使うことで同じWiFiアクセスポイントに接続したクライアントから #{hostname}.local として接続するために使います。
         */
        public String hostname;
    }
	@ApiMethod(path="door")
	public PostDoorResponse postDoor(@Named("clientkey") String clientkey, @Named("deviceid") String deviceid) throws NotFoundException {
		
		PostDoorResponse postDoorResponse = new PostDoorResponse();

		// shall we authenticate client_key here? shall we authenticate
		// client_key for every API call?
		// 1. first authenticate the user
		MyUser user = ObjectifyService.ofy().load().type(MyUser.class).filter("client_key", clientkey).first().now();
		if (user == null) {
			log.info("User not found with an clientkey: " + clientkey);
			return postDoorResponse; // to do: what error code shall be
										// returned?
		}
		
		// convert string id to long id 
		Long ldeviceid = Long.valueOf(deviceid);
		
		// find out the device instance from data store
		// if device hasn't done bonjour and hasn't done the southbound API "postDoor" to register its hostname, the hostname field in data store will be empty
		// if device has done bonjour and has done the southbound API "postDoor" to register its hostname, the hostname field in data store shall be real hostname
		try {
			Device device = ObjectifyService.ofy().load().type(Device.class).id(ldeviceid).now();	// note: id() or filter() won't work in case of there is ancestor key
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
	 * waitForSignal -> getMessage -> postSignal is bound/registered per User, i.e.one user can register signal for any of his/her devices
	 * alternative is to register the signal per device
	 * right now it is registered/bound per User
	 */
	@ApiMethod(path="messages_v2")
	public PostSignal getMessagesV2(@Named("clientkey") String clientkey) {
		
		PostSignal postsignal = new PostSignal();

		// first, get the user instance, either from an existing user list, or
		// create new user into the user list if not existing
		PostUser postuser;
		int index = -1;
		for (int i = 0; i < postUserList.size(); i++) {
			if (postUserList.get(i).client_key.equals(clientkey)) {
				index = i;
				break;
			}
		}
		if (index == -1) { // if user doesn't exist in the list
			MyUser user = ObjectifyService.ofy().load().type(MyUser.class).filter("client_key", clientkey).first()
					.now();
			if (user != null) {
				postuser = new PostUser(user.id, user.name, user.passwd, user.client_key, user.api_key, user.email);
				postUserList.add(postuser);
			} else {
				log.info("User not found with an clientkey: " + clientkey);
				return postsignal; // to do: what error code shall be returned? 
			}
		} else {
			postuser = postUserList.get(index);
		}
				
		// for the first time getMessages, newSignal shall be always initiated "null"
		if (postuser.isInitialGetMessages == true) {
			postuser.newSignalMessage = null;
		}
		
		// for the second and onwards getMessages, don't touch newSignal
		postuser.isInitialGetMessages = false;
		
		// if newSignal is captured not "null", getMessages is returned not "null", wait/capture new signal data is done, 
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

			Signal signal = new Signal(signal_name, format, freq, data, clientkey);

			// Use Objectify to save the greeting and now() is used to make the
			// call
			// synchronously as we
			// will immediately get a new page using redirect and we want the
			// data
			// to be present.
			ObjectifyService.ofy().save().entity(signal).now();
			// end data store
			
			// Now the id is available
			postsignal.id = signal.id;	// must also pass signal id to client app, in order for client app to give a signal name and pass signal name back to server data store
			postsignal.format = format;
			postsignal.freq = freq;
			postsignal.data = data;

			// success response
			return postsignal;
		}
		
		// failure response
		return postsignal; // to do: doesn't make sense to return null, but what error code shall be returned?
	}
	
	/**
	 * Client App is doing long polling "get" here, to receive/capture a newly learned signal data
	 *
	 * Original design
	 * According to Android client request, the server "getMessages" call shall do a callback to the Android client, 
	 * when server receives a "newSignal" from the Irkit device
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
	 
	 * Testing Passed OK
	 * GET https://irkitrestapi.appspot.com/_ah/api/northbound/v1/messages?clear=1&clientkey=8010001
	 * Returned 200OK Response
	 * { "message": {"id": "5096363633147904", "format": "raw", "freq": 38, "data": [18031,8755,1190,1190,1190]}, "hostname": "IRKitXXXX", "deviceid": "5649391675244544",}
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
	public GetMessagesResponse getMessages(@Named("clientkey") String clientkey, @Named("clear") String clear) throws NotFoundException {
		
		GetMessagesResponse getMessagesResponse = new GetMessagesResponse();

		// first, get the user instance, either from an existing user list, or
		// create new user into the user list if not existing
		PostUser postuser;
		int index = -1;
		for (int i = 0; i < postUserList.size(); i++) {
			if (postUserList.get(i).client_key.equals(clientkey)) {
				index = i;
				break;
			}
		}
		if (index == -1) { // if user doesn't exist in the list
			MyUser user = ObjectifyService.ofy().load().type(MyUser.class).filter("client_key", clientkey).first()
					.now();
			if (user != null) {
				postuser = new PostUser(user.id, user.name, user.passwd, user.client_key, user.api_key, user.email);
				postUserList.add(postuser);
			} else {
				log.info("User not found with an clientkey: " + clientkey);
				return getMessagesResponse; // to do: what error code shall be
											// returned?
			}
		} else {
			postuser = postUserList.get(index);
		}

		log.info("postUserList.size() = " + String.valueOf(postUserList.size()));

		
		// In case of initial polling request, i.e. "clear" param = 1, shall clear the buffered signal on the server
		if (clear.equals("1")) {
			postuser.newSignalMessage = null;
		}
			
		// then, store the newly learned Signal data on Server side data base, if there is one received
		if (postuser.newSignalMessage != null) {
			
			log.info("postuser.newSignalMessage.transparent_message = " + postuser.newSignalMessage.transparent_message);
			// Note: JSON cannot parse {\"format\":\"raw\",\"freq\":38,\"data\":[18031,8755,1190,1190,1190]}
			// JSON can only parse it without \
			postuser.newSignalMessage.transparent_message = postuser.newSignalMessage.transparent_message.replace("\\", "");
			log.info("postuser.newSignalMessage.transparent_message = " + postuser.newSignalMessage.transparent_message);
			
			// begin data store
			Signal signal;
			
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
				data = new int[data_array.length()];
				for (int i=0; i < data_array.length(); i++) {
					data[i] = data_array.getInt(i);
				}
			} catch (JSONException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}            		

			String signal_name = "";	// real name shall be given by end user, and then return to server and saved into server database			
			
			signal = new Signal(signal_name, format, freq, data, clientkey);

			// Use Objectify to save the greeting and now() is used to make the call
			// synchronously as we
			// will immediately get a new page using redirect and we want the data
			// to be present.
			ObjectifyService.ofy().save().entity(signal).now();
			// end data store
				
			// prepare for response
			// read signal id from data store
			// Now the id is available
			getMessagesResponse.message = new PostSignal();
			getMessagesResponse.message.id = signal.id;	// must also pass signal id to client app, in order for client app to give a signal name and pass signal name back to server data store
			getMessagesResponse.message.format = format;
			getMessagesResponse.message.freq = freq;
			getMessagesResponse.message.data = data;
			getMessagesResponse.deviceid = String.valueOf(postuser.newSignalMessage.device_id);
			// retrieve device_hostname from device database
			// read from data store
			try {
				Device device = ObjectifyService.ofy().load().type(Device.class).id(postuser.newSignalMessage.device_id).now();	// note: id() or filter() won't work in case of there is ancestor key
				getMessagesResponse.hostname = device.hostname;
				// success response
				return getMessagesResponse;

			} catch (IndexOutOfBoundsException e) {
				throw new NotFoundException("Device not found with an postuser.newSignalMessage.device_id: " + postuser.newSignalMessage.device_id);
			}
		}
		
		// failure response ???
		return getMessagesResponse; // to do: what error code shall be returned?
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
	 * "{\"format\":\"raw\",\"freq\":38,\"data\":[18031,8755,1190,1190,1190]}"
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
	 * 
	 * Testing Passed OK
	 * Input Message format should be without \, so: message={"format":"raw","freq":38,"data":[18031,8755,1190,1190,1190]}
	 */
	@ApiMethod(path="messages")		
	public void postMessages(@Named("clientkey") String clientkey, @Named("deviceid") String deviceid, @Named("message") String message) throws NotFoundException {
		
		// 1. first authenticate the user
		MyUser user = ObjectifyService.ofy().load().type(MyUser.class).filter("client_key", clientkey).first().now();
		if (user == null) {
			log.info("User not found with an clientkey: " + clientkey);
			return; // to do: what error code shall be returned?
		}

		// 2. store all server-sent Messages (actuation commands) on Server side data base, for data mining purpose
		// begin data store
		Message newMessage;

		// convert string id to long id
		Long ldeviceid = Long.valueOf(deviceid);

		newMessage = new Message(clientkey, ldeviceid, message, -1L);

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
		PostDevice postdevice;
		log.info("IRkitSouthboundRestAPI.postDeviceList.size() = " + String.valueOf(IRkitSouthboundRestAPI.postDeviceList.size()));

		int index = -1;
		for (int i = 0; i < IRkitSouthboundRestAPI.postDeviceList.size(); i++) {
			if (IRkitSouthboundRestAPI.postDeviceList.get(i).id.equals(ldeviceid)) {
				index = i;
				break;
			}
		}
		if (index == -1) { // if device doesn't exist in the list
			try {
				Device device = ObjectifyService.ofy().load().type(Device.class).id(ldeviceid).now();	// note: id() or filter() won't work in case of there is ancestor key
				postdevice = new PostDevice(device.id, device.hostname, device.device_key, device.client_key);
				IRkitSouthboundRestAPI.postDeviceList.add(postdevice);
			} catch (IndexOutOfBoundsException e) {
				throw new NotFoundException("Device not found with an device_id: " + deviceid);
			}
		} else {
			postdevice = IRkitSouthboundRestAPI.postDeviceList.get(index);
		}

		// 4. add the message body transparently into postdevice
		postdevice.transparentMessageBuffer.add(postmessage);
		log.info("postdevice.transparentMessageBuffer.size() = " + String.valueOf(postdevice.transparentMessageBuffer.size()));
		log.info("postmessage.seq_id = " + String.valueOf(postmessage.seq_id));

		// 5. success response. Meanwhile the "IRkitSouthboundRestAPI" shall
		// send the getMessages response to the device, including the
		// post_signal
		return;
		// failure response ????? to do: check android client implementation how to handle failure
	}
	
	/** Method v2
	 * Client app does not send the message body, instead it calls a specific signal id from server, and then server generate and send the message to the device
	 * 
	 * Testing Passed OK
	 * Returned 200OK Response
	 */
	@ApiMethod(path="messages_v2")
	public void postMessagesV2(@Named("clientkey") String clientkey, @Named("deviceid") String deviceid, @Named("signalid") String signalid) throws NotFoundException {
		
		// 1. first authenticate the user
		MyUser user = ObjectifyService.ofy().load().type(MyUser.class).filter("client_key", clientkey).first().now();
		if (user == null) {
			log.info("User not found with an client_key: " + clientkey);
			return; // to do: what error code shall be returned?
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
			signal = ObjectifyService.ofy().load().type(Signal.class).id(lsignalid).now();	// note: id() or filter() won't work in case of there is ancestor key
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
		messageData = new Message(clientkey, ldeviceid, transparent_message, lsignalid);	

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
				Device device = ObjectifyService.ofy().load().type(Device.class).id(ldeviceid).now();	// note: id() or filter() won't work in case of there is ancestor key
				postdevice = new PostDevice(device.id, device.hostname, device.device_key, device.client_key);
				IRkitSouthboundRestAPI.postDeviceList.add(postdevice);
			} catch (IndexOutOfBoundsException e) {
				throw new NotFoundException("Device not found with an device_id: " + deviceid);
			}
		} else {
			postdevice = IRkitSouthboundRestAPI.postDeviceList.get(index);
		}

		// 4. add the new signal command into postdevice
		postdevice.transparentMessageBuffer.add(postmessage);
		log.info("postdevice.transparentMessageBuffer.size() = " + String.valueOf(postdevice.transparentMessageBuffer.size()));
		log.info("postmessage.seq_id = " + String.valueOf(postmessage.seq_id));
		// 5. success response. Meanwhile the "IRkitSouthboundRestAPI" shall send the getMessages response to the device, including the post_signal
		return;
				
		// failure response ????? to do: check android client implementation how to handle failure
	}
	
	
	/** Signal API
	 * **
	 **/ 

	@ApiMethod(name = "signal.get", path="signal")
	public PostSignal getSignal(@Named("id") Integer id) throws NotFoundException {
		// read from data store
		try {
			Signal signal = ObjectifyService.ofy().load().type(Signal.class).list().get(id);
			PostSignal postSignal = new PostSignal(signal.id, signal.name, signal.format, signal.freq, signal.data, signal.client_key);
			return postSignal;
		} catch (IndexOutOfBoundsException e) {
			throw new NotFoundException("Greeting not found with an index: " + id);
		}
	}

	/**
	 * 
	 * @return
	 * 
	 * Testing Passed OK
	 */
	@ApiMethod(name = "signal.get.all", path="signal/all")
	public ArrayList<PostSignal> listSignal() {
		// read from data store
		ArrayList<PostSignal> signalList = new ArrayList<PostSignal>();
		List<Signal> list = ObjectifyService.ofy().load().type(Signal.class).list();
		for (Signal signal : list) {
			signalList.add(new PostSignal(signal.id, signal.name, signal.format, signal.freq, signal.data, signal.client_key));
		}
		return signalList;
	}
	
	/** 
	 * list all signals for one user
	 * @param clientkey
	 * @return
	 * 
	 * Testing Passed OK
	 * Returned 200OK response
	 */
	@ApiMethod(name = "signal.get.per_user", path="signal/per_user")
	public ArrayList<PostSignal> listSignalPerUser(@Named("clientkey") String clientkey) {
		// read from data store
		ArrayList<PostSignal> signalList = new ArrayList<PostSignal>();
		List<Signal> list = ObjectifyService.ofy().load().type(Signal.class).filter("client_key", clientkey).list();
		for (Signal signal : list) {
			signalList.add(new PostSignal(signal.id, signal.name, signal.format, signal.freq, signal.data, signal.client_key));
		}
		return signalList;
	}

	/**
	 * Android Client App post a new signal, for registration of newly learned signal into cloud database
	 * POST https://irkitrestapi.appspot.com/_ah/api/northbound/v1/signal
	 * @param postSignal
	 * @return
	 */
	@ApiMethod(name = "signal.post", path = "signal", httpMethod = "post")
	public PostSignal insertSignal(PostSignal postSignal) {
		// begin data store
		Signal signal;

		signal = new Signal(postSignal.name, postSignal.format, postSignal.freq, postSignal.data, postSignal.client_key);

		// Use Objectify to save the greeting and now() is used to make the call
		// synchronously as we
		// will immediately get a new page using redirect and we want the data
		// to be present.
		ObjectifyService.ofy().save().entity(signal).now();

		// end data store

		PostSignal response = new PostSignal();
		response.name = postSignal.name;
		response.format = postSignal.format;
		response.freq = postSignal.freq;
		response.data = postSignal.data;

		return response;
	}
	
	/**
	 * to do: client app needs to all this api after giving a new signal name
	 * 
	 * After learning a new signal, end user shall give a name for the signal on client app, 
	 * and then client app shall call server api to store the signal name on server data store
	 * 
	 * POST https://irkitrestapi.appspot.com/_ah/api/northbound/v1/signal/name
	 * @param signalid
	 * @param signalname
	 * @return
	 * @throws NotFoundException 
	 * 
	 * Testing Passed OK
	 * Returned 200OK Response
	 */
	@ApiMethod(name = "signal.name.post", path = "signal/name", httpMethod = "post")
	public PostSignal insertSignalName(@Named("signalid") String signalid, @Named("signalname") String signalname) throws NotFoundException {
		
		// read from data store
		try {
			Long lsignalid = Long.valueOf(signalid);
			Signal signal = ObjectifyService.ofy().load().type(Signal.class).id(lsignalid).now();	// note: id() or filter() won't work in case of there is ancestor key
			signal.name = signalname;
			ObjectifyService.ofy().save().entity(signal).now();	// save the signal name into signal data store
			PostSignal postSignal = new PostSignal(signal.id, signal.name, signal.format, signal.freq, signal.data, signal.client_key);
			return postSignal;
		} catch (IndexOutOfBoundsException e) {
			throw new NotFoundException("Signal not found with an signalid: " + signalid);
		}
	}

	
	/** Temperature API
	 * **
	 **/

	@ApiMethod(name = "temperature.get", path="temperature")
	public PostTemperature getTemperature(@Named("id") Integer id) throws NotFoundException {
		// read from data store
		try {
			Temperature temperature = ObjectifyService.ofy().load().type(Temperature.class).list().get(id);
			PostTemperature postTemperature = new PostTemperature(temperature.irkit_id, temperature.signal_name, temperature.signal_content);
			return postTemperature;
		} catch (IndexOutOfBoundsException e) {
			throw new NotFoundException("temperature not found with an index: " + id);
		}
	}

	@ApiMethod(name = "temperature.get.all", path="temperature/all")
	public ArrayList<PostTemperature> listTemperature() {
		// read from data store
		ArrayList<PostTemperature> temperatureList = new ArrayList<PostTemperature>();
		List<Temperature> list = ObjectifyService.ofy().load().type(Temperature.class).list();
		for (Temperature temperature : list) {
			temperatureList.add(new PostTemperature(temperature.irkit_id, temperature.signal_name, temperature.signal_content));
		}
		return temperatureList;
	}
	
	/**
	 * GET https://irkitrestapi.appspot.com/_ah/api/northbound/v1/temperature/latest
	 * @return
	 * @throws NotFoundException
	 */
	@ApiMethod(name = "temperature.get.latest", path="temperature/latest")
	public PostTemperature getLatestTemperature() {
		// read from data store
		Temperature temperature = ObjectifyService.ofy().load().type(Temperature.class).order("-date").first().now();
		if (temperature == null) {
			log.info("temperature not found with an index: latest date");
			return null; // to do: does it make sense to return null? or shall return with error code?
		}
		PostTemperature postTemperature = new PostTemperature(temperature.irkit_id, temperature.signal_name,
				temperature.signal_content);
		return postTemperature;
	}

	/** Temperature API
	 * 
	 * Report temperature every 10 mins
	 *  
	 * POST https://irkitrestapi.appspot.com/_ah/api/northbound/v1/temperature
	 * @param postTemperature
	 * @return
	 */
	@ApiMethod(name = "temperature.post", path = "temperature", httpMethod = "post")
	public PostTemperature insertTemperature(PostTemperature postTemperature) {

		// begin data store
		Temperature temperature;

		temperature = new Temperature(postTemperature.irkit_id, postTemperature.signal_name, postTemperature.signal_content);

		// Use Objectify to save the greeting and now() is used to make the call
		// synchronously as we
		// will immediately get a new page using redirect and we want the data
		// to be present.
		ObjectifyService.ofy().save().entity(temperature).now();

		// end data store

		PostTemperature response = new PostTemperature();
		response.irkit_id = postTemperature.irkit_id;
		response.signal_name = postTemperature.signal_name;
		response.signal_content = postTemperature.signal_content;

		return response;
	}

	
	/** Schedule API
	 * **
	 **/

	@ApiMethod(name = "schedule.get", path="schedule")
	public PostSchedule getSchedule(@Named("id") Integer id) throws NotFoundException {
		// read from data store
		try {
			Schedule schedule = ObjectifyService.ofy().load().type(Schedule.class).list().get(id);
			PostSchedule postSchedule = new PostSchedule(schedule.id, schedule.client_key, schedule.device_id, schedule.signal_id, schedule.repeat, schedule.hour_of_day, schedule.minute);
			return postSchedule;
		} catch (IndexOutOfBoundsException e) {
			throw new NotFoundException("schedule not found with an index: " + id);
		}
	}

	@ApiMethod(name = "schedule.get.all", path="schedule/all")
	public ArrayList<PostSchedule> listSchedule() {
		// read from data store
		ArrayList<PostSchedule> scheduleList = new ArrayList<PostSchedule>();
		List<Schedule> list = ObjectifyService.ofy().load().type(Schedule.class).list();
		for (Schedule schedule : list) {
			scheduleList.add(new PostSchedule(schedule.id, schedule.client_key, schedule.device_id, schedule.signal_id, schedule.repeat, schedule.hour_of_day, schedule.minute));
		}
		return scheduleList;
	}
	
	/**
	 * 
	 * @param postSchedule
	 * @return
	 * 
	 * Testing Passed OK
	 */
	@ApiMethod(name = "schedule.post", httpMethod = "post")
	public PostSchedule insertSchedule(PostSchedule postSchedule) {

		// begin data store
		Schedule schedule;

		schedule = new Schedule(postSchedule.client_key, postSchedule.device_id, postSchedule.signal_id, postSchedule.repeat, postSchedule.hour_of_day, postSchedule.minute);

		// Use Objectify to save the greeting and now() is used to make the call
		// synchronously as we
		// will immediately get a new page using redirect and we want the data
		// to be present.
		ObjectifyService.ofy().save().entity(schedule).now();

		// end data store

		PostSchedule response = new PostSchedule();
		response.client_key = postSchedule.client_key;
		response.device_id = postSchedule.device_id;
		response.signal_id = postSchedule.signal_id;
		response.repeat = postSchedule.repeat;
		response.hour_of_day = postSchedule.hour_of_day;
		response.minute = postSchedule.minute;

		return response;
	}

	
	/** User API
	 * **
	 **/

	@ApiMethod(name = "user.get", path="user")
	public PostUser getUser(@Named("id") Integer id) throws NotFoundException {
		// read from data store
		try {
			MyUser user = ObjectifyService.ofy().load().type(MyUser.class).list().get(id);
			PostUser postUser = new PostUser(user.id, user.name, user.passwd, user.client_key, user.api_key, user.email);
			return postUser;
		} catch (IndexOutOfBoundsException e) {
			throw new NotFoundException("User not found with an index: " + id);
		}
	}

	@ApiMethod(name = "user.get.all", path="user/all")
	public ArrayList<PostUser> listUser() {
		// read from data store
		ArrayList<PostUser> userList = new ArrayList<PostUser>();
		List<MyUser> list = ObjectifyService.ofy().load().type(MyUser.class).list();
		for (MyUser user : list) {
			userList.add(new PostUser(user.id, user.name, user.passwd, user.client_key, user.api_key, user.email));
		}
		return userList;
	}

	@ApiMethod(name = "user.post", httpMethod = "post")
	public PostUser insertUser(PostUser postUser) {

		// begin data store
		MyUser user;

		user = new MyUser(postUser.name, postUser.passwd, postUser.client_key, postUser.api_key, postUser.email);

		// Use Objectify to save the greeting and now() is used to make the call
		// synchronously as we
		// will immediately get a new page using redirect and we want the data
		// to be present.
		ObjectifyService.ofy().save().entity(user).now();
		// end data store

		PostUser response = new PostUser();
		response.name = postUser.name;
		response.passwd = postUser.passwd;
		response.client_key = postUser.client_key;
		response.api_key = postUser.api_key;
		response.email = postUser.email;

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
	 * 
	 * Tesing Passed OK
	 * POST https://irkitrestapi.appspot.com/_ah/api/northbound/v1/devices?clientkey=4000003
	 * Returned 200OK Json Response
	 * { "devicekey": "3020001", "deviceid": "5649050225344512",}
	 */
	class PostDevicesResponse {
        public String devicekey;
        public String deviceid;
    }
	@ApiMethod(path="devices")
	public PostDevicesResponse postDevices(@Named("clientkey") String clientkey) throws NotFoundException {
		
		PostDevicesResponse postDevicesResponse = new PostDevicesResponse();

		// devicekey is pre-generated by server, and then pushed to device, so that device will have a devicekey to communicate to server
		String devicekey; 
		
		// first authenticate the current user, and create the new device	
		MyUser user = ObjectifyService.ofy().load().type(MyUser.class).filter("client_key", clientkey).first().now();
		if (user == null) {
			log.info("User not found with an clientkey: " + clientkey);
			return postDevicesResponse; // to do: what error code shall be
										// returned?
		}
		// save the new device into device data store
		String hostname = ""; // hostname is not known by now

		// Key<Device> newkey =
		// ObjectifyService.factory().allocateId(Device.class); // generate new
		// unique key here, use data store key generator to avoid collision
		// devicekey = String.valueOf(newkey.getId());
		devicekey = String.valueOf(UUID.randomUUID()); // objectify id is too
														// short, use UUID
														// instead
		devicekey = devicekey.replaceAll("-", ""); // IRKit Device only supports
													// 32 chars UUID, removes
													// all "-"
		devicekey = devicekey.toUpperCase(); // Shall convert to upper case for
												// successful IRKit Device CRC
												// check

		Device device = new Device(hostname, devicekey, clientkey);
		ObjectifyService.ofy().save().entity(device).now();
		
		// then, return the devicekey and deviceid to client app
		postDevicesResponse.devicekey = devicekey;
		postDevicesResponse.deviceid = String.valueOf(device.id);
		// this method only has success response
		return postDevicesResponse;
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
	 * 
	 * Testing Passed OK
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
			log.info("user is null!");
			return null; // make sense to return null? shall return error code?
		}
		
		MyUser myuser;
		
		// load the user data
		myuser = ObjectifyService.ofy().load().type(MyUser.class).filter("email", user.getEmail()).first().now();
			
		if (myuser == null){
			// if the email has NOT been registered into cloud data store, register new user in the cloud server
			
			// begin data store
			String passwd = "";
			
			String client_key = String.valueOf(UUID.randomUUID()); // objectify id is too short, use UUID instead
			client_key = client_key.replaceAll("-", "");	// IRKit Device only supports 32 chars UUID, removes all "-"
			client_key = client_key.toUpperCase();
			
			String api_key = String.valueOf(UUID.randomUUID()); // objectify id is too short, use UUID instead
			api_key = api_key.replaceAll("-", "");	// IRKit Device only supports 32 chars UUID, removes all "-"
			api_key = api_key.toUpperCase();

			log.info("user doesn't exist, create new user with email: " + user.getEmail());
			myuser = new MyUser(user.getNickname(), passwd, client_key, api_key, user.getEmail());

			// Use Objectify to save the greeting and now() is used to make the call
			// synchronously as we
			// will immediately get a new page using redirect and we want the data to be present.
			ObjectifyService.ofy().save().entity(user).now();
			// end data store
		}					
		
		log.info("existing user found with email: " + user.getEmail());
		// this method only has success response
		PostUser postUser = new PostUser(myuser.id, myuser.name, myuser.passwd, myuser.client_key, myuser.api_key, myuser.email);
		return postUser;
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
	 * 
	 * Testing Passed OK: 
	 * POST https://irkitrestapi.appspot.com/_ah/api/northbound/v1/apps?email=pipoop%40gmail.com
	 * Returned 200OK Json response
	 * {"message": "7010001",}
	 */
	class PostAppsResponse {
        public String message;
    }
	@ApiMethod(path="apps")
	public PostAppsResponse postApps(@Named("email") String email) {

		PostAppsResponse postAppsResponse = new PostAppsResponse();

		// find out the current user from data store
		MyUser user = ObjectifyService.ofy().load().type(MyUser.class).filter("email", email).first().now();
		if (user == null) {
			log.info("User not found with an email: " + email);
			return postAppsResponse; // to do: what error code shall be
										// returned?
		}
		// response
		// to do: need send email here
		postAppsResponse.message = user.api_key;
		// this method only has success response
		return postAppsResponse;
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
	 * Testing Passed OK
	 * POST https://irkitrestapi.appspot.com/_ah/api/northbound/v1/clients?apikey=7010001
	 * Returned 200OK Json response
	 * { "clientkey": "4000003",}
	 */
	class PostClientsResponse {
        public String clientkey;
    }
	@ApiMethod(path="clients")
	public PostClientsResponse postClients(@Named("apikey") String apikey) {

		PostClientsResponse postClientsResponse = new PostClientsResponse();

		// find out the current user from data store
		MyUser user = ObjectifyService.ofy().load().type(MyUser.class).filter("api_key", apikey).first().now();
		if (user == null) {
			log.info("User not found with an apikey: " + apikey);
			return postClientsResponse; // to do: what error code shall be
										// returned?
		}
		postClientsResponse.clientkey = user.client_key;
		// this method only has success response
		return postClientsResponse;
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
	public PostKeysResponse postKeys(@Named("clienttoken") String clienttoken, @Named("client_key") String client_key) {

		PostKeysResponse postKeysResponse = new PostKeysResponse();

		// clienttoken = devicekey here, see Southbound API "postKeys" implementation.
		
		// first authenticate the user, in case "clientkey" is provided in the
		// request
		if (!client_key.isEmpty()) {
			MyUser user = ObjectifyService.ofy().load().type(MyUser.class).filter("client_key", client_key).first()
					.now();
			if (user == null) {
				log.info("User not found with an clientkey: " + client_key);
				return postKeysResponse; // to do: what error code shall be
											// returned?
			}
		}
				
		// find out the current device from data store
		Device device = ObjectifyService.ofy().load().type(Device.class).filter("device_key", clienttoken).first()
				.now();
		if (device != null) {
			postKeysResponse.deviceid = String.valueOf(device.id);
			postKeysResponse.clientkey = device.client_key;
			// here, if clientkey and device.client_key is different, new
			// clientkey will be applied and saved into data store => i.e.
			// device user is changed!
			if (!client_key.isEmpty() && client_key != device.client_key) {
				postKeysResponse.clientkey = client_key;
				device.client_key = client_key;
				ObjectifyService.ofy().save().entity(device).now(); // save the
																	// new
																	// device-user
																	// relationship
																	// into data
																	// store!
			}
			// this method only has success response
			return postKeysResponse;
		} else {
			log.info("Device not found with a clienttoken: " + clienttoken);
			return postKeysResponse; // to do: what error code shall be
										// returned?
		}
	}

}



	
	

		
	



