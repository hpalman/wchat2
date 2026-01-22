package com.wchat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wchat.model.ChatMessage;
import com.wchat.model.MessageType; // 명확하게 임포트
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class RedisSubscriber {
    private final ObjectMapper objectMapper;
    private final SimpMessageSendingOperations messagingTemplate;

    public void sendMessage(String publishMessage) {
        try {
            // Redis에서 받은 JSON 문자열을 ChatMessage 객체로 변환
            ChatMessage chatMessage = objectMapper.readValue(publishMessage, ChatMessage.class);
            
            // 1. 해당 채팅방(고객 ID 기반)으로 메시지 전송
            messagingTemplate.convertAndSend("/sub/chat/room/" + chatMessage.getRoomId(), chatMessage);
            
            // 2. 상담사 연결 요청(CALL_AGENT)인 경우 상담사 전용 공통 채널로 별도 전송
            if (chatMessage.getType() == MessageType.CALL_AGENT) {
                log.info("상담사 연결 요청 수신: {}", chatMessage.getSender());
                messagingTemplate.convertAndSend("/sub/chat/agents", chatMessage);
            }
        } catch (Exception e) {
            log.error("메시지 파싱 에러: {}", e.getMessage());
        }
    }
}