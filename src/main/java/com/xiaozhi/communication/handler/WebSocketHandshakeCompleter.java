package com.xiaozhi.communication.handler;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * WebSocket 握手完成事件处理器
 * 在 Netty 完成 WebSocket 握手后，从此事件中提取设备信息并设置到 Channel 属性中
 */
@Component
@ChannelHandler.Sharable // 这个 Handler 是无状态的，可以被共享
public class WebSocketHandshakeCompleter extends ChannelInboundHandlerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketHandshakeCompleter.class);

    // 将 AttributeKey 统一定义在这里或一个专门的常量类中
    public static final AttributeKey<String> SESSION_ID = AttributeKey.valueOf("sessionId");
    public static final AttributeKey<String> DEVICE_ID = AttributeKey.valueOf("deviceId");
    // 如果你采纳了之前的建议，把会话状态也放在这里
    public static final AttributeKey<Object> DEVICE_CONFIG = AttributeKey.valueOf("deviceConfig");
    public static final AttributeKey<Boolean> LISTENING_STATE = AttributeKey.valueOf("listeningState");


    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            // 握手成功事件
            WebSocketServerProtocolHandler.HandshakeComplete event = (WebSocketServerProtocolHandler.HandshakeComplete) evt;

            // ***【修正点】***
            // 从事件中获取请求的 URI，而不是整个 request 对象
            String requestUri = event.requestUri();

            try {
                // 使用获取到的 URI 来解析参数
                QueryStringDecoder decoder = new QueryStringDecoder(requestUri);
                Map<String, List<String>> params = decoder.parameters();

                // 安全地获取 device-id 参数
                String deviceId = params.getOrDefault("device-id", List.of())
                        .stream()
                        .findFirst()
                        .orElse(null);

                if (deviceId == null || deviceId.isEmpty()) {
                    logger.warn("WebSocket连接缺少device-id参数，握手已完成但连接无效。关闭连接。URI: {}", requestUri);
                    ctx.close();
                    return;
                }

                // 生成会话ID并存储
                String sessionId = ctx.channel().id().asShortText();
                ctx.channel().attr(SESSION_ID).set(sessionId);
                ctx.channel().attr(DEVICE_ID).set(deviceId);

                logger.info("WebSocket 握手成功 - SessionId: {}, DeviceId: {}", sessionId, deviceId);

            } catch (Exception e) {
                logger.error("处理WebSocket握手完成事件时发生错误", e);
                ctx.close();
            }
        } else {
            // 传递其他所有事件
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("WebSocketHandshakeCompleter 发生异常", cause);
        ctx.close();
    }
}
