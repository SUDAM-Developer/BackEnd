package com.sudam.domain.share.dto.response;

/**
 * 카카오톡 공유 창에 넣을 정보. 프론트가 카카오 공유 템플릿(피드)에 그대로 넣는다.
 */
public record ShareResponse(

        Long shareId, // 공유 기록 ID (카카오 serverCallbackArgs의 발송 ID)

        String url, // 받는 사람의 링크 화면 URL

        String title, // 메시지 제목

        String description, // 메시지 설명

        String buttonTitle // 버튼 문구
) {}
