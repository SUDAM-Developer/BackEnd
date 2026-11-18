package com.sudam.domain.participant.service;

import com.sudam.domain.participant.entity.Participant;
import com.sudam.domain.participant.enums.ParticipantLabel;
import com.sudam.domain.participant.exception.ParticipantException;
import com.sudam.domain.participant.repository.ParticipantRepository;
import com.sudam.domain.tutoring.entity.Tutoring;
import com.sudam.domain.user.enums.Subject;
import com.sudam.global.exception.ProjectException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("ParticipantLinkService 링크 토큰으로 학부모 찾기")
class ParticipantLinkServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 18, 21, 0);
    private static final String TOKEN = "link-token";

    @Mock
    private ParticipantRepository participantRepository;

    private ParticipantLinkService participantLinkService;
    private Tutoring tutoring;

    @BeforeEach
    void setUp() {
        participantLinkService = new ParticipantLinkService(participantRepository,
                Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL));
        tutoring = Tutoring.builder().subject(Subject.ENGLISH).studentName("윤서아")
                .gradeLevel(9).gradeSchoolYear(2026).build();
    }

    @Test
    @DisplayName("진행 중 과외(만료 없음)의 학부모 토큰이면 그 학부모를 돌려준다")
    void 유효한_학부모_링크() {
        // given
        Participant parent = parent();
        given(participantRepository.findWithTutoringByLinkToken(TOKEN)).willReturn(Optional.of(parent));

        // when & then
        assertThat(participantLinkService.resolveParent(TOKEN)).isSameAs(parent);
    }

    @Test
    @DisplayName("만료 시각 전이면 열린다")
    void 만료_전() {
        // given
        Participant parent = parent();
        ReflectionTestUtils.setField(parent, "linkExpiresAt", NOW.plusMinutes(1));
        given(participantRepository.findWithTutoringByLinkToken(TOKEN)).willReturn(Optional.of(parent));

        // when & then
        assertThat(participantLinkService.resolveParent(TOKEN)).isSameAs(parent);
    }

    @Test
    @DisplayName("없는 토큰이면 PARTICIPANT404_1")
    void 없는_토큰() {
        // given
        given(participantRepository.findWithTutoringByLinkToken(TOKEN)).willReturn(Optional.empty());

        // when & then
        assertLinkNotFound();
    }

    @Test
    @DisplayName("만료 시각이 지났으면 PARTICIPANT404_1")
    void 만료된_링크() {
        // given
        Participant parent = parent();
        ReflectionTestUtils.setField(parent, "linkExpiresAt", NOW);
        given(participantRepository.findWithTutoringByLinkToken(TOKEN)).willReturn(Optional.of(parent));

        // when & then
        assertLinkNotFound();
    }

    @Test
    @DisplayName("삭제된 참여자면 PARTICIPANT404_1")
    void 삭제된_참여자() {
        // given
        Participant parent = parent();
        ReflectionTestUtils.setField(parent, "removedAt", NOW.minusDays(1));
        given(participantRepository.findWithTutoringByLinkToken(TOKEN)).willReturn(Optional.of(parent));

        // when & then
        assertLinkNotFound();
    }

    @Test
    @DisplayName("삭제된 과외의 링크면 PARTICIPANT404_1")
    void 삭제된_과외() {
        // given
        Participant parent = parent();
        ReflectionTestUtils.setField(tutoring, "deletedAt", NOW.minusDays(1));
        given(participantRepository.findWithTutoringByLinkToken(TOKEN)).willReturn(Optional.of(parent));

        // when & then
        assertLinkNotFound();
    }

    @Test
    @DisplayName("학생 링크면 PARTICIPANT404_1 (리포트는 학부모에게 보낸다)")
    void 학생_링크() {
        // given
        Participant student = Participant.createStudent(tutoring, null);
        student.issueLinkToken(TOKEN, NOW);
        given(participantRepository.findWithTutoringByLinkToken(TOKEN)).willReturn(Optional.of(student));

        // when & then
        assertLinkNotFound();
    }

    private Participant parent() {
        Participant parent = Participant.createParent(tutoring, ParticipantLabel.MOTHER, "01000000002");
        parent.issueLinkToken(TOKEN, NOW.minusDays(30));
        return parent;
    }

    private void assertLinkNotFound() {
        assertThatThrownBy(() -> participantLinkService.resolveParent(TOKEN))
                .isInstanceOf(ParticipantException.class)
                .extracting(e -> ((ProjectException) e).getErrorCode().getCode())
                .isEqualTo("PARTICIPANT404_1");
    }
}
