package websocket.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson.JSONObject;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.util.CharsetUtil;
import mq.producer.ProducerService;

/**
 * websocket 具体业务处理方法
 * 
 */

@Component
@Sharable
public class WebSocketServerHandler extends BaseWebSocketServerHandler {
	
	private static Logger logger = LoggerFactory.getLogger(WebSocketServerHandler.class);

	private WebSocketServerHandshaker handshaker;
	
	@Autowired
	private ProducerService producerService;

	/**
	 * 当客户端连接成功，返回个成功信息
	 */
	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		logger.info("webSocket connect success!");
		Constant.aaChannelGroup.add(ctx.channel());
		//Constant.pushCtxMap.put(key, ctx);
	}

	/**
	 * 当客户端断开连接
	 */
	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		logger.info("webSocket connetion is closed");
		for (String key : Constant.pushCtxMap.keySet()) {
			if (ctx.equals(Constant.pushCtxMap.get(key))) {
				// 从连接池内剔除
				System.out.println(Constant.pushCtxMap.size());
				System.out.println("剔除" + key);
				Constant.pushCtxMap.remove(key);
				System.out.println(Constant.pushCtxMap.size());
			}

		}
	}

	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
		ctx.flush();
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
		if (msg instanceof FullHttpRequest) {
			logger.info("http request!");
			handleHttpRequest(ctx, (FullHttpRequest) msg);
		} else if (msg instanceof WebSocketFrame) {
			logger.info("websocket request!");
			handlerWebSocketFrame(ctx, (WebSocketFrame) msg);
		}

	}

	public void handlerWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
		// 关闭请求
		if (frame instanceof CloseWebSocketFrame) {
			handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
			return;
		}
		// ping请求
		if (frame instanceof PingWebSocketFrame) {
			ctx.channel().write(new PongWebSocketFrame(frame.content().retain()));
			return;
		}
		// 只支持文本格式，不支持二进制消息
		if (!(frame instanceof TextWebSocketFrame)) {
			throw new Exception("仅支持文本格式");
		}

		// 客服端发送过来的消息
		String request = ((TextWebSocketFrame) frame).text();
		logger.info("服务端收到：" + request);
		//将接受到的信息推送到rabbitmq
		producerService.sendMsg(request);
//		ctx.channel().write(new TextWebSocketFrame(request + " , 欢迎使用Netty WebSocket服务，现在时刻：" + new java.util.Date().toString()));
		
		//调用producerService 将消息写入rabbitmq
		

		JSONObject jsonObject = null;

		try {
			jsonObject = JSONObject.parseObject(request);
			System.out.println(jsonObject.toJSONString());
		} catch (Exception e) {
		}
		if (jsonObject == null) {
			return;
		}

		String id = (String) jsonObject.get("id");
		String type = (String) jsonObject.get("type");

		// 根据id判断是否登陆或者是否有权限等
		if (id != null && !"".equals("id") && type != null && !"".equals("type")) {
			// 用户是否有权限
			boolean idAccess = true;
			// 类型是否符合定义
			boolean typeAccess = true;
			
			if (idAccess && typeAccess) {
				System.out.println("添加到连接池：" + request);
				Constant.pushCtxMap.put(request, ctx);
				Constant.aaChannelGroup.add(ctx.channel());
			}
		}
	}

	/**
	 * 第一次请求是http请求，请求头包括ws的信息
	 * @param ctx
	 * @param req
	 */
	public void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req) {
		if (!req.decoderResult().isSuccess() || !"websocket".equals(req.headers().get("Upgrade"))) {
			sendHttpResponse(ctx, req,
					new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST));
			return;
		}

		WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
				"ws:/" + ctx.channel() + "/websocket", null, false);
		handshaker = wsFactory.newHandshaker(req);
		if (handshaker == null) {
			WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
		} else {
			handshaker.handshake(ctx.channel(), req);
		}
	}

	/**
	 * 返回应答给客户端
	 * @param ctx
	 * @param req
	 * @param res
	 */
	public static void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest req, DefaultFullHttpResponse res) {
		if (res.status().code() != 200) {
			ByteBuf buf = Unpooled.copiedBuffer(res.status().toString(), CharsetUtil.UTF_8);
			res.content().writeBytes(buf);
			buf.release();
		}
		
		ChannelFuture f = ctx.channel().writeAndFlush(res);
		// 如果是非Keep-Alive，关闭连接
		if (!isKeepAlive(req) || res.status().code() != 200) {
			f.addListener(ChannelFutureListener.CLOSE);
		}

	}

	private static boolean isKeepAlive(FullHttpRequest req) {
		return false;
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		cause.printStackTrace();
		ctx.close();
	}

}
