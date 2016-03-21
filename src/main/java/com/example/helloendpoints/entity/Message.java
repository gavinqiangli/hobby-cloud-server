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

/**
 * Message Entity
 * Store all server-sent messages (actuation commands)
 * for data mining purpose
 **/
@Entity
public class Message {
	@Parent
	Key<Irkit> theIrkit;
	@Id
	public Long id;

	@Index
	public String client_key;
	
	@Index
	public Long device_id;
	
	public String transparent_message;	// "{\"format\":\"raw\",\"freq\":38,\"data\":[18031,8755,1190,1190,1190]}"
	
	@Index
	public Long signal_id;
	
	@Index
	public Date date;

	/**
	 * Simple constructor just sets the date
	 **/
	public Message() {
		date = new Date();
	}

	/**
	 * Constructor takes all important fields
	 **/
	public Message(String irkit, String client_key, Long device_id, String transparent_message, Long signal_id) {
		this();
		if (irkit != null) {
			theIrkit = Key.create(Irkit.class, irkit); // Creating the Ancestor
														// key
		} else {
			theIrkit = Key.create(Irkit.class, "default");
		}
		
		this.client_key = client_key;
		this.device_id = device_id;
		this.transparent_message = transparent_message;
		this.signal_id = signal_id;
	}

}
