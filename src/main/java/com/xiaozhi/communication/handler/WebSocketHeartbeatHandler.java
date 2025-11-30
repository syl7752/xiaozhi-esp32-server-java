package com.xiaozhi.communication.handler;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

import static com.xiaozhi.communication.handler.WebSocketHandshakeHandler.SESSION_ID;

/**
 * WebSocket 心跳处理器
 * 处理连接空闲事件，发送心跳包
 */
@Component
@ChannelHandler.Sharable
public class WebSocketHeartbeatHandler extends ChannelInboundHandlerAdapter {

  private static final Logger logger = LoggerFactory.getLogger(WebSocketHeartbeatHandler.class);

  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
    if (evt instanceof IdleStateEvent) {
      IdleStateEvent event = (IdleStateEvent) evt;
      String sessionId = ctx.channel().attr(SESSION_ID).get();

      if (event.state() == IdleState.READER_IDLE) {
        logger.debug("读空闲超时 - SessionId: {}", sessionId);
        // 长时间没有收到客户端数据，可以发送ping帧检测连接
        ctx.writeAndFlush(new PingWebSocketFrame(
            ctx.alloc().buffer().writeBytes("ping".getBytes(StandardCharsets.UTF_8))));
      } else if (event.state() == IdleState.WRITER_IDLE) {
        logger.debug("写空闲超时 - SessionId: {}", sessionId);
        // 长时间没有向客户端发送数据
      } else if (event.state() == IdleState.ALL_IDLE) {
        logger.warn("全空闲超时 - SessionId: {}, 关闭连接", sessionId);
        // 如果全空闲时间过长，可以考虑关闭连接
        ctx.close();
      }
    } else {
      super.userEventTriggered(ctx, evt);
    }
  }
}
