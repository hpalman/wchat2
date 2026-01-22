package com.wchat.model;

public enum MessageType {
    ENTER,      // 입장
    TALK,       // 일반 대화 (봇 또는 상담사)
    CALL_AGENT, // 상담사 연결 요청
    ACCEPT,     // 상담사 수락
    TO_BOT,     // 봇으로 전환
    NOTICE      // 관리자 공지
}