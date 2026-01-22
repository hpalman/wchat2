package com.wchat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/*
상담사가 연결될 때만 isBotMode가 변경되도록 하고, 이 상태를 서버의 메모리가 아닌 Redis에 보관하여 관리하는 방식으로 수정해 드리겠습니다.

이렇게 하면 화면(클라이언트)에서 isBotMode를 조작할 수 없어 보안이 강화되며, 서버가 여러 대인 분산 환경에서도 상담 상태가 안전하게 공유됩니다.

1. Redis 상태 관리 서비스 생성
Redis에 특정 방(Room)의 봇 모드 여부를 저장하고 확인하는 서비스를 만듭니다.
 */
@Service
@RequiredArgsConstructor
public class ChatStateService {

    private final StringRedisTemplate redisTemplate;
    private static final String KEY_PREFIX = "chat:botmode:";

    // 봇 모드 여부 확인 (기본값은 true)
    public boolean isBotMode(String roomId) {
        String value = redisTemplate.opsForValue().get(KEY_PREFIX + roomId);
        // Redis에 값이 없거나 "true"인 경우 봇 모드로 간주
        return value == null || "true".equals(value);
    }

    // 상태 변경 (상담사 연결 시 false, 종료 시 true)
    public void setBotMode(String roomId, boolean isBotMode) {
        redisTemplate.opsForValue().set(
                KEY_PREFIX + roomId, 
                String.valueOf(isBotMode), 
                24, TimeUnit.HOURS // 24시간 후 자동 삭제 (세션 관리용)
        );
    }
}
