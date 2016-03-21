package com.example.helloendpoints.postdata;

public class PostUser {

	// initialize parameter for northbound long polling request "getMessages" API session, i.e. wait/capture newly learned IRsignal - NewSignalMessage 
	//	(includes signalData, device id and hostname) from southbound, per User
	public PostMessage newSignalMessage = null;
	public boolean isInitialGetMessages = true;

	//
		
	public Long id;
	public String name;
	public String passwd;
	public String client_key;	// unique identify a user
	public String api_key;
	public String email;

	public PostUser() {
	};

	public PostUser(Long id, String name, String passwd, String client_key, String api_key, String email) {
		this.id = id;
		this.name = name;
		this.passwd = passwd;
		this.client_key = client_key;
		this.api_key = api_key;
		this.email = email;
	}
}
