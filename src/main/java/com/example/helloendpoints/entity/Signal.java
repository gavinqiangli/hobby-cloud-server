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
 * Signal Entity
 * Store all registered new signals on server
 * for a client app to pick a signal and send to the device 
 **/
@Entity
public class Signal {

	@Id
	public Long id;

	@Index
	public String name;
	
	public String format;
	public double freq;
	public int[] data;
	
	@Index
	public String client_key;	// in order to filter signals for a particular user
	
	@Index
	public Date date;

	/**
	 * Simple constructor just sets the date
	 **/
	public Signal() {
		date = new Date();
	}

	/**
	 * Constructor takes all important fields
	 **/
	public Signal(String name, String format, double freq, int[] data, String client_key) {
		this();
		
		this.name = name;
		this.format = format;
		this.freq = freq;
		this.data = data;
		this.client_key = client_key;
	}

}
