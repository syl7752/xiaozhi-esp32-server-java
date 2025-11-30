package com.xiaozhi.communication.handler;

import cn.hutool.extra.spring.SpringUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaozhi.dialogue.service.AudioService;
import com.xiaozhi.dialogue.service.MessageService;
import com.xiaozhi.dialogue.service.VadService;
import com.xiaozhi.entity.SysConfig;
import com.xiaozhi.entity.SysDevice;
import com.xiaozhi.service.SysConfigService;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import static com.xiaozhi.communication.handler.WebSocketHandshakeCompleter.SESSION_ID;


/**
 * WebSocket 二进制消息处理器
 * 处理所有二进制类型的 WebSocket 消息，主要是音频数据
 */
@Component
public class BinaryWebSocketFrameHandler extends SimpleChannelInboundHandler<BinaryWebSocketFrame> {

  private static final Logger logger = LoggerFactory.getLogger(BinaryWebSocketFrameHandler.class);

  private final VadService vadService = SpringUtil.getBean(VadService.class);

  private final AudioService audioService = SpringUtil.getBean(AudioService.class);

  private final MessageService messageService = SpringUtil.getBean(MessageService.class);

  private final SysConfigService configService = SpringUtil.getBean(SysConfigService.class);

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, BinaryWebSocketFrame frame) {
    String sessionId = ctx.channel().attr(SESSION_ID).get();

    // 检查会话是否处于监听状态，如果不是则忽略音频数据
    if (!TextWebSocketFrameHandler.isListening(sessionId)) {
      return;
    }

    SysDevice device = TextWebSocketFrameHandler.getDeviceConfig(sessionId);
    if (device == null) {
      logger.warn("收到二进制消息但设备未初始化 - SessionId: {}", sessionId);
      return;
    }

    // 获取设备配置
    SysConfig sttConfig = null;
    SysConfig ttsConfig = null;

    if (device.getSttId() != null) {
      sttConfig = configService.selectConfigById(device.getSttId());
    }

    if (device.getTtsId() != null) {
      ttsConfig = configService.selectConfigById(device.getTtsId());
    }

    ByteBuf content = frame.content().retain();  // 显式 retain
    try {
      byte[] opusData = new byte[content.readableBytes()];
      content.readBytes(opusData);
      // 处理音频数据
      processAudioData(ctx, sessionId, device, sttConfig, ttsConfig, opusData);
    } catch (Exception e) {
      logger.error("处理二进制消息失败", e);
    }finally {
      content.release();  // 确保释放
    }
  }

  /**
   * 处理音频数据
   */
  private void processAudioData(ChannelHandlerContext ctx, String sessionId, SysDevice device,
      SysConfig sttConfig, SysConfig ttsConfig, byte[] opusData) throws Exception {
    VadService.VadResult result = vadService.processAudio(sessionId, opusData);

    if (result != null) {
      logger.info("检测到语音结束 - SessionId: {}, 音频大小: {} 字节", sessionId, result.getProcessedData().length);
      // 调用 SpeechToTextService 进行语音识别
        messageService.sendMessage(ctx.channel(), "stt", "start", " ");


//        // 使用句子切分处理流式响应
//        llmManager.chatStreamBySentence(device, result, (sentence, isStart, isEnd) -> {
//          try {
//            String audioPath = textToSpeechService.textToSpeech(sentence, ttsConfig,
//                device.getVoiceName());
//            audioService.sendAudio(ctx.channel(), audioPath, sentence, isStart, isEnd);
//          } catch (Exception e) {
//            logger.error("处理句子失败: {}", e.getMessage(), e);
//          }
//        });
      }
    }
}
