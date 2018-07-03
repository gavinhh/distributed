package websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONObject;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
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

public class WebSocketServerHandler extends SimpleChannelInboundHandler<Object> {

	private static final Logger logger = LoggerFactory.getLogger(WebSocketServerHandler.class);

	private WebSocketServerHandshaker handshaker;

	/**
	 * 当客户端连接成功，返回个成功信息
	 */
	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		// TODO Auto-generated method stub
		push(ctx, "连接成功");
	}

	/**
	 * 当客户端断开连接
	 */
	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
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
		// http：//xxxx
		if (msg instanceof FullHttpRequest) {

			handleHttpRequest(ctx, (FullHttpRequest) msg);
		} else if (msg instanceof WebSocketFrame) {
			// ws://xxxx
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
		System.out.println("服务端收到：" + request);
		
		ctx.channel().write( new TextWebSocketFrame(request+ " , 欢迎使用Netty WebSocket服务，现在时刻："+ new java.util.Date().toString()));

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

			// 根据type 存放进对于的channel池，这里就简单实现，直接放进aaChannelGroup,方便群发
		}

	}
	// 第一次请求是http请求，请求头包括ws的信息
	public void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req) {

		if (!req.decoderResult().isSuccess()) {

			sendHttpResponse(ctx, req,
					new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST));
			return;
		}

		WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
				"ws:/" + ctx.channel() + "/websocket", null, false);
		handshaker = wsFactory.newHandshaker(req);

		if (handshaker == null) {
			// 不支持
			WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
		} else {

			handshaker.handshake(ctx.channel(), req);
		}

	}

	public static void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest req, DefaultFullHttpResponse res) {

		// 返回应答给客户端
		if (res.status().code() != 200) {
			ByteBuf buf = Unpooled.copiedBuffer(res.status().toString(), CharsetUtil.UTF_8);
			res.content().writeBytes(buf);
			buf.release();
		}

		// 如果是非Keep-Alive，关闭连接
		ChannelFuture f = ctx.channel().writeAndFlush(res);
		if (!isKeepAlive(req) || res.status().code() != 200) {
			f.addListener(ChannelFutureListener.CLOSE);
		}

	}

	private static boolean isKeepAlive(FullHttpRequest req) {
		return false;
	}

	// 异常处理，netty默认是关闭channel
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		cause.printStackTrace();
		ctx.close();
	}

	/**
	 * 推送单个
	 * 
	 */
	public static final void push(final ChannelHandlerContext ctx, final String message) {
		TextWebSocketFrame tws = new TextWebSocketFrame(message);
		ctx.channel().writeAndFlush(tws);

	}

	/**
	 * 群发
	 * 
	 */
	public static final void push(final ChannelGroup ctxGroup, final String message) {

		TextWebSocketFrame tws = new TextWebSocketFrame(message);
		ctxGroup.writeAndFlush(tws);

	}
}
