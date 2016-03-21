package com.example.helloendpoints.postdata;

public class PostSignal {

	public Long id;
	public String name;
	public String format;
    public double freq;
    public int[] data;
    
    public String client_key; // in order to filter signals for a particular user

	public PostSignal() {
	};

	public PostSignal(Long id, String name, String format, double freq, int[] data, String client_key) {
		this.id = id;
		this.name = name;
		this.format = format;
		this.freq = freq;
		this.data = data;
		this.client_key = client_key;
	}
}
