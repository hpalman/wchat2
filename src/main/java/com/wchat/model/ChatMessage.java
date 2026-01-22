package com.wchat.model;

import lombok.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {
    private MessageType type;
    private String roomId;    // 여기서는 사용자 ID를 기반으로 생성된 채팅방 ID
    private String sender;    // 발신자 ID
    private String receiver;  // 수신자 ID (상담사 또는 고객)
    private String message;
    private boolean isBotMode; // 현재 봇 응대 모드 여부
}