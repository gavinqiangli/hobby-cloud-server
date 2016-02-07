package com.example.helloendpoints.api;

import com.example.helloendpoints.Constants;
import com.example.helloendpoints.entity.ScheduleData;
import com.example.helloendpoints.entity.SignalData;
import com.example.helloendpoints.entity.TemperatureData;
import com.example.helloendpoints.entity.UserData;
import com.example.helloendpoints.postdata.PostScheduleData;
import com.example.helloendpoints.postdata.PostSignalData;
import com.example.helloendpoints.postdata.PostTemperatureData;
import com.example.helloendpoints.postdata.PostUserData;
import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.response.NotFoundException;
import com.google.appengine.api.users.User;
import com.googlecode.objectify.LoadResult;
import com.googlecode.objectify.ObjectifyService;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Named;

/**
 * Defines v1 of a IRKit API, which provides simple "greeting" methods.
 */
@Api(name = "irkit", version = "v1", scopes = { Constants.EMAIL_SCOPE }, clientIds = { Constants.WEB_CLIENT_ID,
		Constants.ANDROID_CLIENT_ID, Constants.IOS_CLIENT_ID,
		Constants.API_EXPLORER_CLIENT_ID }, audiences = { Constants.ANDROID_AUDIENCE })
public class IRkitRestAPI {

	public static ArrayList<PostTemperatureData> temperatureDataList = new ArrayList<PostTemperatureData>();

	public PostTemperatureData getTemperatureData(@Named("id") Integer id) throws NotFoundException {
		// read from data store
		try {
			TemperatureData temperatureData = ObjectifyService.ofy().load().type(TemperatureData.class).list().get(id);
			PostTemperatureData postTemperatureData = new PostTemperatureData(temperatureData.irkit_id, temperatureData.signal_name, temperatureData.signal_content);
			return postTemperatureData;
		} catch (IndexOutOfBoundsException e) {
			throw new NotFoundException("Greeting not found with an index: " + id);
		}
	}

	@ApiMethod(path="get_all_temperature")
	public ArrayList<PostTemperatureData> listTemperatureData() {
		// read from data store
		temperatureDataList.clear();
		List<TemperatureData> list = ObjectifyService.ofy().load().type(TemperatureData.class).list();
		for (TemperatureData temperatureData : list) {
			temperatureDataList.add(new PostTemperatureData(temperatureData.irkit_id, temperatureData.signal_name, temperatureData.signal_content));
		}
		return temperatureDataList;
	}
	
	@ApiMethod(path="get_latest_temperature")
	public PostTemperatureData getLatestTemperatureData() throws NotFoundException {
		// read from data store
		try {
			TemperatureData temperatureData = ObjectifyService.ofy().load().type(TemperatureData.class).order("-date").first().now();
			PostTemperatureData postTemperatureData = new PostTemperatureData(temperatureData.irkit_id, temperatureData.signal_name, temperatureData.signal_content);
			return postTemperatureData;
		} catch (IndexOutOfBoundsException e) {
			throw new NotFoundException("Greeting not found with an index: latest date");
		}
	}

	@ApiMethod(name = "temperature.post", httpMethod = "post")
	public PostTemperatureData insertTemperatureData(@Named("post_id") String post_id, PostTemperatureData postTemperatureData) {

		// begin data store
		TemperatureData temperatureData;

		String irkit = "IRKit" + post_id;

		temperatureData = new TemperatureData(irkit, postTemperatureData.irkit_id, postTemperatureData.signal_name, postTemperatureData.signal_content);

		// Use Objectify to save the greeting and now() is used to make the call
		// synchronously as we
		// will immediately get a new page using redirect and we want the data
		// to be present.
		ObjectifyService.ofy().save().entity(temperatureData).now();

		// end data store

		PostTemperatureData response = new PostTemperatureData();
		response.irkit_id = postTemperatureData.irkit_id;
		response.signal_name = postTemperatureData.signal_name;
		response.signal_content = postTemperatureData.signal_content;

		return response;
	}

	@ApiMethod(name = "temperature.authed", path = "temperature/authed")
	public PostTemperatureData authedTemperature(User user) {
		PostTemperatureData response = new PostTemperatureData("hi", "hej", "hello " + user.getEmail());
		return response;
	}
	
	
	/* Signal API
	 * **
	 */
	public static ArrayList<PostSignalData> signalDataList = new ArrayList<PostSignalData>();

	public PostSignalData getSignalData(@Named("id") Integer id) throws NotFoundException {
		// read from data store
		try {
			SignalData signalData = ObjectifyService.ofy().load().type(SignalData.class).list().get(id);
			PostSignalData postSignalData = new PostSignalData(signalData.irkit_id, signalData.signal_name, signalData.signal_content);
			return postSignalData;
		} catch (IndexOutOfBoundsException e) {
			throw new NotFoundException("Greeting not found with an index: " + id);
		}
	}

	@ApiMethod(path="get_all_signal")
	public ArrayList<PostSignalData> listSignalData() {
		// read from data store
		signalDataList.clear();
		List<SignalData> list = ObjectifyService.ofy().load().type(SignalData.class).list();
		for (SignalData signalData : list) {
			signalDataList.add(new PostSignalData(signalData.irkit_id, signalData.signal_name, signalData.signal_content));
		}
		return signalDataList;
	}
	
	@ApiMethod(path="get_latest_signal")
	public PostSignalData getLatestSignalData() throws NotFoundException {
		// read from data store
		try {
			SignalData signalData = ObjectifyService.ofy().load().type(SignalData.class).order("-date").first().now();
			PostSignalData postSignalData = new PostSignalData(signalData.irkit_id, signalData.signal_name, signalData.signal_content);
			return postSignalData;
		} catch (IndexOutOfBoundsException e) {
			throw new NotFoundException("Greeting not found with an index: latest date");
		}
	}

	@ApiMethod(name = "signal.post", httpMethod = "post")
	public PostSignalData insertSignalData(@Named("post_id") String post_id, PostSignalData postSignalData) {

		// begin data store
		SignalData signalData;

		String irkit = "IRKit" + post_id;

		signalData = new SignalData(irkit, postSignalData.irkit_id, postSignalData.signal_name, postSignalData.signal_content);

		// Use Objectify to save the greeting and now() is used to make the call
		// synchronously as we
		// will immediately get a new page using redirect and we want the data
		// to be present.
		ObjectifyService.ofy().save().entity(signalData).now();

		// end data store

		PostSignalData response = new PostSignalData();
		response.irkit_id = postSignalData.irkit_id;
		response.signal_name = postSignalData.signal_name;
		response.signal_content = postSignalData.signal_content;

		return response;
	}

	@ApiMethod(name = "signal.authed", path = "signal/authed")
	public PostSignalData authedSignal(User user) {
		PostSignalData response = new PostSignalData("hi", "hej", "hello " + user.getEmail());
		return response;
	}
	
	
	/* Schedule API
	 * **
	 */
	public static ArrayList<PostScheduleData> scheduleDataList = new ArrayList<PostScheduleData>();

	public PostScheduleData getScheduleData(@Named("id") Integer id) throws NotFoundException {
		// read from data store
		try {
			ScheduleData scheduleData = ObjectifyService.ofy().load().type(ScheduleData.class).list().get(id);
			PostScheduleData postScheduleData = new PostScheduleData(scheduleData.irkit_id, scheduleData.signal_name, scheduleData.signal_content);
			return postScheduleData;
		} catch (IndexOutOfBoundsException e) {
			throw new NotFoundException("Greeting not found with an index: " + id);
		}
	}

	@ApiMethod(path="get_all_schedule")
	public ArrayList<PostScheduleData> listScheduleData() {
		// read from data store
		scheduleDataList.clear();
		List<ScheduleData> list = ObjectifyService.ofy().load().type(ScheduleData.class).list();
		for (ScheduleData scheduleData : list) {
			scheduleDataList.add(new PostScheduleData(scheduleData.irkit_id, scheduleData.signal_name, scheduleData.signal_content));
		}
		return scheduleDataList;
	}
	
	@ApiMethod(path="get_latest_schedule")
	public PostScheduleData getLatestScheduleData() throws NotFoundException {
		// read from data store
		try {
			ScheduleData scheduleData = ObjectifyService.ofy().load().type(ScheduleData.class).order("-date").first().now();
			PostScheduleData postScheduleData = new PostScheduleData(scheduleData.irkit_id, scheduleData.signal_name, scheduleData.signal_content);
			return postScheduleData;
		} catch (IndexOutOfBoundsException e) {
			throw new NotFoundException("Greeting not found with an index: latest date");
		}
	}

	@ApiMethod(name = "schedule.post", httpMethod = "post")
	public PostScheduleData insertScheduleData(@Named("post_id") String post_id, PostScheduleData postScheduleData) {

		// begin data store
		ScheduleData scheduleData;

		String irkit = "IRKit" + post_id;

		scheduleData = new ScheduleData(irkit, postScheduleData.irkit_id, postScheduleData.signal_name, postScheduleData.signal_content);

		// Use Objectify to save the greeting and now() is used to make the call
		// synchronously as we
		// will immediately get a new page using redirect and we want the data
		// to be present.
		ObjectifyService.ofy().save().entity(scheduleData).now();

		// end data store

		PostScheduleData response = new PostScheduleData();
		response.irkit_id = postScheduleData.irkit_id;
		response.signal_name = postScheduleData.signal_name;
		response.signal_content = postScheduleData.signal_content;

		return response;
	}

	@ApiMethod(name = "schedule.authed", path = "schedule/authed")
	public PostScheduleData authedSchedule(User user) {
		PostScheduleData response = new PostScheduleData("hi", "hej", "hello " + user.getEmail());
		return response;
	}
	
	
	/* User API
	 * **
	 */
	public static ArrayList<PostUserData> userDataList = new ArrayList<PostUserData>();

	public PostUserData getUserData(@Named("id") Integer id) throws NotFoundException {
		// read from data store
		try {
			UserData userData = ObjectifyService.ofy().load().type(UserData.class).list().get(id);
			PostUserData postUserData = new PostUserData(userData.irkit_id, userData.signal_name, userData.signal_content);
			return postUserData;
		} catch (IndexOutOfBoundsException e) {
			throw new NotFoundException("Greeting not found with an index: " + id);
		}
	}

	@ApiMethod(path="get_all_user")
	public ArrayList<PostUserData> listUserData() {
		// read from data store
		userDataList.clear();
		List<UserData> list = ObjectifyService.ofy().load().type(UserData.class).list();
		for (UserData userData : list) {
			userDataList.add(new PostUserData(userData.irkit_id, userData.signal_name, userData.signal_content));
		}
		return userDataList;
	}
	
	@ApiMethod(path="get_latest_user")
	public PostUserData getLatestUserData() throws NotFoundException {
		// read from data store
		try {
			UserData userData = ObjectifyService.ofy().load().type(UserData.class).order("-date").first().now();
			PostUserData postUserData = new PostUserData(userData.irkit_id, userData.signal_name, userData.signal_content);
			return postUserData;
		} catch (IndexOutOfBoundsException e) {
			throw new NotFoundException("Greeting not found with an index: latest date");
		}
	}

	@ApiMethod(name = "user.post", httpMethod = "post")
	public PostUserData insertUserData(@Named("post_id") String post_id, PostUserData postUserData) {

		// begin data store
		UserData userData;

		String irkit = "IRKit" + post_id;

		userData = new UserData(irkit, postUserData.irkit_id, postUserData.signal_name, postUserData.signal_content);

		// Use Objectify to save the greeting and now() is used to make the call
		// synchronously as we
		// will immediately get a new page using redirect and we want the data
		// to be present.
		ObjectifyService.ofy().save().entity(userData).now();

		// end data store

		PostUserData response = new PostUserData();
		response.irkit_id = postUserData.irkit_id;
		response.signal_name = postUserData.signal_name;
		response.signal_content = postUserData.signal_content;

		return response;
	}

	@ApiMethod(name = "user.authed", path = "user/authed")
	public PostUserData authedUser(User user) {
		PostUserData response = new PostUserData("hi", "hej", "hello " + user.getEmail());
		return response;
	}
	
}



	
	

		
	



