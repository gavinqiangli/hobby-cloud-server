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
public class SignalData {
	@Parent
	Key<Irkit> theIrkit;
	@Id
	public Long id;

	public String irkit_id;
	public String signal_name;
	public String signal_content;
	@Index
	public Date date;

	/**
	 * Simple constructor just sets the date
	 **/
	public SignalData() {
		date = new Date();
	}

	/**
	 * Constructor takes all important fields
	 **/
	public SignalData(String irkit, String irkit_id, String signal_name, String signal_content) {
		this();
		if (irkit != null) {
			theIrkit = Key.create(Irkit.class, irkit); // Creating the Ancestor
														// key
		} else {
			theIrkit = Key.create(Irkit.class, "default");
		}
		
		this.irkit_id = irkit_id;
		this.signal_name = signal_name;
		this.signal_content = signal_content;
	}

}
