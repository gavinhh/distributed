package mq.consumer;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.ChannelAwareMessageListener;
import org.springframework.stereotype.Service;

import com.rabbitmq.client.Channel;

import websocket.spring.BaseWebSocketServerHandler;

@Service
public class ConsumerService implements ChannelAwareMessageListener{

	@Override
	public void onMessage(Message message, Channel channel) throws Exception {
		System.out.println("消费者消费消息："+message.toString());
		
		//处理业务逻辑
		BaseWebSocketServerHandler.push(new String(message.getBody()));
		//消息的标识，false只确认当前一个消息收到，true确认所有consumer获得的消息 (正常消费)
		channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
		
		//ack返回false，捕获本地异常后通过此方法，使消息重新回到队列。
		//channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
		
		//拒绝消息
		//channel.basicReject(message.getMessageProperties().getDeliveryTag(), true);
	}

}
