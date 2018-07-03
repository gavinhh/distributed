package mq.producer;

import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ProducerService {
	@Autowired
    private AmqpTemplate amqpTemplate;
	
	@Value("#{configProperties['mq.exchange.fanout']}")
	private String exchange;
	
	public void sendMsg(Object object) {
        // convertAndSend 将Java对象转换为消息发送至匹配key的交换机中Exchange
        amqpTemplate.convertAndSend(exchange, "", object);
    }
}
