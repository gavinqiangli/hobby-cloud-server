package com.example.helloendpoints.entity;


import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;
import com.googlecode.objectify.annotation.Parent;
import com.example.helloendpoints.OfyHelper;
import com.googlecode.objectify.Key;

import java.lang.String;
import java.util.Date;

/**
 * The @Entity tells Objectify about our entity.  We also register it in {@link OfyHelper}
 * Our primary key @Id is set automatically by the Google Datastore for us.
 *
 * We add a @Parent to tell the object about its ancestor. We are doing this to support many
 * guestbooks.  Objectify, unlike the AppEngine library requires that you specify the fields you
 * want to index using @Index.  Only indexing the fields you need can lead to substantial gains in
 * performance -- though if not indexing your data from the start will require indexing it later.
 *
 * NOTE - all the properties are PUBLIC so that can keep the code simple.
 **/
@Entity
public class Schedule {
	@Parent
	Key<Irkit> theIrkit;
	@Id
	public Long id;

	public String client_key;
	public Long device_id;
	public Long signal_id;
	public String repeat = "daily";	// now only handle "daily"
									// later on, should support "never"
									// later on, should support like iOS: "every monday", "every tuesday", .... "every sunday"
	public int hour_of_day;	// 0-23
	public int minute;		// 0 - 59
	
	@Index
	public Date date;

	/**
	 * Simple constructor just sets the date
	 **/
	public Schedule() {
		date = new Date();
	}

	/**
	 * Constructor takes all important fields
	 **/
	public Schedule(String irkit, String client_key, Long device_id, Long signal_id, String repeat, int hour_of_day, int minute) {
		this();
		if (irkit != null) {
			theIrkit = Key.create(Irkit.class, irkit); // Creating the Ancestor
														// key
		} else {
			theIrkit = Key.create(Irkit.class, "default");
		}
		
		this.client_key = client_key;
		this.device_id = device_id;
		this.signal_id = signal_id;
		this.repeat = repeat;
		this.hour_of_day = hour_of_day;
		this.minute = minute;
	}

}
