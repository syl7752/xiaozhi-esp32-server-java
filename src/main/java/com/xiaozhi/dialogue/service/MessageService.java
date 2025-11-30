package com.xiaozhi.dialogue.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xiaozhi.communication.common.ChatSession;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

import static com.xiaozhi.communication.handler.WebSocketHandshakeHandler.SESSION_ID;

@Service("messageService")
public class MessageService {

    private static final Logger logger = LoggerFactory.getLogger(MessageService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 发送格式消息给指定会话
     *
     * @param session WebSocket会话
     * @param text    消息文本内容
     * @return 表示操作完成的CompletableFuture
     */
    public void sendTtsMessage(ChatSession session, String text, String state) {
        if (session == null || !session.isOpen()) {
            logger.warn("sendTtsMessage无法发送消息 - 会话已关闭或为null");
        }
        ObjectNode messageJson = objectMapper.createObjectNode();
        messageJson.put("type", "tts");
        messageJson.put("state", state);
        if(text != null){
            messageJson.put("text", text);
        }

        String jsonMessage = messageJson.toString();
        logger.info("sendTtsMessage发送消息 - SessionId: {}, Message: {}", session.getSessionId(), jsonMessage);
        sendTextMessage(session, jsonMessage);
    }

    /**
     * 发送格式消息给指定会话
     * 
     * @param session WebSocket会话
     * @param text    消息文本内容
     * @return 表示操作完成的CompletableFuture
     */
    public void sendSttMessage(ChatSession session, String text) {
        if (session == null || !session.isOpen()) {
            logger.warn("sendSttMessage无法发送消息 - 会话已关闭或为null");
        }
        ObjectNode messageJson = objectMapper.createObjectNode();
        messageJson.put("type", "stt");
        messageJson.put("text", text);

        String jsonMessage = messageJson.toString();
        logger.info("sendSttMessage发送消息 - SessionId: {}, Message: {}", session.getSessionId(), jsonMessage);
        sendTextMessage(session, jsonMessage);
    }

    /**
     * 发送IoT命令消息给指定会话
     * 
     * @param session  WebSocket会话
     * @param commands 命令列表
     * @return 表示操作完成的CompletableFuture
     */
    public void sendIotCommandMessage(ChatSession session, List<Map<String, Object>> commands) {
        if (session == null || !session.isOpen()) {
            logger.warn("sendIotCommandMessage无法发送消息 - 会话已关闭或为null");
        }
        ObjectNode messageJson = objectMapper.createObjectNode();
        messageJson.put("session_id", session.getSessionId());
        messageJson.put("type", "iot");
        messageJson.set("commands", objectMapper.valueToTree(commands));

        String jsonMessage = messageJson.toString();
        logger.debug("sendIotCommandMessage发送iot消息 - SessionId: {}, Message: {}", session.getSessionId(), messageJson);
        sendTextMessage(session, jsonMessage);
    }

    /**
     * 向设备发送表情符号
     *
     * @param session WebSocket会话
     * @param emotion 表情内容
     * @return 表示操作完成的CompletableFuture
     */
    public void sendEmotion(ChatSession session, String emotion) {
        if (session == null || !session.isOpen()) {
            logger.warn("sendEmotion无法发送消息 - 会话已关闭或为null");
        }
        ObjectNode messageJson = objectMapper.createObjectNode();
        messageJson.put("session_id", session.getSessionId());
        messageJson.put("type", "llm");
        messageJson.put("emotion", emotion);
        messageJson.put("text", emotion);
        String jsonMessage = messageJson.toString();
        logger.info("sendEmotion发送Emotion消息 - SessionId: {}, Message: {}", session.getSessionId(), jsonMessage);
        sendTextMessage(session, jsonMessage);
    }

    public void sendTextMessage(ChatSession chatSession, String message){
        try {
            // 检查会话是否仍然有效
            if (chatSession == null || !chatSession.isOpen()) {
                return;
            }
            chatSession.sendTextMessage(message);
        } catch (Exception e) {
            logger.error("发送消息时发生异常 - SessionId: {}, Error: {}", chatSession.getSessionId(), e.getMessage());
            throw new RuntimeException("发送消息失败, 消息内容: " + message, e);
        }
    }


    public void sendBinaryMessage(ChatSession chatSession, byte[] opusFrame){
        try {
            // 检查会话是否仍然有效
            if (chatSession == null || !chatSession.isOpen()) {
                return;
            }
            chatSession.sendBinaryMessage(opusFrame);
        } catch (Exception e) {
            logger.error("发送消息时发生异常 - SessionId: {}, Error: {}", chatSession.getSessionId(), e.getMessage());
            throw new RuntimeException("发送音频消息失败, 消息内容", e);
        }
    }

    /**
     * 发送简单状态消息
     *
     * @param channel Netty 通道
     * @param type    消息类型
     * @param state   消息状态
     */
    public void sendMessage(Channel channel, String type, String state) {
        if (channel == null || !channel.isActive()) {
            logger.warn("无法发送消息 - 通道不活跃或为null");
            return;
        }

        try {
            String sessionId = channel.attr(SESSION_ID).get();
            ObjectNode response = objectMapper.createObjectNode();
            response.put("session_id", sessionId);
            response.put("type", type);
            response.put("state", state);

            channel.writeAndFlush(new TextWebSocketFrame(response.toString()));
            logger.info("发送消息 - SessionId: {}, Type: {}, State: {}", sessionId, type, state);
        } catch (Exception e) {
            logger.error("发送消息时发生异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 发送带文本内容的消息
     *
     * @param channel Netty 通道
     * @param type    消息类型
     * @param state   消息状态
     * @param text    文本内容
     */
    public void sendMessage(Channel channel, String type, String state, String text) {
        if (channel == null || !channel.isActive()) {
            logger.warn("无法发送消息 - 通道不活跃或为null");
            return;
        }

        try {
            String sessionId = channel.attr(SESSION_ID).get();
            ObjectNode response = objectMapper.createObjectNode();
            response.put("session_id", sessionId);
            response.put("type", type);
            response.put("state", state);
            response.put("text", text);

            channel.writeAndFlush(new TextWebSocketFrame(response.toString()));
            logger.info("发送消息 - SessionId: {}, Type: {}, State: {}, Text: {}",
                    sessionId, type, state, text);
        } catch (Exception e) {
            logger.error("发送消息时发生异常: {}", e.getMessage(), e);
        }
    }

}