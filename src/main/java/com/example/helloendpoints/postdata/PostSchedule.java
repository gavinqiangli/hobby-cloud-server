package com.example.helloendpoints.postdata;

public class PostSchedule {

	public Long id;
	
	public String client_key;
	public Long device_id;
	public Long signal_id;
	public String repeat = "daily";	// now only handle "daily"
									// later on, should support "never"
									// later on, should support like iOS: "every monday", "every tuesday", .... "every sunday"
	public int hour_of_day;	// 0-23
	public int minute;		// 0 - 59

	public PostSchedule() {
	};

	public PostSchedule(Long id, String client_key, Long device_id, Long signal_id, String repeat, int hour_of_day, int minute) {
		this.id = id;
		this.client_key = client_key;
		this.device_id = device_id;
		this.signal_id = signal_id;
		this.repeat = repeat;
		this.hour_of_day = hour_of_day;
		this.minute = minute;
	}
}
