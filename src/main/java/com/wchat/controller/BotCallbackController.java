package com.wchat.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wchat.model.ChatMessage;
import com.wchat.model.MessageType;
import com.wchat.service.RedisPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/bot")
public class BotCallbackController {

    private final RedisPublisher redisPublisher;

    /**
     * 타 서버(봇)에서 처리 완료 후 호출하는 API
     * 외부 Bot 시스템이 응답을 보낼 때 호출하는 API
     * POST /api/bot/callback
     */
    @PostMapping("/callback")
    public void botCallback(@RequestBody ChatMessage botResponse) {
    	log.info("AI BOT이 callback을 호출하였어요! ChatMessage:{}<<<", botResponse);
        // Bot 응답임을 명시
        botResponse.setSender("AI BOT");
        botResponse.setType(MessageType.TALK);
        botResponse.setBotMode(true);
        // Redis를 통해 고객에게 메시지 전달
        // Redis를 통해 해당 고객의 roomId로 메시지 발행
        // RedisSubscriber가 이를 받아 WebSocket으로 전달함
        redisPublisher.publish(botResponse);
    }
}