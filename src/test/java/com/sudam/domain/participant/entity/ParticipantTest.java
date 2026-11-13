package com.sudam.domain.participant.entity;

import com.sudam.domain.participant.enums.NotificationConsent;
import com.sudam.domain.participant.enums.ParticipantLabel;
import com.sudam.domain.participant.enums.ParticipantRole;
import com.sudam.domain.participant.enums.ReceiveMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Participant 엔티티 상태 변경")
class ParticipantTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 13, 10, 0);

    @Test
    @DisplayName("호칭을 고르지 않은 학부모는 보호자님으로 생성되고 링크 수신 · 동의 대기 상태다")
    void 호칭이_없으면_보호자님으로_생성된다() {
        // when
        Participant parent = Participant.createParent(null, null, "01012345678");

        // then
        assertThat(parent.getRole()).isEqualTo(ParticipantRole.PARENT);
        assertThat(parent.getLabel()).isEqualTo(ParticipantLabel.GUARDIAN);
        assertThat(parent.getReceiveMethod()).isEqualTo(ReceiveMethod.LINK);
        assertThat(parent.getNotificationConsent()).isEqualTo(NotificationConsent.PENDING);
    }

    @Test
    @DisplayName("학생은 호칭 없이 생성된다")
    void 학생은_호칭이_없다() {
        // when
        Participant student = Participant.createStudent(null, null);

        // then
        assertThat(student.getRole()).isEqualTo(ParticipantRole.STUDENT);
        assertThat(student.getLabel()).isNull();
    }

    @Test
    @DisplayName("링크 토큰을 재발급하면 이전 토큰을 덮어쓰고 발급 시각이 바뀐다")
    void 재발급하면_토큰을_덮어쓴다() {
        // given
        Participant parent = Participant.createParent(null, ParticipantLabel.MOTHER, "01012345678");
        parent.issueLinkToken("old-token", NOW);

        // when
        parent.issueLinkToken("new-token", NOW.plusDays(1));

        // then
        assertThat(parent.getLinkToken()).isEqualTo("new-token");
        assertThat(parent.getLinkIssuedAt()).isEqualTo(NOW.plusDays(1));
    }

    @Test
    @DisplayName("링크 토큰을 폐기하면 토큰이 비워진다")
    void 폐기하면_토큰이_비워진다() {
        // given
        Participant parent = Participant.createParent(null, ParticipantLabel.MOTHER, "01012345678");
        parent.issueLinkToken("token", NOW);

        // when
        parent.revokeLinkToken();

        // then
        assertThat(parent.getLinkToken()).isNull();
    }

    @Test
    @DisplayName("참여자를 삭제하면 삭제 시각이 기록되고 링크 토큰과 연락처가 파기된다")
    void 삭제하면_토큰과_연락처가_파기된다() {
        // given
        Participant parent = Participant.createParent(null, ParticipantLabel.FATHER, "01012345678");
        parent.issueLinkToken("token", NOW);

        // when
        parent.remove(NOW.plusDays(3));

        // then
        assertThat(parent.getRemovedAt()).isEqualTo(NOW.plusDays(3));
        assertThat(parent.getLinkToken()).isNull();
        assertThat(parent.getPhone()).isNull();
    }
}
