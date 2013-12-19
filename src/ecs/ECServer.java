package ecs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.json.Json;
import javax.json.JsonObjectBuilder;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Logger;

import common.messages.JSONSerializer;
import common.messages.KVAdminMessage;
import common.messages.KVAdminMessage.Commands;
import common.messages.TextMessage;
import metadata.MetaData;

public class ECServer {

	private static final int BUFFER_SIZE = 1024;
	private static final int DROP_SIZE = 128 * BUFFER_SIZE;
	List<MetaData> mMetaData;
	List<ServerNodeData> mServerConfig;
	private List<Socket> mEcsClientSockets;
	private HashMap <String,Socket> mEcsClientSocketMap;
	JsonObjectBuilder objectBuilder = Json.createObjectBuilder();
	HashMap<String, BigInteger> hashMap;
	TreeMap<String ,BigInteger> sorted;
	
	private static Logger logger = Logger.getRootLogger();


	public ECServer(String string) {
		//logger.addAppender(new ConsoleAppender());
		File cmdLineConfig = null;
		if(string!=null){
			cmdLineConfig = new File(string);
		} else {
			//Error
		}
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(cmdLineConfig));
		} catch (FileNotFoundException e1) {
			// TODO Add logs
			e1.printStackTrace();

		}
		String line = null;
		String[] tokens = null;
		mServerConfig = new ArrayList<ServerNodeData>();
		try {
			while ((line = reader.readLine()) != null) {
				tokens=line.split(" ");
				if(tokens.length ==3){
					ServerNodeData tempServerConfig = new ServerNodeData(tokens[0],tokens[1],tokens[2]);
					mServerConfig.add(tempServerConfig);
				} else {
					// TODO Log this error

					System.out.println("Invalid ECS Config file : "+cmdLineConfig.getPath());
				}
			}
		} catch (IOException e) {
			// TODO Log this error
			e.printStackTrace();
		}
	}

	public int getMaxAvailableNodeCount() {
		if(mServerConfig!=null)
			return mServerConfig.size();
		else
			return 0;
	}
	
	public int getActivatedNodeCount() {
		if(mMetaData!=null)
			return mMetaData.size();
		else
			return 0;
	}




	public boolean initService	(int numberOfNodes) {
		boolean result = true;
		initMetaData(numberOfNodes);
		mEcsClientSockets = new ArrayList<Socket>();
		mEcsClientSocketMap = new HashMap<String, Socket>();
		String cmd= null;
		for(MetaData metaData: mMetaData ){
			System.out.println("Now="+new BigInteger(metaData.getRangeStart(),16)+":"+new BigInteger(metaData.getRangeEnd(),16));
			try{
				execSSH(metaData);
				Thread.sleep(1000);
				Socket ecsClientSocket = new Socket(metaData.getIP(),Integer.parseInt(metaData.getPort()));
				mEcsClientSocketMap.put(metaData.getIP()+":"+Integer.parseInt(metaData.getPort()), ecsClientSocket);
				mEcsClientSockets.add(ecsClientSocket);
				
			} catch (IOException e) {
				//TODO Log this error
				result = false;
				System.out.println("io");
				e.printStackTrace();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				result = false;
				e.printStackTrace();
			}
		}
		
		System.out.println("KVServers="+mEcsClientSockets.size());
		
		result = sendKVServersMetaData();
		return result;
	}

	public boolean start(){
		// TODO convert this command in EcsAdminMsg and send to all servers
		boolean result = true;
		for(Socket socket: mEcsClientSockets){

			try {
				TextMessage txtMsg = JSONSerializer.marshalKVAdminMsg(null, Commands.START, "","");
				sendMessage(socket, txtMsg);
				
				TextMessage responseTxtMsg = receiveMessage(socket);
				KVAdminMessage responseKVAdminMsg = JSONSerializer.unmarshalKVAdminMsgForCommand(responseTxtMsg);
				if(!responseKVAdminMsg.getCommand().equals(Commands.START_SUCCESS)) {
					result = false;
					break;
				} 

			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				result =  false;
			}
		}
		return result;
	}

	private boolean sendStartCommand(String ip, int port) {
		Socket socket = mEcsClientSocketMap.get(ip+":"+Integer.toString(port));
		try {
			
			TextMessage txtMsg = JSONSerializer.marshalKVAdminMsg(null, Commands.START, "","");
			sendMessage(socket, txtMsg);
			TextMessage responseTxtMsg = receiveMessage(socket);
			KVAdminMessage responseKVAdminMsg = JSONSerializer.unmarshalKVAdminMsgForCommand(responseTxtMsg);
			if(responseKVAdminMsg.getCommand().equals(Commands.START_SUCCESS)) {
				return true;
			} else {
				logger.error("Received response  !START_SUCCESSS from KVServer-"+socket.getInetAddress().getHostAddress()+":"+socket.getPort());
				return false;
			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
	}

	public boolean stop(){
		boolean result = true;
		for(Socket socket: mEcsClientSockets){
			try {
				TextMessage txtMsg = JSONSerializer.marshalKVAdminMsg(null, Commands.STOP, "","");
				sendMessage(socket, txtMsg);
				
				TextMessage responseTxtMsg = receiveMessage(socket);
				KVAdminMessage responseKVAdminMsg = JSONSerializer.unmarshalKVAdminMsgForCommand(responseTxtMsg);
				if(!responseKVAdminMsg.getCommand().equals(Commands.STOP_SUCCESS)) {
					logger.error("Received response  !STOP_SUCCESSS from KVServer-"+socket.getInetAddress().getHostAddress()+":"+socket.getPort());
					
					result = false;
					break;
				} 

			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				result = false;
			}
		}	
		return result;
	}

	public boolean shutDown(){
		boolean result = true;
		for(Socket socket: mEcsClientSockets){

			try {
				TextMessage txtMsg = JSONSerializer.marshalKVAdminMsg(null, Commands.SHUTDOWN, "","");
				sendMessage(socket, txtMsg);
				
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				result = false;
			}


		}	
		return result;
	}

	public boolean shutDownOldNode(String ip , int port){
		boolean result = true;
		Socket socket = mEcsClientSocketMap.get(ip+":"+Integer.toString(port));
		try {
			TextMessage txtMsg = JSONSerializer.marshalKVAdminMsg(null, Commands.SHUTDOWN, "","");
			sendMessage(socket, txtMsg);
			
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			result=false;
			e.printStackTrace();
		}
		return result;

	}

	private boolean sendKVServersMetaData(){
		boolean result = true;
		for(Socket socket : mEcsClientSockets) {
			try {
				TextMessage txtMsg = JSONSerializer.marshalKVAdminMsg(mMetaData, Commands.INIT, "","");
				sendMessage(socket, txtMsg);
				TextMessage responseTxtMsg = receiveMessage(socket);
				KVAdminMessage responseKVAdminMsg = JSONSerializer.unmarshalKVAdminMsgForCommand(responseTxtMsg);
				if(!responseKVAdminMsg.getCommand().equals(Commands.INIT_SUCCESS)) {
					logger.error("Received response  !INIT_SUCCESSS from KVServer-"+socket.getInetAddress().getHostAddress()+":"+socket.getPort());
					result = false;
					break;
				}

			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				result = false;
			}

		}
		return result;
	}

	private boolean updateKVServersMetaData(){
		boolean result = true;
		for(Socket socket : mEcsClientSockets) {
			try {
				TextMessage txtMsg = JSONSerializer.marshalKVAdminMsg(mMetaData, Commands.UPDATE, "","");
				sendMessage(socket, txtMsg);
				TextMessage responseTxtMsg = receiveMessage(socket);
				KVAdminMessage responseKVAdminMsg = JSONSerializer.unmarshalKVAdminMsgForCommand(responseTxtMsg);
				if(!responseKVAdminMsg.getCommand().equals(Commands.UPDATE_SUCCESS)) {
					logger.error("Received response  !UPDATE_SUCCESSS from KVServer-"+socket.getInetAddress().getHostAddress()+":"+socket.getPort());
					result = false;
					break;
				}

			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				result = false;
			}

		}
		return result;
	}

	private boolean updateKVServerMetaData(String ip, int port) {

		Socket socket = mEcsClientSocketMap.get(ip+":"+Integer.toString(port));
		try {
			TextMessage txtMsg = JSONSerializer.marshalKVAdminMsg(mMetaData, Commands.UPDATE, "","");
			sendMessage(socket, txtMsg);
			TextMessage responseTxtMsg = receiveMessage(socket);
			KVAdminMessage responseKVAdminMsg = JSONSerializer.unmarshalKVAdminMsgForCommand(responseTxtMsg);
			if(responseKVAdminMsg.getCommand().equals(Commands.UPDATE_SUCCESS)) {
				return true;
			} else {
				logger.error("Received response  !UPDATE_SUCCESSS from KVServer-"+socket.getInetAddress().getHostAddress()+":"+socket.getPort());
				return false;
			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}

	}

	private boolean initNewNodeMetaData(String ip, int port) {

		Socket socket = mEcsClientSocketMap.get(ip+":"+Integer.toString(port));
		try {
			TextMessage txtMsg = JSONSerializer.marshalKVAdminMsg(mMetaData, Commands.INIT, "","");
			sendMessage(socket, txtMsg);
			TextMessage responseTxtMsg = receiveMessage(socket);
			KVAdminMessage responseKVAdminMsg = JSONSerializer.unmarshalKVAdminMsgForCommand(responseTxtMsg);
			if(responseKVAdminMsg.getCommand().equals(Commands.INIT_SUCCESS)) {
				return true;
			} else {
				logger.error("Received response  !INIT_SUCCESSS from KVServer-"+socket.getInetAddress().getHostAddress()+":"+socket.getPort());
				return false;
			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}

	}

	private boolean setLockWrite(String ip, int port) {

		Socket socket = mEcsClientSocketMap.get(ip+":"+Integer.toString(port));
		try {
			TextMessage txtMsg = JSONSerializer.marshalKVAdminMsg(null, Commands.LOCK_WRITE, "","");
			sendMessage(socket, txtMsg);
			TextMessage responseTxtMsg = receiveMessage(socket);
			KVAdminMessage responseKVAdminMsg = JSONSerializer.unmarshalKVAdminMsgForCommand(responseTxtMsg);
			if(responseKVAdminMsg.getCommand().equals(Commands.LOCK_WRITE_SUCCESS)) {
				return true;
			} else {
				logger.error("Received response  !LOCK_WRITE_SUCCESSS from KVServer-"+socket.getInetAddress().getHostAddress()+":"+socket.getPort());
				return false;
			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}

	}

	private boolean releaseLockWrite(String ip, int port) {
		Socket socket = mEcsClientSocketMap.get(ip+":"+Integer.toString(port));
		try {
			TextMessage txtMsg = JSONSerializer.marshalKVAdminMsg(null, Commands.UNLOCK_WRITE, "","");
			sendMessage(socket, txtMsg);
			TextMessage responseTxtMsg = receiveMessage(socket);		
			KVAdminMessage responseKVAdminMsg = JSONSerializer.unmarshalKVAdminMsgForCommand(responseTxtMsg);
			
			if(responseKVAdminMsg.getCommand().equals(Commands.UNLOCK_WRITE_SUCCESS)) {
				return true;
			} else {
				logger.error("Received response  !UNLOCK_WRITE_SUCCESSS from KVServer-"+socket.getInetAddress().getHostAddress()+":"+socket.getPort());
				return false;
			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.out.println("releaseLockWrite()=exception:"+e);
			return false;
		}

	}

	private boolean moveData(MetaData sourceMetaData, MetaData destMetaData) {
		Socket socket = mEcsClientSocketMap.get(sourceMetaData.getIP()+":"+sourceMetaData.getPort());
		try {
			TextMessage txtMsg = JSONSerializer.marshalKVAdminMsg(null, Commands.MOVE_DATA, 
					destMetaData.getIP()+":"+destMetaData.getPort(),
					destMetaData.getRangeStart()+":"+destMetaData.getRangeEnd());
			sendMessage(socket, txtMsg);
			TextMessage responseTxtMsg = receiveMessage(socket);		
			KVAdminMessage responseKVAdminMsg = JSONSerializer.unmarshalKVAdminMsgForCommand(responseTxtMsg);
			if(responseKVAdminMsg.getCommand().equals(Commands.MOVE_DATA_SUCCESS)) {
				return true;
			} else {
				logger.error("Received response  !MOVE_DATA_SUCCESSS from KVServer-"+socket.getInetAddress().getHostAddress()+":"+socket.getPort());
				return false;
			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}		
	}

	public boolean addNode(){

		List<MetaData> oldMetaData = mMetaData;
		MetaData newNodeMetaData = null;
		MetaData successorNodeMetaData = null;
		initMetaData(getActivatedNodeCount()+1);
		int i=0;
		
		System.out.println("oldMetaData="+oldMetaData);
		System.out.println("newMetaData="+mMetaData);
		
		
		
		
		for(MetaData metaData: oldMetaData){			
			if(!metaData.getIP().equals(mMetaData.get(i).getIP()) || !metaData.getPort().equals(mMetaData.get(i).getPort())){
				//This is the newly added node
				newNodeMetaData = mMetaData.get(i);
				successorNodeMetaData = mMetaData.get(i+1);
				break;
			}
			i++;
		}
		if(newNodeMetaData ==null){
			newNodeMetaData = mMetaData.get(mMetaData.size()-1);
			successorNodeMetaData = mMetaData.get(0);

		}
		try {
			execSSH(newNodeMetaData);
			
			System.out.println("Adding new socket for newly added server-"+newNodeMetaData.getIP()+":"+newNodeMetaData.getPort());
			Socket ecsClientSocket = new Socket(newNodeMetaData.getIP(),Integer.parseInt(newNodeMetaData.getPort()));
			mEcsClientSocketMap.put(newNodeMetaData.getIP()+":"+newNodeMetaData.getPort(), ecsClientSocket);
			mEcsClientSockets.add(ecsClientSocket);
		} catch (IOException e) {
			//TODO Log this error
			e.printStackTrace();
		}
		boolean result=false;
		result = initNewNodeMetaData(newNodeMetaData.getIP(), Integer.parseInt(newNodeMetaData.getPort()));
		System.out.println("addNode-1:result="+result);
		if(result) {
			
			result = sendStartCommand(newNodeMetaData.getIP(), Integer.parseInt(newNodeMetaData.getPort()));
			System.out.println("addNode-2:result="+result);
			if(result) {
				
				result = setLockWrite(successorNodeMetaData.getIP(), Integer.parseInt(successorNodeMetaData.getPort()));
				System.out.println("addNode-3:result="+result);
				if(result) {
					result = moveData(successorNodeMetaData, newNodeMetaData);
					
					System.out.println("addNode-4:result="+result);
					if(result) {
						result = updateKVServersMetaData();
						System.out.println("addNode-5:result="+result);
						if(result) {
							
							result = releaseLockWrite(successorNodeMetaData.getIP(), Integer.parseInt(successorNodeMetaData.getPort()));
							System.out.println("addNode-6:result="+result);
						}
					}
				}
			}

		}
		return result;

	}



	public boolean removeNode(){
		List<MetaData> oldMetaData = mMetaData;
		MetaData oldNodeMetaData = null;
		MetaData successorNodeMetaData = null;
		initMetaData(getActivatedNodeCount()-1);
		int i=0;
		for(MetaData metaData: mMetaData){
			if(!metaData.getIP().equals(oldMetaData.get(i).getIP()) || !metaData.getPort().equals(oldMetaData.get(i).getPort())){
				//This is the old removed node
				oldNodeMetaData = oldMetaData.get(i);
				successorNodeMetaData = oldMetaData.get(i+1);
				break;
			}
			i++;
		}
		if(oldNodeMetaData==null){
			oldNodeMetaData = oldMetaData.get(oldMetaData.size()-1);
			successorNodeMetaData = oldMetaData.get(0);
		}

		boolean result=false;
		result = setLockWrite(oldNodeMetaData.getIP(), Integer.parseInt(oldNodeMetaData.getPort()));
		if(result) {
			result = setLockWrite(successorNodeMetaData.getIP(), Integer.parseInt(successorNodeMetaData.getPort()));
			if(result) {
				result = updateKVServerMetaData(successorNodeMetaData.getIP(), Integer.parseInt(successorNodeMetaData.getPort()));
				if(result) {
					result = moveData(oldNodeMetaData, successorNodeMetaData);
					if(result) {
						result = updateKVServersMetaData();
						if(result) {
							result = releaseLockWrite(successorNodeMetaData.getIP(), Integer.parseInt(successorNodeMetaData.getPort()));
						} if(result) {
							result = shutDownOldNode(oldNodeMetaData.getIP(), Integer.parseInt(oldNodeMetaData.getPort()));
						}
					}
				}
			}

		}
		mEcsClientSocketMap.remove(oldNodeMetaData.getIP()+":"+oldNodeMetaData.getPort());
		mEcsClientSockets.remove(mEcsClientSockets.size()-1);
		return result;

	}

	private String getMD5(String msg){
		MessageDigest messageDigest = null;
		try {
			messageDigest = MessageDigest.getInstance("MD5");
		} catch(NoSuchAlgorithmException ex){
			// TODO : Add logs
			return null;
		}

		messageDigest.reset();
		messageDigest.update(msg.getBytes());
		byte[] hashValue = messageDigest.digest();
		BigInteger bigInt = new BigInteger(1,hashValue);
		String hashHex = bigInt.toString(16);
		// Now we need to zero pad it if you actually want the full 32 chars.
		while(hashHex.length() < 32 ){
			hashHex = "0"+hashHex;
		}
		return hashHex;
	}

	private void initMetaData(int nodeCount){
		mMetaData = new ArrayList<MetaData>();
		MetaData tempMetaData = null;
		hashMap = new HashMap<String, BigInteger>();



		for(int i=0;i<nodeCount;i++){
			String ipPort = mServerConfig.get(i).getIPAddress()+":"+mServerConfig.get(i).getPort();
			BigInteger prevRangeBi = new BigInteger(getMD5(ipPort),16);
			hashMap.put(ipPort, prevRangeBi);
		}	
		ValueComparator vc = new ValueComparator(hashMap);
		sorted = new TreeMap<String, BigInteger>(vc);
		sorted.putAll(hashMap);

		int i = 1;
		MetaData previous = new MetaData();
		for (String key : sorted.keySet()) {

			System.out.println(key + " : " + sorted.get(key)); // why null values here?

			tempMetaData = new MetaData();
			String tokens[] = key.split(":");

			tempMetaData.setIP(tokens[0]);
			tempMetaData.setPort(tokens[1]);

			//TODO change range values back to HEX after completion
			tempMetaData.setRangeEnd(sorted.get(key).toString(16));
			//				tempMetaData.setRangeEnd(sorted.get(key).toString());//decimal form

			if(i>1){

				BigInteger prevRangeBi = new BigInteger(previous.getRangeEnd(),16);
				BigInteger nextRangeBi = prevRangeBi.add(new BigInteger("1"));
				//TODO change range values back to HEX after completion					
				tempMetaData.setRangeStart(nextRangeBi.toString(16));
				//					tempMetaData.setRangeStart(nextRangeBi.toString());//decimal form

			}
			mMetaData.add(tempMetaData);
			previous = tempMetaData;
			i++;

		}

		BigInteger prevRangeBi = new BigInteger(mMetaData.get(mMetaData.size()-1).getRangeEnd(),16);
		BigInteger nextRangeBi = prevRangeBi.add(new BigInteger("1"));

		//TODO change range values back to HEX after completion
		mMetaData.get(0).setRangeStart(nextRangeBi.toString(16));
		//			mMetaData.get(0).setRangeStart(nextRangeBi.toString());//decimal form

		System.out.println(sorted.values()); // But we do have non-null values here!


	}

	/**
	 * Method sends a TextMessage using this socket.
	 * 
	 * @param msg
	 *            the message that is to be sent.
	 * @throws IOException
	 *             some I/O error regarding the output stream
	 */
	public void sendMessage(Socket socket, TextMessage msg) throws IOException {
		OutputStream output = socket.getOutputStream();
		byte[] msgBytes = msg.getMsgBytes();
		output.write(msgBytes, 0, msgBytes.length);
		logger.info("sendMessage() ="+msg.getMsg());
		output.flush();
		logger.info("SEND \t<" + socket.getInetAddress().getHostAddress()
				+ ":" + socket.getPort() + ">: '" + msg.getMsgBytes().toString() + "'");
	}

	private TextMessage receiveMessage(Socket socket) throws IOException {
		InputStream input = socket.getInputStream();
		int index = 0;
		byte[] msgBytes = null, tmp = null;
		byte[] bufferBytes = new byte[BUFFER_SIZE];

		/* read first char from stream */
//		System.out.println("BEFORE");
		logger.info("receiveMessage() of:"+socket);
		logger.info("receiveMessage-->before Read");
		byte read = (byte) input.read();
		logger.info("receiveMessage-->after Read");
//		System.out.println("After");
		boolean reading = true;

		while (read != 13 && reading) {/* carriage return */
			/* if buffer filled, copy to msg array */
			if (index == BUFFER_SIZE) {
				logger.info("receiveMessage-->index == BUFFER SIZE");
				if (msgBytes == null) {
					tmp = new byte[BUFFER_SIZE];
					System.arraycopy(bufferBytes, 0, tmp, 0, BUFFER_SIZE);
				} else {
					tmp = new byte[msgBytes.length + BUFFER_SIZE];
					System.arraycopy(msgBytes, 0, tmp, 0, msgBytes.length);
					System.arraycopy(bufferBytes, 0, tmp, msgBytes.length,
							BUFFER_SIZE);
				}

				msgBytes = tmp;
				bufferBytes = new byte[BUFFER_SIZE];
				index = 0;
			}

			/* only read valid characters, i.e. letters and constants */
			bufferBytes[index] = read;
			index++;

			/* stop reading is DROP_SIZE is reached */
			if (msgBytes != null && msgBytes.length + index >= DROP_SIZE) {
				logger.info("receiveMessage-->DROP SIZE reached");
				reading = false;
			}

			/* read next char from stream */
			logger.info("receiveMessage-->before Read2");
			read = (byte) input.read();
			logger.info("receiveMessage-->after Read2");
		}

		if (msgBytes == null) {
			tmp = new byte[index];
			System.arraycopy(bufferBytes, 0, tmp, 0, index);
		} else {
			tmp = new byte[msgBytes.length + index];
			System.arraycopy(msgBytes, 0, tmp, 0, msgBytes.length);
			System.arraycopy(bufferBytes, 0, tmp, msgBytes.length, index);
		}

		msgBytes = tmp;

		/* build final String */
		TextMessage msg = new TextMessage(msgBytes);
		logger.info("RECEIVE \t<"
				+ socket.getInetAddress().getHostAddress() + ":"
				+ socket.getPort() + ">: '" + msg.getMsg().trim() + "'"
				+ "=" + msgBytes + ",");
		return msg;
	}

	public void execSSH(MetaData metaData) throws IOException{
		
		/*String cmd = "java -jar ms3-server.jar "+metaData.getPort();
//		//cmd = "ssh -n "+metaData.getIP()+" nohup java -jar ms3-server.jar "+metaData.getPort()+" ERROR &";
		Runtime run = Runtime.getRuntime();
		run.exec(cmd);
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/
		
		
	}


	static class ValueComparator implements Comparator<String> {

		Map<String, BigInteger> base;

		ValueComparator(Map<String, BigInteger> base) {
			this.base = base;
		}

		@Override
		public int compare(String a, String b) {
			BigInteger x = base.get(a);
			BigInteger y = base.get(b);

			return x.compareTo(y);
		}
	}


}