package mq;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Address;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

public class MqConsumer {
	private static String IP_ADDRESS="172.16.50.1";
	
	private static int port = 5672;
	
	private static String EXCHANGE_NAME = "exchange_fanout";
	
	public static void main(String[] args) throws IOException, TimeoutException {
		
		String name = ManagementFactory.getRuntimeMXBean().getName();
		String pid = name.split("@")[0];
		
		Address[] address = new Address[]{new Address(IP_ADDRESS,port)};
		
		ConnectionFactory factory = new ConnectionFactory();
		
		factory.setUsername("root");
		factory.setPassword("root");
		
		Connection connection = factory.newConnection(address);
		
		Channel channel = connection.createChannel();
		
		channel.exchangeDeclare(EXCHANGE_NAME, "fanout");
		
		String queueName = channel.queueDeclare().getQueue();
		
		channel.queueBind(queueName, EXCHANGE_NAME, "");
		
		System.out.println(pid + "已经创建,正在等待消息...");

		Consumer consumer = new DefaultConsumer(channel){

			@Override
			public void handleDelivery(String consumerTag, Envelope envelope, BasicProperties properties, byte[] body)
					throws IOException {
				System.out.println("recv message:"+new String(body));
			}
			
		};
		
		channel.basicConsume(queueName, consumer);
		
	}
}
