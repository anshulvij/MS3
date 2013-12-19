package app_kvEcs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.UnknownHostException;
import java.nio.channels.ShutdownChannelGroupException;
import java.util.ArrayList;
import java.util.List;

import logger.LogSetup;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import app_kvClient.KVClient;
import client.KVStore;
import client.ClientSocketListener.SocketStatus;
import common.messages.KVMessage;
import common.messages.TextMessage;
import common.messages.KVMessage.StatusType;
import ecs.ECServer;
import ecs.ServerNodeData;

public class ECSClient {
	
	private int mNodeCount=0;
	private boolean mStorageServiceRunning = false;
	private boolean mStorageServiceInitiated = false;
	
	private static Logger logger = Logger.getRootLogger();
	private static final String PROMPT = "ECSClient> ";
	private BufferedReader stdin;
	private boolean stop=false;
	private ECServer mECSServer;
	public ECSClient(String string) {
		
		mECSServer = new ECServer(string);
		// TODO Auto-generated constructor stub
	}

	public static void main(String[] args) {
    	try {
			new LogSetup("logs/ecs/ecsclient.log", Level.ALL);
			if(args!=null&&args.length==1){
				ECSClient app = new ECSClient(args[0]);
				app.run();
			} else {
				System.out.println("Error! Invalid number of parameters. Parameter count should be 1.");
				System.exit(1);
			}
			
		} catch (IOException e) {
			System.out.println("Error! Unable to initialize logger!");
			e.printStackTrace();
			System.exit(1);
		}
		

		
	
		
		
	}
	
	public void run() {
		while(!stop) {
			stdin = new BufferedReader(new InputStreamReader(System.in));
			System.out.print(PROMPT);
			
			try {
				String cmdLine = stdin.readLine();
				this.handleCommand(cmdLine);
			} catch (IOException e) {
				stop = true;
				printError("CLI does not respond - Application terminated ");
			}
		}
	}
	
	private void handleCommand(String cmdLine) {
		if(cmdLine != null) {
			String[] tokens = cmdLine.split("\\s+");
	
			if(tokens[0].equals("quit")) {	
				mECSServer.shutDown();
				mStorageServiceInitiated = false;
				mStorageServiceRunning = false;
				stop = true;
				
				//TODO: Shutdown KVServers and then exit application
				System.out.println(PROMPT + "Application exit!");
			
			} else if (tokens[0].equals("initService")){
				if(tokens.length == 2) {
					try{
					
						mNodeCount = Integer.parseInt(tokens[1]);
						if(mECSServer.getMaxAvailableNodeCount() > mNodeCount) {
							boolean result = mECSServer.initService(mNodeCount);
							if(result) {
								printNewMessage("Storage service is initiated.");
								mStorageServiceInitiated=true;
							} else {
								printError("Unable to initialize storage service due to internal error.");
							}
							
						} else {
							printError("Unable to initialize storage service since given number of nodes : "+mNodeCount+" is more than total available nodes : "+ mECSServer.getMaxAvailableNodeCount());
						}
					} catch(NumberFormatException nfe) {
						printError("Invalid argument! Number of nodes should be between 1 to 8.");
						logger.info("Unable to parse argument <port>", nfe);
					} 
				} else {
					printError("Invalid number of parameters!");
				}
				
			} else  if (tokens[0].equals("start")) {
				if(tokens.length == 1) {
					if(mStorageServiceInitiated) { 
						boolean result = mECSServer.start();
						if(result) {
							printNewMessage("Storage service is started. Clients can access KVServers now.");
							mStorageServiceRunning=true;
						} else {
							printError("Unable to start storage service due to internal error.");
						}
						
					} else {
						printError("Illegal Operation! First initialize storage service by \"initService\" command");
					}
						
					
				} else {
					printError("Invalid number of parameters!");
				}
				
			} else  if (tokens[0].equals("stop")) {
				if(tokens.length == 1) {
					if(mStorageServiceRunning) {
						boolean result = mECSServer.stop();
						if(result) {
							printNewMessage("Storage service is stopped!");
							mStorageServiceRunning = false;
						} else {
							printError("Unable to stop storage service due to internal error.");
						}
						
					} else {
						printError("Illegal Operation! First start storage service by \"start\" command");
					}
					
				} else {
					printError("Invalid number of parameters!");
				}
				
			} else  if (tokens[0].equals("shutdown")) {
				if(tokens.length == 1) {
					if(mStorageServiceInitiated) {
						boolean result = mECSServer.shutDown();
						if(result) {
							printNewMessage("Storage service is shutdown!");
							mStorageServiceRunning = false;
							mStorageServiceInitiated = false;
						} else {
							printError("Unable to shutdown storage service due to internal error.");
						}
						
					} else {
						printError("Illegal Operation! No storage servers are running. First initialize the storage service by \"initService\" command.");	
					}
					
				} else {
					printError("Invalid number of parameters!");
				}
				
			} else  if (tokens[0].equals("addNode")) {
				if(tokens.length == 1) {
					if(mStorageServiceRunning) {
						if(mECSServer.getActivatedNodeCount() < mECSServer.getMaxAvailableNodeCount()) {
							boolean result = mECSServer.addNode();
							if(result) {
								printNewMessage("Added node successfully!");
							} else {
								printError("Unable to add new node due to internal error.");
							}
						} else {
							printError("Cannot add more nodes; Maximum node count  reached.");
						}
							
					} else {
						printError("Illegal operation!  No storage servers are running. First initialize the storage service by \"initService\" command.");
					}
					
				} else {
					printError("Invalid number of parameters!");
				}
				
			} else  if (tokens[0].equals("removeNode")) {
				if(tokens.length == 1) { 
					if(mECSServer.getActivatedNodeCount() > 0) {
						boolean result = mECSServer.removeNode();
						if(result) {
							printNewMessage("Removed node successfully!");
						} else {
							printError("Unable to remove a node due to internal error.");
						}
					} else {
						printError("Cannot remove node; No node is running.");
					}

				} else {
					printError("Invalid number of parameters!");
				}
				
			} else if(tokens[0].equals("logLevel")) {
				if(tokens.length == 2) {
					String level = setLevel(tokens[1]);
					if(level.equals(LogSetup.UNKNOWN_LEVEL)) {
						printError("No valid log level!");
						printPossibleLogLevels();
					} else {
						System.out.println(PROMPT + 
								"Log level changed to level " + level);
					}
				} else {
					printError("Invalid number of parameters!");
				}
				
			} else if(tokens[0].equals("help")) {
				printHelp();
			} else {
				printError("Unknown command");
				printHelp();
			}
		}
	}
	
/*	private void sendMessage(String msg){
		try {
			mKVStore.sendMessage(new TextMessage(msg));
		} catch (IOException e) {
			printError("Unable to send message!");
			disconnect();
		}
	}*/


	
	
	private void printHelp() {
		//TODO 2: Define new help for ECSClient
		
		StringBuilder sb = new StringBuilder();
		sb.append(PROMPT).append("ECS CLIENT HELP (Usage):\n");
		sb.append(PROMPT);
		sb.append("::::::::::::::::::::::::::::::::");
		sb.append("::::::::::::::::::::::::::::::::\n");
		sb.append(PROMPT).append("connect <host> <port>");
		sb.append("\t establishes a connection to a server\n");
		sb.append(PROMPT).append("send <text message>");
		sb.append("\t\t sends a text message to the server \n");
		sb.append(PROMPT).append("disconnect");
		sb.append("\t\t\t disconnects from the server \n");
		
		sb.append(PROMPT).append("logLevel");
		sb.append("\t\t\t changes the logLevel \n");
		sb.append(PROMPT).append("\t\t\t\t ");
		sb.append("ALL | DEBUG | INFO | WARN | ERROR | FATAL | OFF \n");
		
		sb.append(PROMPT).append("quit ");
		sb.append("\t\t\t exits the program");
		System.out.println(sb.toString());
	}
	
	private void printPossibleLogLevels() {
		System.out.println(PROMPT 
				+ "Possible log levels are:");
		System.out.println(PROMPT 
				+ "ALL | DEBUG | INFO | WARN | ERROR | FATAL | OFF");
	}

	private String setLevel(String levelString) {
		
		if(levelString.equals(Level.ALL.toString())) {
			logger.setLevel(Level.ALL);
			return Level.ALL.toString();
		} else if(levelString.equals(Level.DEBUG.toString())) {
			logger.setLevel(Level.DEBUG);
			return Level.DEBUG.toString();
		} else if(levelString.equals(Level.INFO.toString())) {
			logger.setLevel(Level.INFO);
			return Level.INFO.toString();
		} else if(levelString.equals(Level.WARN.toString())) {
			logger.setLevel(Level.WARN);
			return Level.WARN.toString();
		} else if(levelString.equals(Level.ERROR.toString())) {
			logger.setLevel(Level.ERROR);
			return Level.ERROR.toString();
		} else if(levelString.equals(Level.FATAL.toString())) {
			logger.setLevel(Level.FATAL);
			return Level.FATAL.toString();
		} else if(levelString.equals(Level.OFF.toString())) {
			logger.setLevel(Level.OFF);
			return Level.OFF.toString();
		} else {
			return LogSetup.UNKNOWN_LEVEL;
		}
	}
	
	
	
	public void printNewMessage(String msg) {
		if(!stop) {
		    System.out.print(PROMPT);
			System.out.println(msg);
		}
	}
	


	private void printError(String error){
		System.out.println(PROMPT + "Error! " +  error);
	}


}