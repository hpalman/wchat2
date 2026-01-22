package com.wchat.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.wchat.model.ChatMessage;
import com.wchat.model.MessageType;
import com.wchat.service.RedisPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	private final RedisPublisher redisPublisher; // 봇 메시지 발행을 위해 주입
	
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 메시지 구독 요청 prefix: /sub
        config.enableSimpleBroker("/sub");
        // 메시지 발행 요청 prefix: /pub
        config.setApplicationDestinationPrefixes("/pub");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 클라이언트에서 접속할 엔드포인트: /ws-chat
        registry.addEndpoint("/ws-chat")
                .setAllowedOriginPatterns("*")
                .withSockJS(); // SockJS 지원
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

                // [추가 기능] 고객이 자신의 개인 채널을 구독하기 시작할 때 봇 인사말 전송
                if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    String destination = accessor.getDestination(); // 예: /sub/chat/room/user1
                    
                    if (destination != null && destination.startsWith("/sub/chat/room/")) {
                        String roomId = destination.replace("/sub/chat/room/", "");
                        
                        // "ALL" 공지 채널이 아닌 실제 사용자 룸인 경우에만 인사말 전송
                        if (!"ALL".equals(roomId)) {
                            sendWelcomeMessage(roomId);
                        }
                    }
                }
                return message;
            }
        });
    }

    // 봇 환영 메시지를 Redis를 통해 전송
    private void sendWelcomeMessage(String roomId) {
        ChatMessage welcomeMsg = ChatMessage.builder()
                .type(MessageType.TALK)
                .roomId(roomId)
                .sender("AI 봇")
                .message("안녕하세요? 봇입니다. 무엇을 도와드릴까요?")
                .isBotMode(true)
                .build();
        
        // 약간의 지연을 주어 클라이언트가 구독 완료를 처리할 시간을 줍니다.
        new Thread(() -> {
            try {
                Thread.sleep(500); // 0.5초 대기
                redisPublisher.publish(welcomeMsg);
            } catch (InterruptedException e) {
                log.error("Welcome message delay error", e);
            }
        }).start();
    }    
    
    // 클라이언트로 나가는 채널(Outbound) 설정에 인터셉터 추가
    /*
	  STOMP 클라이언트(stomp.js)에서 frame.headers.server 값이 undefined로 나오는 이유는, 서버가 연결 확정 응답(CONNECTED 프레임)을 보낼 때 server 헤더를 포함하지 않기 때문입니다.
      Spring Boot의 메시징 인프라에서 이 헤더를 직접 제어하려면 **ChannelInterceptor**를 사용하여 연결 응답 프레임에 헤더를 강제로 주입해야 합니다.
      
      a["CONNECTED\nversion:1.1\nheart-beat:0,0\n\n\u0000"]
      
    */	
    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
        	@Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                // StompHeaderAccessor.wrap을 사용하여 안전하게 접근합니다.
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
                if (accessor != null ) {
                	// 모든 Native Header (클라이언트로 실제 전송되는 헤더) 출력
                	log.info("All Native Headers: {}", accessor.toNativeHeaderMap());

                	// Spring 메시지 내부의 모든 Header (메타데이터 포함) 출력
                	log.info("All Message Headers: {}", accessor.getMessageHeaders());
                	
                	// [로그 출력 부분]
                    // getDetailedLogMessage는 헤더와 페이로드 요약을 포함한 상세 정보를 제공합니다.
                    log.info("Outbound Message Details: {}", accessor.getDetailedLogMessage(message.getPayload()));                	
                }
                // accessor가 null이 아니고, STOMP 명령이 CONNECTED인 경우에만 헤더 주입
                if (accessor != null && StompCommand.CONNECTED.equals(accessor.getCommand())) {
                    accessor.setNativeHeader("server", "WCHAT-Server-v1.0");
                    
                    // 수정한 헤더를 반영하기 위해 메시지를 다시 생성할 필요는 없으나, 
                    // 변경된 설정값이 담긴 accessor를 사용하여 헤더를 갱신할 수도 있습니다.
                    // accessor.setImmutable(); // 선택사항
                }
                return message;
            }
        });
    }
}