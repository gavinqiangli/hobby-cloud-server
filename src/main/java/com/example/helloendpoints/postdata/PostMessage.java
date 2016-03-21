package com.example.helloendpoints.postdata;

public class PostMessage {

	public Long id;		// auto generated id by google app engine, random numeric, unique
    public Long seq_id;	// manually generated id by sequence, = System.currentTimeMillis(), in sequence, unique per device

	public Long device_id;
    public String client_key;
    public String transparent_message;
    public Long signal_id;

	public PostMessage() {
	};

	public PostMessage(Long id, Long seq_id, String client_key, Long device_id, String transparent_message, Long signal_id) {
		this.id = id;
		this.seq_id = seq_id;
		this.client_key = client_key;
		this.device_id = device_id;
		this.transparent_message = transparent_message;
		this.signal_id = signal_id;
	}
}
