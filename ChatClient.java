package uk.ac.cam.de300.fjava.tick2;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;

import uk.ac.cam.cl.fjava.messages.NewMessageType;
import uk.ac.cam.cl.fjava.messages.RelayMessage;
import uk.ac.cam.cl.fjava.messages.StatusMessage;
import uk.ac.cam.cl.fjava.messages.ChangeNickMessage;
import uk.ac.cam.cl.fjava.messages.ChatMessage;
import uk.ac.cam.cl.fjava.messages.DynamicObjectInputStream;
import uk.ac.cam.cl.fjava.messages.Execute;

@FurtherJavaPreamble(
		author = "Daniel Ellis",
		date = "16th Oct 2016",
		crsid = "de300",
		summary = "A chat client, part of the cambridge computer science 1B further java "
				+ "course ticklet 2. "
				+ "http://www.cl.cam.ac.uk/teaching/1617/FJava/workbook2.html",
		ticker = FurtherJavaPreamble.Ticker.A)

public class ChatClient {

	private ObjectOutputStream oos = null;
	private String serverName;
	private int portNumber;
	private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");

	//entry point
	public static void main(String[] args) {
		ChatClient chatClient = new ChatClient();
		try {
			chatClient.parseArguments(args);
		} catch (ParseArgumentsException e) {
			System.err.println("This application requires two arguments: <machine> <port>");
			return;
		}
		chatClient.startClient();
	}
	
	//start both threads, for listening to server messages and reading user input
	protected void startClient() {
		try {
			startServerListenerThread();
			startHandlingInput();
		} catch (ListenerThreadException e) {
			return;
		}
		
	}
	
	//parse the arguments passed to the program
	protected void parseArguments(String[] args) throws ParseArgumentsException {
		if (args.length != 2) {
			throw new ParseArgumentsException();
		}
		serverName = args[0];
		try {
			portNumber = Integer.valueOf(args[1]);
		} catch (NumberFormatException ex) {
			throw new ParseArgumentsException();
		}
	}

	//start the output thread to run in the background reading from the server and printing messages
	//WARNING: will execute unknown code automatically from the server
	@SuppressWarnings("resource")
	protected void startServerListenerThread() throws ListenerThreadException{
		//initialise the socket and its input and output streams
		final Socket s;
		final DynamicObjectInputStream ois;
		try {
			s = new Socket(serverName, portNumber);
			InputStream inputStream = s.getInputStream();
			ois = new DynamicObjectInputStream(inputStream);
			OutputStream outputStream = s.getOutputStream();
			oos = new ObjectOutputStream(outputStream);
		} catch (IOException ex) {
			System.err.println("Cannot connect to "+ serverName + " on port " + portNumber);
			throw new ListenerThreadException();
		} catch (SecurityException ex) {
			System.err.println("Cannot connect to "+ serverName + " on port " + portNumber);
			throw new ListenerThreadException();
		}
		
		System.out.println(getDateString() + " [Client] Connected to " + serverName + " on port " 
				+ portNumber + ".");
		
		//define a new thread for the server listener to run on
		Thread output = new Thread() {
			@Override
			public void run() {
				while (true) {	
					//read the next message and handle it as required
					try {
						//blocking call for a serialised object
						Object messageObject = ois.readObject();
						
						//handle the different types of message
						if (messageObject instanceof RelayMessage) {
							RelayMessage message = (RelayMessage) messageObject;
							
							System.out.println(getDateString() + " [" + 
									message.getFrom() + "] " + message.getMessage());
							
						} else if (messageObject instanceof StatusMessage) {
							StatusMessage message = (StatusMessage) messageObject;
							
							System.out.println(getDateString() + 
									" [Server] " + message.getMessage());
							
						} else if (messageObject instanceof NewMessageType) {
							NewMessageType message = (NewMessageType) messageObject;
							ois.addClass(message.getName(), message.getClassData());
							
							System.out.println(getDateString() + 
									" [Client] New class " + message.getName() + " loaded.");
						} else {
							//this is handling messages that are represented by classes downloaded from
							//the server at runtime, printing their fields and executing methods that 
							//have no arguments and are annotated with the Execute tag
							
							//print the fields
							Class<?> unknownClass = messageObject.getClass();
							Field[] fields = unknownClass.getDeclaredFields();
							StringBuilder response = new StringBuilder();
							response.append(getDateString() + " [Client] " + 
									unknownClass.getSimpleName() + ": ");
							
							if (fields.length != 0) {
								int i;
								for (i = 0; i < fields.length; i++) {
									Field f = fields[i];
									f.setAccessible(true);
									try {
										response.append(f.getName() + "(" + 
												getFieldContents(f, unknownClass, messageObject) + "), ");
									} catch (FieldReadException e) {
										continue;
									}
								}
								response.delete(response.length()-2, response.length());
							}	
							System.out.println(response);
							
							//execute the methods
							Method[] methods = unknownClass.getMethods();
							for (Method meth : methods) {
								if (meth.getParameterCount() == 0 && 
										meth.isAnnotationPresent(Execute.class)) {
									try {
										meth.invoke(messageObject);
									} catch (IllegalAccessException e) {
										System.err.println("illegal access exception for method");
										continue;
									} catch (InvocationTargetException e) {
										System.err.println("error calling method on unknown message object");
										continue;
									}
								}
							}
						}
					} catch (ClassNotFoundException e) {
						System.err.println("failed to read message due to stream not "
								+ "containing a valid Object");
						return;
					} catch (IOException e) {
						System.err.println("failed to read message from stream");
						return;
					} catch (IllegalArgumentException e) {
						// this should never happen
						System.err.println("error calling method on unknown message object with no "
								+ "arguments");
						return;
					} 
				}
			}
		};
		
		//the JVM can stop running the program when only daemon threads are running
		output.setDaemon(true);
		output.start();
	}
	
	//returns the string representation of the field's contents
	protected String getFieldContents(Field f, Class<?> c, Object o) throws FieldReadException {
		char[] charName = f.getName().toCharArray();
		charName[0] = Character.toTitleCase(charName[0]);
		
		String stringName = "get" + charName.toString();
		try {
			Method meth = c.getMethod(stringName);
			return meth.invoke(o).toString();
		} catch (NoSuchMethodException e) {
			return simpleFieldReader(f,o);
		} catch (SecurityException e) {
			return simpleFieldReader(f,o);
		} catch (IllegalAccessException e) {
			return simpleFieldReader(f,o);
		} catch (IllegalArgumentException e) {
			return simpleFieldReader(f,o);
		} catch (InvocationTargetException e) {
			return simpleFieldReader(f,o);
		}
	}
	
	//tries to straightforwardly read the field itself
	protected String simpleFieldReader(Field f, Object o) throws FieldReadException {
		try {
			return f.get(o).toString();
		} catch (IllegalArgumentException e1) {
			throw new FieldReadException();
		} catch (IllegalAccessException e1) {
			throw new FieldReadException();
		}
	}
	
	//generate the time string in the correct format: hours:minutes:seconds
	protected String getDateString() {
		Date date = new Date();
		return dateFormat.format(date);
	}

	//read the user's input and send it to the server or respond to commands
	protected void startHandlingInput() {
		BufferedReader r = new BufferedReader(new InputStreamReader(System.in));
		String message = null;
		
		while( true ) {
			try {
				message = r.readLine();
			} catch (IOException e) {
				System.err.println("Error reading user's message");
			}

			try {
				if (message.startsWith("\\")) {
					//parse the command
					if (message.equals("\\quit")) {
						break;
					} else if (message.startsWith("\\nick ")) {
						String newName = message.substring(6);
						ChangeNickMessage cnm = new ChangeNickMessage(newName);
						oos.writeObject(cnm);
					} else if (message.equals("\\")) {
						System.out.println(getDateString() + 
								" [Client] Unknown command \"\"");
					} else {
						System.out.println(getDateString() + 
								" [Client] Unknown command \"" + 
								message.substring(1) + "\"");
					}
				} else {
					//send the message to the server
					ChatMessage chatMessage = new ChatMessage(message);
					oos.writeObject(chatMessage);
				}
			} catch (IOException e) {
				System.err.println("Error writing message to the server");
			}
		}
		System.out.println(getDateString() + " [Client] Connection terminated.");
	}
}
