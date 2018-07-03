package mq.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import mq.producer.ProducerService;

@Controller
@RequestMapping("/mq")
public class MqController{
	
	@Autowired
	private ProducerService producerService;
	
	
	@ResponseBody
	@RequestMapping("/sendMsg")
	public String sendMsg() {
	    try {
	        Map<String, Object> map = new HashMap<String, Object>();
	        map.put("data", "hello rabbitmq");
	        // 注意：第二个属性是 Queue 与 交换机绑定的路由
	        producerService.sendMsg(map);
	    } catch (Exception e) {
	        e.printStackTrace();
	    }
	    return "发送完毕";
	}
}
