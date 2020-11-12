package be.msec.labgrpc;

import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.grpc.*;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;


//import be.msec.labgrpc.CalculatorGrpc.CalculatorBlockingStub;
//import be.msec.labgrpc.CalculatorGrpc.CalculatorStub;

public class ClientgRPC {
	private static final Logger logger = Logger.getLogger(ClientgRPC.class.getName());
	
	private final ManagedChannel channel;
	private final ChatRoomgRPC.ChatRoomBlockingStub blockingStub;
	private StreamObserver<Chat.ChatRequest> chat;
	private String token = "";
	private boolean ingelogd = false;
	//private final CalculatorStub asyncStub;
	
	public ClientgRPC(String host, int port) {
		this(ManagedChannelBuilder.forAddress(host, port).usePlaintext().build());
	}
	
	public ClientgRPC(ManagedChannel channel) {
		this.channel = channel;
		blockingStub = ChatRoomgRPC.newBlockingStub(channel);
		//channel = channelBuilder.build();
		//asyncStub = CalculatorGrpc.newStub(channel);
	}

	public void shutdown() throws InterruptedException {
		channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
	}

	public boolean login(String name) {
		Chat.LoginRequest request = Chat.LoginRequest.newBuilder().setName(name).build();
		Chat.LoginResponse response;
		try {
			response = blockingStub.login(request);
		} catch (StatusRuntimeException e) {
			logger.error("rpc failed with status:" + e.getStatus() + " message:" + e.getMessage());
			return false;
		}
		logger.info("login with name {} OK!"+ name);
		this.token = response.getToken();
		this.ingelogd = true;
		startReceive();
		return true;
	}

	public void startReceive() {
		Metadata meta = new Metadata();
		meta.put(Constant.HEADER_ROLE,this.token);

		chat =  MetadataUtils.attachHeaders(ChatRoomGrpc.newStub(this.channel),meta).chat(new StreamObserver<Chat.ChatResponse>() {
			@Override
			public void onNext(Chat.ChatResponse value) {
				switch (value.getEventCase()){
					case ROLE_LOGIN:
					{
						logger.info("user {}:login!!",value.getRoleLogin().getName());
					}
					break;
					case ROLE_LOGOUT:
					{
						logger.info("user {}:logout!!",value.getRoleLogout().getName());
					}
					break;
					case ROLE_MESSAGE:
					{
						logger.info("user {}:{}",value.getRoleMessage().getName(),value.getRoleMessage().getMsg());
					}
					break;
					case EVENT_NOT_SET:
					{
						logger.error("receive event error:{}",value);
					}
					break;
					case SERVER_SHUTDOWN:
					{
						logger.info("server closed!");
						logout();
					}
					break;
				}
			}
			@Override
			public void onError(Throwable t) {
				logger.error("got error from server:{}",t.getMessage(),t);
			}

			@Override
			public void onCompleted() {
				logger.info("closed by server");
			}
		});
		Metadata header = new Metadata();
		header.put(Constant.HEADER_ROLE,this.token);
	}

	public void sendMessage (String msg) throws InterruptedException {
		if("LOGOUT".equals(msg)){
			this.chat.onCompleted();
			this.logout();
			this.Loggined = false;
			shutdown();
		}else{
			if(this.chat != null) this.chat.onNext(Chat.ChatRequest.newBuilder().setMessage(msg).build());
		}
	}

	public void logout(){
		Chat.LogoutResponse resp = blockingStub.logout(Chat.LogoutRequest.newBuilder().build());
		logger.info("logout result:{}",resp);
	}
	
	/*public void calculateSum(int a, int b){
		info("Calculating sum of {0} and {1}", a, b);
		
		Sum request = Sum.newBuilder().setA(a).setB(b).build();
		CalculatorReply reply;
		try{
			reply = blockingStub.calculateSum(request);
		} catch (StatusRuntimeException e) {
			logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
			return;
		}
		
		info("Solution of the {0} + {1} = {2}", a, b, reply.getSolution());
	}
	
	public void streamingSum(int operands) throws InterruptedException {
		info("Streaming sum");
		final CountDownLatch finishLatch = new CountDownLatch(1);
		StreamObserver<CalculatorReply> responseObserver = new StreamObserver<CalculatorReply>() {
			@Override
			public void onNext(CalculatorReply reply) {
				info("Finished streaming sum. The solution is {0}", reply.getSolution());
			}
			
			@Override
			public void onError(Throwable t){
				Status status = Status.fromThrowable(t);
				logger.log(Level.WARNING, "StreamingSum failed:{0}", status);
				finishLatch.countDown();
			}
			
			@Override
			public void onCompleted(){
				info("Finished StreamingSum");
				finishLatch.countDown();
			}
		};
		
		StreamObserver<Sum> requestObserver = asyncStub.streamingSum(responseObserver);
		try{
			int a;
			int b;
			Sum request;
			for(int i = 0; i < (operands+1)/2; i++){
				a = (int)(Math.random() * 10);
				b = (int)(Math.random() * 10);
				if(i == ((operands+1)/2-1) && (operands % 2) != 0){
					b = 0;
					info("Adding new summand {0}", a);
				} else
					info("Adding new summands {0} and {1} ", a, b);
				
				request = Sum.newBuilder().setA(a).setB(b).build();
				
				requestObserver.onNext(request);
				
				Thread.sleep(1000);
				if (finishLatch.getCount() == 0){
					// RPC completed or errored before we finished sending.
					// Sending further request won't error, but they will just be thrown away.
					return;
				}
					
			}
		} catch (RuntimeException e){
			requestObserver.onError(e);
			throw e;
		}
		
		// Mark the end of requests
		requestObserver.onCompleted();
		
		// Receiving happens asynchronously
		finishLatch.await(1, TimeUnit.MINUTES);
	}
	
	public void calculatorHistory(){
		info("Requesting calculator history");
		
		Iterator<Calculation> calculations;
		try{
			calculations = blockingStub.calculatorHistory(Empty.newBuilder().build());
		} catch (StatusRuntimeException e) {
			logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
			return;
		}
		
		StringBuilder responseLog = new StringBuilder("History:\n");
		while(calculations.hasNext()){
			Calculation calculation = calculations.next();
			responseLog.append(calculation);
		}
		
		info(responseLog.toString());
	}*/

	public static void main(String[] args) throws InterruptedException {
		ClientgRPC client = new ClientgRPC("localhost", 50050);
		try{
			String name = "";
			Scanner sc = new Scanner(System.in);
			do{
				System.out.println("please input your nickname");
				name = sc.nextLine();
			}while (!client.login(name));

			while(client.ingelogd){
				name = sc.nextLine();
				if(client.ingelogd)client.sendMessage(name);
			}
		} finally {
			client.shutdown();
		}
		

	}

	/*private static void info(String msg, Object... params) {
		logger.log(Level.INFO, msg, params);
	}*/

}
