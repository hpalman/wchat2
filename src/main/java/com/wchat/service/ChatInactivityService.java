package com.wchat.service;

import com.wchat.model.ChatMessage;
import com.wchat.model.MessageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant; // Date 대신 Instant 사용
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatInactivityService {
    private final RedisPublisher redisPublisher;
    private final ThreadPoolTaskScheduler taskScheduler;
    
    // 각 채팅방별 실행 예정인 작업 저장 (Thread-safe)
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    
    // 타임아웃 설정 (5분)
    private static final long INACTIVITY_TIMEOUT_MS = 5 * 60 * 1000;

    /**
     * 고객 활동 감지 시 타이머 리셋
     */
    public void resetInactivityTimer(String roomId) {
        // 1. 기존에 예약된 타이머가 있다면 취소
        ScheduledFuture<?> existingTask = scheduledTasks.get(roomId);
        if (existingTask != null && !existingTask.isDone()) {
            existingTask.cancel(false);
        }

        // 2. 새로운 타이머 예약 (Spring 6+ 권장 방식: Instant 사용)
        // schedule(Runnable task, Instant startTime) 메서드 호출
        Instant startTime = Instant.now().plusMillis(INACTIVITY_TIMEOUT_MS);
        
        ScheduledFuture<?> newTask = taskScheduler.schedule(
            () -> switchToBotMode(roomId), 
            startTime
        );
        
        scheduledTasks.put(roomId, newTask);
        log.debug("Chatroom [{}] 타이머 리셋: {} 후에 실행 예정", roomId, startTime);
    }

    /**
     * 타임아웃 발생 시 봇 모드로 전환 처리
     */
    private void switchToBotMode(String roomId) {
        log.info("Chatroom [{}] 부재 감지로 인한 봇 전환 실행", roomId);
        
        ChatMessage timeoutMsg = ChatMessage.builder()
                .type(MessageType.TO_BOT)
                .roomId(roomId)
                .sender("시스템")
                .message("고객님의 응답이 장시간 없어 상담을 종료하고 봇 모드로 전환합니다.")
                .isBotMode(true)
                .build();

        // Redis를 통해 모든 서버 및 클라이언트에 전파
        redisPublisher.publish(timeoutMsg);
        
        // 작업 완료 후 맵에서 제거
        scheduledTasks.remove(roomId);
    }
}