package uk.ac.cam.de300.fjava.tick2;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

class TestMessageReadWrite {

	//write the given message to the given file
	static boolean writeMessage(String message, String filename) {
		TestMessage testMessage = new TestMessage();
		testMessage.setMessage(message);
		
		try {
			FileOutputStream fos = new FileOutputStream(filename);
			ObjectOutputStream out = new ObjectOutputStream(fos);
			out.writeObject(testMessage);
			out.close();
		} catch (IOException e) {
			System.err.println("failed to write message to file");
			return false;
		}
		
		return true;
	}

	//read the message from the given location
	static String readMessage(String location) {
		InputStream inputStream;
		if (location.startsWith("http://")) {
			//set the input stream to read the message from a webpage
			try {
				URL url = new URL(location);
				URLConnection urlConnection = url.openConnection();
				inputStream = urlConnection.getInputStream();
			} catch (MalformedURLException e) {
				System.err.println("failed to read message due to a malformed URL being provided");
				return null;
			} catch (IOException e) {
				System.err.println("failed to read message from URL");
				return null;
			} 
			
		} else {
			//set the input stream to read the message from a file
			try {
				inputStream = new FileInputStream(location);
			} catch (FileNotFoundException e) {
				System.err.println("failed to read message due to the file not existing");
				return null;
			}
		}
		
		//read the message from the input stream
		TestMessage testMessage = null;
		try {
			ObjectInputStream ois = new ObjectInputStream(inputStream);
			testMessage = (TestMessage) ois.readObject();
			return testMessage.getMessage();
		} catch (ClassNotFoundException e) {
			System.err.println("failed to read message due to stream not containing a TestMessage class");
			return null;
		} catch (IOException e) {
			System.err.println("failed to read message from stream");
			return null;
		}
	}

	public static void main(String args[]) {
		//test the methods
		System.out.println(readMessage("http://www.cl.cam.ac.uk/teaching/current/FJava/testmessage-de300.jobj"));
		
		writeMessage("This is a test", "testFile");
		System.out.println(readMessage("testFile"));
	}
}