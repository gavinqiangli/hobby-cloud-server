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
public class Device {
	@Parent
	Key<Irkit> theIrkit;
	@Id
	public Long id;

	public String hostname;
	public String device_key;
	public String client_key;	// unique identify a user, who owns the device

	@Index
	public Date date;

	/**
	 * Simple constructor just sets the date
	 **/
	public Device() {
		date = new Date();
	}

	/**
	 * Constructor takes all important fields
	 **/
	public Device(String irkit, String hostname, String device_key, String client_key) {
		this();
		if (irkit != null) {
			theIrkit = Key.create(Irkit.class, irkit); // Creating the Ancestor
														// key
		} else {
			theIrkit = Key.create(Irkit.class, "default");
		}
		
		this.hostname = hostname;
		this.device_key = device_key;
		this.client_key = client_key;
	}

}
