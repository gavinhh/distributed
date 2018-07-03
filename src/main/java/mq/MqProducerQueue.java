package mq;

import java.io.IOException;
import java.util.Date;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

public class MqProducerQueue {
private static String IP_ADDRESS="172.16.50.1";
	
	private static int port = 5672;
	
	private static String EXCHANGE_NAME = "exchange_direct";
	
	private static String QUEUE_NAME = "queue_demo";
	
	public static void main(String[] args) throws IOException, TimeoutException, InterruptedException {
		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost(IP_ADDRESS);
		factory.setPort(port);
		factory.setUsername("root");
		factory.setPassword("root");
		Connection connection = factory.newConnection();
		Channel channel = connection.createChannel();
		channel.exchangeDeclare(EXCHANGE_NAME, "direct");
		channel.queueBind(QUEUE_NAME, EXCHANGE_NAME, "mq.queue");
		int i = 0;
		System.out.println("producer create ...");
		while(i<9){
			Thread.sleep(1000);
			String msg = i +" ......";
			System.out.println(" producer msg:"+msg);
			channel.basicPublish(EXCHANGE_NAME, "mq.queue", null, msg.getBytes());
			i++;
		}
		channel.close();
	}
}
