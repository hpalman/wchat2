package com.wchat.controller;

import com.wchat.model.ChatMessage;
import com.wchat.model.MessageType;
import com.wchat.service.ChatInactivityService;
import com.wchat.service.ChatStateService;
import com.wchat.service.RedisPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Controller;
import org.springframework.web.client.RestTemplate;

@Slf4j
@RequiredArgsConstructor
@Controller
public class ChatController {

    private final RedisPublisher redisPublisher;
    // Bot 시스템 API 호출을 위한 RestTemplate
    private final RestTemplate restTemplate = new RestTemplate();

    
    private final ChatInactivityService inactivityService;

    private final ChatStateService chatStateService; 
    
    @MessageMapping("/chat/message")
    public void message(ChatMessage message) {
    	
    	/*
    	클라이언트가 보내는 isBotMode 필드를 신뢰하지 않고, 서버가 직접 Redis에서 조회하여 처리하도록 수정합니다. 또한 상담사 수락(ACCEPT)과 종료(TO_BOT) 시점에 Redis 상태를 갱신합니다.
   	
    	*/
    	String roomId = message.getRoomId();
    	// 1. 상담사 연결/종료 타입에 따라 Redis 상태 업데이트 (서버에서만 제어)
        if (message.getType() == MessageType.ACCEPT) {
            chatStateService.setBotMode(roomId, false); // 상담 시작 -> 봇 모드 OFF
        } else if (message.getType() == MessageType.TO_BOT) {
            chatStateService.setBotMode(roomId, true);  // 상담 종료 -> 봇 모드 ON
        }

        // 2. 현재 방의 봇 모드 상태를 Redis에서 가져옴 (클라이언트 입력 무시)
        boolean currentBotMode = chatStateService.isBotMode(roomId);
        message.setBotMode(currentBotMode); // 메시지 객체에 서버 기준 상태 주입

        // 3. DB 저장
        //ChatEntity entity = ChatEntity.builder()
        //        .roomId(roomId)
        //        .sender(message.getSender())
        //        .message(message.getMessage())
        //        .type(message.getType())
        //        .isBotMode(currentBotMode)
        //        .createdAt(LocalDateTime.now())
        //        .build();
        //chatRepository.save(entity);

        // 4. Redis 발행 (실시간 전송)
        redisPublisher.publish(message);

        // 5. 봇 시스템 전달 (서버의 Redis 상태가 true일 때만 실행)
        if (currentBotMode && message.getType() == MessageType.TALK) {
        	log.info("Bot에 전송. message:{} <<<", message);
            sendToBotSystem(message);
        }
        else {
        // 상담사 연결 상태(isBotMode=false)에서 고객이 메시지를 보낸 경우 타이머 리셋
        //if (!message.isBotMode() && message.getType() == MessageType.TALK) {
        //    inactivityService.resetInactivityTimer(message.getRoomId());
        //}
        }
    }

    private void sendToBotSystem(ChatMessage message) {
    	
        // 1. Redis에 메시지 발행 (Pub/Sub)
		// 1. 모든 메시지는 먼저 Redis에 발행하여 실시간으로 화면에 표시되게 함
        //redisPublisher.publish(message);

        // 상담사 연결 상태(isBotMode=false)에서 고객이 메시지를 보낸 경우 타이머 리셋
        //if (!message.isBotMode() && message.getType() == MessageType.TALK) {
        //    inactivityService.resetInactivityTimer(message.getRoomId());
        //}
        
        // 2. 봇 모드이고 일반 대화일 경우 외부 봇 API 호출
		// 2. 봇 모드(isBotMode=true)이면서 일반 대화(TALK)인 경우에만 Bot API 호출
        //if (message.isBotMode() && message.getType() == MessageType.TALK) {
            log.info("Bot 시스템으로 문의 전달: {}", message.getMessage());
            
            try {
                // [요구사항] 타 서버의 Bot 시스템 API 호출 (비동기 처리 가정)
                // 실제 Bot 서버 주소로 변경 필요
                String botApiUrl = "http://localhost:3000/api/bot/ask"; 
                botApiUrl = "http://localhost:3000/api/ask";
                
                // Bot 시스템에 고객 메시지 전달 (비동기 호출을 위해 별도 스레드나 큐 사용 권장)
                // 여기서는 개념 증명을 위해 post 전송 예시만 작성합니다.
                restTemplate.postForEntity(botApiUrl, message, String.class);
                
                log.info("Bot 시스템에 성공적으로 전달되었습니다.");
            } catch (Exception e) {
                log.error("Bot API 호출 실패: {}", e.getMessage());
            }
            // 외부 봇 서버에 비동기 요청 (예시 경로)
            // restTemplate.postForEntity("http://bot-server/api/ask", message, String.class);
            // System.out.println("Bot API 호출됨: " + message.getMessage());
        //}
    	
    }
    
    // 관리자 공지용 (API 예시)
    @MessageMapping("/chat/notice")
    public void notice(ChatMessage message) {
        message.setType(MessageType.NOTICE);
        message.setRoomId("ALL"); // 전체 공지용 가상 룸
        redisPublisher.publish(message);
    }
}