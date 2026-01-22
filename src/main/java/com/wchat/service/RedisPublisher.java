package com.wchat.service;

import com.wchat.model.ChatMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class RedisPublisher {
    private final RedisTemplate<String, Object> redisTemplate;
    private final ChannelTopic topic;

    public void publish(ChatMessage message) {
        // Redis 토픽으로 메시지 발행 (이후 Subscriber가 받아서 WebSocket으로 전달)
        redisTemplate.convertAndSend(topic.getTopic(), message);
    }
}