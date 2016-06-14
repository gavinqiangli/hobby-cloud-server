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
public class Temperature {

	@Id
	public Long id;

	@Index
	public String irkit_id;
	
	@Index
	public String signal_name;
	
	@Index
	public String signal_content;
	
	@Index
	public Date date;

	/**
	 * Simple constructor just sets the date
	 **/
	public Temperature() {
		date = new Date();
	}

	/**
	 * Constructor takes all important fields
	 **/
	public Temperature(String irkit_id, String signal_name, String signal_content) {
		this();
		
		this.irkit_id = irkit_id;
		this.signal_name = signal_name;
		this.signal_content = signal_content;
	}

}
