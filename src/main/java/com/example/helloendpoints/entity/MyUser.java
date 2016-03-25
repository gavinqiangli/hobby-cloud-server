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
public class MyUser {
	
	@Id
	public Long id;

	@Index
	public String name;	// name is also the email address?
	
	public String passwd;
	
	@Index
	public String client_key;	// unique identify a user
	
	@Index
	public String api_key;
	
	@Index
	public String email;

	@Index
	public Date date;

	/**
	 * Simple constructor just sets the date
	 **/
	public MyUser() {
		date = new Date();
	}

	/**
	 * Constructor takes all important fields
	 **/
	public MyUser(String name, String passwd, String client_key, String api_key, String email) {
		this();
		
		this.name = name;
		this.passwd = passwd;
		this.client_key = client_key;
		this.api_key = api_key;
		this.email = email;

	}

}
