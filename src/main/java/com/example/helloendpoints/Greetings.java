package com.example.helloendpoints;

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
 * Defines v1 of a helloworld API, which provides simple "greeting" methods.
 */
@Api(name = "helloworld", version = "v1", scopes = { Constants.EMAIL_SCOPE }, clientIds = { Constants.WEB_CLIENT_ID,
		Constants.ANDROID_CLIENT_ID, Constants.IOS_CLIENT_ID,
		Constants.API_EXPLORER_CLIENT_ID }, audiences = { Constants.ANDROID_AUDIENCE })
public class Greetings {

	public static ArrayList<PostGreeting> post_greetings = new ArrayList<PostGreeting>();

	static {
		post_greetings.add(new PostGreeting("hello world!", "hello world!", "hello world!"));
		post_greetings.add(new PostGreeting("goodbye world!", "goodbye world!", "goodbye world!"));
	}

	public PostGreeting getGreeting(@Named("id") Integer id) throws NotFoundException {
		// read from data store
		try {
			Greeting greeting = ObjectifyService.ofy().load().type(Greeting.class).list().get(id);
			PostGreeting postgreeting = new PostGreeting(greeting.irkit_id, greeting.signal_name, greeting.signal_content);
			return postgreeting;
		} catch (IndexOutOfBoundsException e) {
			throw new NotFoundException("Greeting not found with an index: " + id);
		}
	}

	@ApiMethod(path="get_all")
	public ArrayList<PostGreeting> listGreeting() {
		// read from data store
		post_greetings.clear();
		List<Greeting> greetinglist = ObjectifyService.ofy().load().type(Greeting.class).list();
		for (Greeting greeting : greetinglist) {
			post_greetings.add(new PostGreeting(greeting.irkit_id, greeting.signal_name, greeting.signal_content));
		}
		return post_greetings;
	}
	
	@ApiMethod(path="get_latest")
	public PostGreeting getLatestGreeting() throws NotFoundException {
		// read from data store
		try {
			Greeting greeting = ObjectifyService.ofy().load().type(Greeting.class).order("-date").first().now();
			PostGreeting postgreeting = new PostGreeting(greeting.irkit_id, greeting.signal_name, greeting.signal_content);
			return postgreeting;
		} catch (IndexOutOfBoundsException e) {
			throw new NotFoundException("Greeting not found with an index: latest date");
		}
	}

	@ApiMethod(name = "greetings.post", httpMethod = "post")
	public PostGreeting insertGreeting(@Named("post_id") String post_id, PostGreeting post_gretting) {

		// begin data store
		Greeting greeting_store;

		String irkit = "IRKit" + post_id;

		greeting_store = new Greeting(irkit, post_gretting.irkit_id, post_gretting.signal_name, post_gretting.signal_content);

		// Use Objectify to save the greeting and now() is used to make the call
		// synchronously as we
		// will immediately get a new page using redirect and we want the data
		// to be present.
		ObjectifyService.ofy().save().entity(greeting_store).now();

		// end data store

		PostGreeting response = new PostGreeting();
		response.irkit_id = post_gretting.irkit_id;
		response.signal_name = post_gretting.signal_name;
		response.signal_content = post_gretting.signal_content;

		return response;
	}

	@ApiMethod(name = "greetings.authed", path = "hellogreeting/authed")
	public PostGreeting authedGreeting(User user) {
		PostGreeting response = new PostGreeting("hi", "hej", "hello " + user.getEmail());
		return response;
	}
}
