package com.example.helloendpoints;

public class PostGreeting {

	public String irkit_id;
	public String signal_name;
	public String signal_content;

	public PostGreeting() {
	};

	public PostGreeting(String irkit_id, String signal_name, String signal_content) {
		this.irkit_id = irkit_id;
		this.signal_name = signal_name;
		this.signal_content = signal_content;
	}
	
/* Following code is cancelled due to Weird behavior, repeating redundant post parameters.......
	public String getIrkitID() {
		return irkit_id;
	}

	public void setIrkitID(String irkit_id) {
		this.irkit_id = irkit_id;
	}

	public String getSignalName() {
		return signal_name;
	}

	public void setSignalName(String signal_name) {
		this.signal_name = signal_name;
	}

	public String getSignalContent() {
		return signal_content;
	}

	public void setSignalContent(String signal_content) {
		this.signal_content = signal_content;
	}
	*/
}
