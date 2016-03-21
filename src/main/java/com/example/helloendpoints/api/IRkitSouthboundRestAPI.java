package com.example.helloendpoints.api;

import com.example.helloendpoints.Constants;
import com.example.helloendpoints.entity.Device;
import com.example.helloendpoints.entity.MyUser;
import com.example.helloendpoints.entity.Temperature;
import com.example.helloendpoints.postdata.PostDevice;
import com.example.helloendpoints.postdata.PostTemperature;
import com.example.helloendpoints.postdata.PostUser;
import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.response.NotFoundException;
import com.googlecode.objectify.ObjectifyService;

import java.util.ArrayList;

import javax.inject.Named;

/**
 * Defines v1 of a IRKit Southbound API, which provides southbound methods.
 */
@Api(name = "southbound", version = "v1", scopes = { Constants.EMAIL_SCOPE }, clientIds = { Constants.WEB_CLIENT_ID,
		Constants.ANDROID_CLIENT_ID, Constants.IOS_CLIENT_ID,
		Constants.API_EXPLORER_CLIENT_ID }, audiences = { Constants.ANDROID_AUDIENCE })
public class IRkitSouthboundRestAPI {

	/**
	 * Saved in the memory, should be cleared after a scheduled time period in order to release memory. 
	 * The schedule shall be selected very carefully, in order not to affect end users. e.g. during midnight when there are much fewer requests.
	 * Still, Problem is how to handle the lost cache data due to such memory release, e.g. actuation commands lost, new signal learnings lost, should we really clear the buffer?
	 * or should we never clear the buffer?
	 */
	public static ArrayList<PostDevice> postDeviceList = new ArrayList<PostDevice>();	
	

	/** 
	 * Door API
	 *  
	 * After IRKit device is connected to home WiFi, it shall do
	 * Bonjour, and then call this API, To Register IRKit device hostname to
	 * server
	 *
	 * POST https://irkitrestapi.appspot.com/_ah/api/southbound/v1/door
	 *
	 * Request
	 * 
	 * int8_t irkit_httpclient_post_door() { //
	 * devicekey=[0-9A-F]{32}&hostname=IRKit%%%% char
	 * body[POST_DOOR_BODY_LENGTH+1]; sprintf(body, "devicekey=%s&hostname=%s",
	 * keys.getKey(), gs.hostname()); return gs.post( "/d", body,
	 * POST_DOOR_BODY_LENGTH, &on_post_door_response, 50 ); }
	 * 
	 * int8_t GSwifi::post(const char *path, const char *body, uint16_t length,
	 * GSResponseHandler handler, uint8_t timeout_second) { return request(
	 * GSMETHOD_POST, path, body, length, handler, timeout_second, false ); }
	 * 
	 * Response
	 * 
	 * static int8_t on_post_door_response
	 * switch (status_code) { 
	 * case 200: 
	 * case 401: // keys have expired, we have to start listening for POST /wifi again 
	 * case 400: // must be program bug, happens when there's no hostname parameter 
	 * case 408: 
	 * case 503: // heroku responds with 503 if longer than 30sec default:  // retry again on next loop 
	 * }
	 */
	class PostDoorResponse {
    }
	@ApiMethod(path="door")
	public PostDoorResponse postDoor(@Named("devicekey") String devicekey, @Named("hostname") String hostname, PostDoorResponse postDoorResponse) throws NotFoundException {
		
		// first find out the device from data store
		try {
			Device device = ObjectifyService.ofy().load().type(Device.class).filter("device_key", devicekey).first().now();
			// then save the hostname into device into data store
			device.hostname = hostname;
			ObjectifyService.ofy().save().entity(device).now();	
			
			return postDoorResponse;

		} catch (IndexOutOfBoundsException e) {
			throw new NotFoundException("Device not found with an devicekey: " + devicekey);
		}
		
		// Always return 200 here?
		// How Appengine return all the response code above?
	}
	
	
	/** Message API
	 * ** API is called by IRKit device
	 **/
	
	/**
	 * Long polling getMessages request sent by irkit device, to capture
	 * server-sent northbound actuation commands
	 * 
	 * GET https://irkitrestapi.appspot.com/_ah/api/southbound/v1/messages
	 * 
	 * Original design
	 * 
	 * Request
	 * 
	 * long polling getMessages request sent by irkit device 
	 * irkit_httpclient_get_messages 
	 * gs.get(path"/m?devicekey=%s&newer_than=%ld", &on_get_messages_response)
	 * e.g. int8_t irkit_httpclient_get_messages() { //
	 * /m?devicekey=C7363FDA0F06406AB11C29BA41272AE3&newer_than=4294967295 char
	 * path[68]; sprintf(path, P("/m?devicekey=%s&newer_than=%ld"),
	 * keys.getKey(), newest_message_id); return gs.get(path,
	 * &on_get_messages_response, 50); }
	 * 
	 * int8_t GSwifi::get(const char *path, GSResponseHandler handler, uint8_t
	 * timeout_second) { return request( GSMETHOD_GET, path, NULL, 0, handler,
	 * timeout_second, false ); }
	 *
	 * Response
	 * 
	 * on_get_messages_response: Cloud posted request command e.g. on/off.
	 * Actuation.  * receives 200 OK from Cloud/Phone  *
	 * parse_json(gs.bufferGet()): convert from gs buffered char to Json data 
	 * * irkit_json_parse  * on_json_data  * IR_put( value );  *
	 * irkit_httpclient_start_polling( 5 );
	 * 
	 * on_get_messages_response switch (status_code) { case 200: case
	 * HTTP_STATUSCODE_DISCONNECT: irkit_httpclient_start_polling( 5 ); case
	 * 503: // heroku responds with 503 if longer than 30sec, // or when deploy
	 * occurs
	 * 
	 * SignalCommand Format
	 * 
	 * test.c char buf[] =
	 * "{\"format\":\"raw\",\"freq\":38,\"data\":[50489,9039,1205,1127],\"id\":3,\"pass\":\"0123456789\"}";
	 * for (i=0; i<strlen(buf); i++) { irkit_json_parse(buf[i], &start, &data,
	 * &end); }
	 * 
	 * irkit_json_parse // (check only the first 2 letters to identify key) // -
	 * ID // - FOrmat // - FReq // - DAta // - Pass 
	 * if (first_letter_of_key == 'i' && letter == 'd') { current_token = IrJsonParserDataKeyId; } else if
	 * (first_letter_of_key == 'f' && letter == 'o') { current_token =
	 * IrJsonParserDataKeyFormat; } else if (first_letter_of_key == 'f' &&
	 * letter == 'r') { current_token = IrJsonParserDataKeyFreq; } else if
	 * (first_letter_of_key == 'd' && letter == 'a') { current_token =
	 * IrJsonParserDataKeyData; } else if (first_letter_of_key == 'p' && letter
	 * == 'a') { current_token = IrJsonParserDataKeyPass; }
	 */
	class GetMessagesResponse {
        public String message;	// "{\"format\":\"raw\",\"freq\":38,\"data\":[50489,9039,1205,1127],\"id\":3,\"pass\":\"0123456789\"}"
        						// "{\"format\":\"raw\",\"freq\":38,\"data\":[50489,9039,1205,1127],\"id\":3}"
    }
	@ApiMethod(path="messages")
	public GetMessagesResponse getMessages(@Named("devicekey") String devicekey, @Named("newer_than") int newer_than, GetMessagesResponse getMessagesResponse) throws NotFoundException {
		
		// get the latest message for this device_key
		// first, get the device instance, either from an existing device list, or
		// create new device into the device list if not existing
		PostDevice postdevice = null;
		int index = -1;
		for (int i = 0; i < postDeviceList.size(); i++) {
			if (postDeviceList.get(i).device_key == devicekey) {
				index = i;
				break;
			}
		}
		if (index == -1) { // if device doesn't exist in the list
			try {
				Device device = ObjectifyService.ofy().load().type(Device.class).filter("device_key", devicekey).first().now();
				postdevice = new PostDevice(device.id, device.hostname, device.device_key, device.client_key);
				postDeviceList.add(postdevice);
			} catch (IndexOutOfBoundsException e) {
				throw new NotFoundException("Device not found with an devicekey: " + devicekey);
			}
		} else {
			postdevice = postDeviceList.get(index);
		}
		
		// Loop, check if there is message (actuation commands) buffered on server for this device
		for (int i = 0; i < postdevice.transparentMessageBuffer.size(); i++) {
			long seq_id = postdevice.transparentMessageBuffer.get(i).seq_id;
			if (newer_than < seq_id) { // this message has not been executed / forwarded to device, i.e. this is a new message	
				// convert message from "{\"format\":\"raw\",\"freq\":38,\"data\":[18031,8755,1190,1190,1190]}"
				// to "{\"format\":\"raw\",\"freq\":38,\"data\":[50489,9039,1205,1127],\"id\":3,\"pass\":\"0123456789\"}"
				// or to "{\"format\":\"raw\",\"freq\":38,\"data\":[50489,9039,1205,1127],\"id\":3}"
				String addIDtoMessage = ",\"id\":" + String.valueOf(seq_id) + "}";
				getMessagesResponse.message = postdevice.transparentMessageBuffer.get(i).transparent_message.replace("}", addIDtoMessage);
				
				break;
			} else { // this message has been sent before, i.e. it is an old message, shall be removed from the message buffer
				postdevice.transparentMessageBuffer.remove(i);
				i--;
			}
		}
			
		// this method shall only provide success response
		return getMessagesResponse;
	}
	
	/**
	 * IRKit device post a newly learned signal data to cloud server
	 * 
	 * POST https://irkitrestapi.appspot.com/_ah/api/southbound/v1/messages
	 *
	 * Request 
	 * 
	 * irkit_httpclient_post_messages  
	 * Gs.postBinary: AT+NCTCP=1, and then
	 * Serial_->print to send Http Data: Http POST/GET,
	 * Path(“/p?devicekey=%s&freq=%d”), Host("deviceapi.getirkit.com"), body(IR
	 * signal data: sharedbuffer) I think after you do at+nctcp=x,x you want to
	 * go back to serial mode to let Arduino try to send data. Then trigger back
	 * with escape sequence to get AT commands and close the connection when
	 * done. And then also do “on_post_messages_response” to return OK to a
	 * Cloud/Phone-sent Post request
	 *
	 * int8_t irkit_httpclient_post_messages_() { // post body is IR data, move
	 * devicekey parameter to query, for implementation simplicity //
	 * /p?devicekey=C7363FDA0F06406AB11C29BA41272AE3&freq=38 char path[54];
	 * sprintf(path, P("/p?devicekey=%s&freq=%d"), keys.getKey(), IrCtrl.freq);
	 * int8_t cid = gs.postBinary( path, (const char*)sharedbuffer,
	 * IR_packedlength(), &on_post_messages_response, 10 ); }
	 * 
	 * int8_t GSwifi::postBinary(const char *path, const char *body, uint16_t
	 * length, GSResponseHandler handler, uint8_t timeout_second) { return
	 * request( GSMETHOD_POST, path, body, length, handler, timeout_second, true
	 * ); }
	 * 
	 * Response
	 * 
	 * on_post_messages_response
	 * status_code = 200
	 * or
	 * if (status_code != 200)
	 */
	class PostMessagesResponse {
    }
	@ApiMethod(path="messages")
	public PostMessagesResponse postMessages(@Named("devicekey") String devicekey, @Named("freq") float freq, @Named("body") String body, PostMessagesResponse postMessagesResponse) throws NotFoundException {
		
		// first, get the device instance, either from an existing device list,
		// or create new device into the device list if not existing
		PostDevice postdevice;
		int deviceindex = -1;
		for (int i = 0; i < postDeviceList.size(); i++) {
			if (postDeviceList.get(i).device_key == devicekey) {
				deviceindex = i;
				break;
			}
		}
		if (deviceindex == -1) { // if device doesn't exist in the list
			try {
				Device device = ObjectifyService.ofy().load().type(Device.class).filter("device_key", devicekey).first().now();
				postdevice = new PostDevice(device.id, device.hostname, device.device_key, device.client_key);
				postDeviceList.add(postdevice);
			} catch (IndexOutOfBoundsException e) {
				throw new NotFoundException("Device not found with an devicekey: " + devicekey);
			}
		} else {
			postdevice = postDeviceList.get(deviceindex);
		}

		// then, get the user instance, either from an existing user list, or
		// create new user into the user list if not existing
		PostUser postuser;
		int userindex = -1;
		for (int i = 0; i < IRkitNorthBoundRestAPI.postUserList.size(); i++) {
			if (IRkitNorthBoundRestAPI.postUserList.get(i).client_key == postdevice.client_key) {
				userindex = i;
				break;
			}
		}
		if (userindex == -1) { // if user doesn't exist in the list
			try {
				MyUser user = ObjectifyService.ofy().load().type(MyUser.class).filter("client_key", postdevice.client_key).first().now();
				postuser = new PostUser(user.id, user.name, user.passwd, user.client_key, user.api_key, user.email);
				IRkitNorthBoundRestAPI.postUserList.add(postuser);
			} catch (IndexOutOfBoundsException e) {
				throw new NotFoundException("User not found with an postdevice.client_key: " + postdevice.client_key);
			}
		} else {
			postuser = IRkitNorthBoundRestAPI.postUserList.get(userindex);
		}
		
		// save the new Message into PostUser newSignalMessage in the memory
		if(postuser != null && postdevice != null) {
			postuser.newSignalMessage.device_id = postdevice.id;
			postuser.newSignalMessage.transparent_message = body;
			
			// "body: IR data" format is "{\"format\":\"raw\",\"freq\":38,\"data\":[18031,8755,1190,1190,1190]}"
			// need to convert it to Signal format in northbound getmessages request
			// Format is described in IRKit device API "on_get_messages_request", for client app learning a new signal directly from device, without going to server
			// this should be the same, when server receives a new learning signal from device.
			/*
			gs.write("{\"format\":\"raw\",\"freq\":"); // format fixed to "raw" for now
		    gs.write(IrCtrl.freq);
		    gs.write(",\"data\":[");
		    for (uint16_t i=0; i<IrCtrl.len; i++) {
		        gs.write( IR_get() );
		        if (i != IrCtrl.len - 1) {
		            gs.write(",");
		        }
		    }
		    gs.write("]}");
		    gs.writeEnd();
			*/
			
		}
			
		// response
		return postMessagesResponse;
	}
		
	
	
	/** Signal API
	 * **
	 **/ 

	
	/** Temperature API
	 * **
	 **/
	@ApiMethod(name = "temperature.post", httpMethod = "post")
	public PostTemperature insertTemperatureData(@Named("post_id") String post_id, PostTemperature postTemperatureData) {
		// begin data store
		Temperature temperatureData;

		temperatureData = new Temperature(null, postTemperatureData.irkit_id, postTemperatureData.signal_name,
				postTemperatureData.signal_content);

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
	
	/** Device API
	 * **
	 **/
	
	
	/**
	 * Key API
	 * 
	 * This API is not used by now.
	 * 
	 * Check the "Key API" Overview part in Northbound interface
	 * 
	 * To get clientkey and deviceid, the're 2 ways.
	 * 
	 * 1. (Which is not used by now, it's not the initial bootstrapping procedure, it's just a more aggressive method to get clientkey and deviceid):
	 * If you already have a Wi-Fi connected IRKit device, use POST /keys
	 * against IRKit device to get a clienttoken, and pass it to our server on
	 * Internet using POST /1/keys. If you already have IRKit, and you want to
	 * use it more aggressively, use this course.
	 * 
	 * This API servers as the more aggressive option 1, which is not used by now.
	 * 
	 * On device side - 
	 * When client requests for a new key, via WiFi, we
	 * request server for one, and respond to client with the result from server
	 * 
	 * 1) Device receives Post /Keys request from client app, via WiFi:
	 * 
	 * Client app calls Device API via WiFi:
	 * POST /keys 
	 * Get a clienttoken 
	 * Pass clienttoken over to next request (northbound API): POST /1/keys to get clientkey and deviceid.
	 * 
	 * case 1: // POST /keys // when client requests for a new key, via WiFi, we
	 * request server for one, and respond to client with the result from server
	 * return on_post_keys_request(cid, state);
	 * 
	 * on_post_keys_request(cid, state): 
	 * // POST /keys to server 
	 * ring_put( &commands, COMMAND_POST_KEYS );
	 * 
	 * switch (command) { case COMMAND_POST_KEYS: irkit_httpclient_post_keys();
	 * 
	 * 2) Device requests key from server, send Post /Keys request to the server
	 * int8_t irkit_httpclient_post_keys() { // devicekey=[0-9A-F]{32} char
	 * body[POST_KEYS_BODY_LENGTH+1]; sprintf(body, "devicekey=%s",
	 * keys.getKey()); int8_t result = gs.post( "/k", body,
	 * POST_KEYS_BODY_LENGTH, &on_post_keys_response, 10 );
	 * 
	 * 3) on_post_keys_response
	 * switch (status_code) {
	 *  case 200:
	 *  while (! gs.bufferEmpty()) {
	 *       char letter = gs.bufferGet();
	 *           gs.write( letter );
	 *           }
	 *           default:
	 */
	class PostKeysResponse {
        public String clienttoken;	// generated by server
        							// used by the more aggressive option 1, for initial bootstrapping, key generation and key loading
        							// the clienttoken uniquely identify both a devicekey and clientkey
    }
	@ApiMethod(path="keys")
    public PostKeysResponse postKeys(@Named("devicekey") String devicekey, PostKeysResponse postKeysResponse) throws NotFoundException {
		// first find out the device from data store
		try {
			Device device = ObjectifyService.ofy().load().type(Device.class).filter("device_key", devicekey).first().now();
			postKeysResponse.clienttoken = device.device_key;	// actually, we can just use devicekey as clienttoken, seems OK, devicekey can identify both a devicekey and clientkey
			return postKeysResponse;

		} catch (IndexOutOfBoundsException e) {
			throw new NotFoundException("Device not found with an devicekey: " + devicekey);
		}

	}

}

	
	

		
	



