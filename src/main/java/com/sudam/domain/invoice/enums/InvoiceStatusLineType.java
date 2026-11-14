package com.sudam.domain.invoice.enums;

// 수입 탭 교육비 카드 아래 한 줄 안내. 저장하지 않고 청구 상태 · 시각 · 공유 설정으로 계산한다.
public enum InvoiceStatusLineType {
    PARENT_CONFIRMED, // 학부모가 [입금했어요]를 누름 (at = 요청 시각)
    PARTIAL_PAID, // 일부 입금 (at = 마지막 입금 확인 시각)
    RESENT, // 카톡으로 입금 안내를 다시 보냄 (at = 다시 보낸 시각)
    REMINDER_SCHEDULED, // 미입금 알림 예정 (공유 설정에서 켬, 아직 보내기 전)
    NOT_SENT, // 교육비 안내를 아직 보내지 않음
    NONE // 표시할 안내 없음 (입금 완료 포함)
}
