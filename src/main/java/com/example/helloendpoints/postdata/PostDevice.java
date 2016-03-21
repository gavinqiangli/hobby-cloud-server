package com.example.helloendpoints.postdata;

import java.util.ArrayList;

public class PostDevice {

	// transparently buffer the message body received from northbound request "postMessage", for actuation commands
	// will be read by southbound long polling request "getMessages" API session, i.e. wait/capture new signal message command from northbound, per device
	public ArrayList<PostMessage> transparentMessageBuffer = new ArrayList<PostMessage>();

		
	public Long id;
	public String hostname;
	public String device_key;
	public String client_key;	// unique identify a user, who owns the device

	public PostDevice() {
	};

	public PostDevice(Long id, String hostname, String device_key, String client_key) {
		this.id = id;
		this.hostname = hostname;
		this.device_key = device_key;
		this.client_key = client_key;
	}
}
