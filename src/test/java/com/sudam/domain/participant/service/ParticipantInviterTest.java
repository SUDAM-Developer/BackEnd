package com.sudam.domain.participant.service;

import com.sudam.domain.participant.entity.Participant;
import com.sudam.domain.participant.enums.ParticipantLabel;
import com.sudam.domain.participant.enums.ParticipantRole;
import com.sudam.domain.participant.repository.ParticipantRepository;
import com.sudam.domain.tutoring.entity.Tutoring;
import com.sudam.domain.user.entity.TeacherProfile;
import com.sudam.domain.user.entity.User;
import com.sudam.domain.user.enums.Provider;
import com.sudam.domain.user.enums.Subject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("ParticipantInviter")
class ParticipantInviterTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 26, 18, 0);
    private static final TeacherProfile TEACHER = TeacherProfile.create(
            User.createTeacher(Provider.KAKAO, "kakao-1", "teacher@sudam.app"), "한서윤", List.of(Subject.MATH), null);

    @Mock
    private ParticipantRepository participantRepository;

    // 토큰 생성기는 순수 컴포넌트라 실제 구현을 쓴다. 256비트 난수라 서로 다름 검증이 사실상 결정적이다.
    private ParticipantInviter participantInviter;

    private Tutoring tutoring;

    @BeforeEach
    void setUp() {
        participantInviter = new ParticipantInviter(participantRepository, new LinkTokenGenerator());
        tutoring = Tutoring.builder()
                .teacher(TEACHER)
                .subjects(List.of(Subject.MATH))
                .studentName("김서아")
                .gradeLevel(9)
                .gradeSchoolYear(2026)
                .build();
        given(participantRepository.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("학부모 2명과 학생을 학부모 · 학생 순서로 만들고 토큰을 모두 발급한다")
    void 학부모_둘과_학생을_초대한다() {
        // given
        List<ParentContact> parents = List.of(
                new ParentContact(ParticipantLabel.MOTHER, "01011112222"),
                new ParentContact(ParticipantLabel.FATHER, "01033334444"));

        // when
        List<Participant> result = participantInviter.invite(tutoring, parents, "01055556666", NOW);

        // then
        assertThat(result).extracting(Participant::getRole)
                .containsExactly(ParticipantRole.PARENT, ParticipantRole.PARENT, ParticipantRole.STUDENT);
        assertThat(result).extracting(Participant::getPhone)
                .containsExactly("01011112222", "01033334444", "01055556666");
        assertThat(result).extracting(Participant::getLinkToken).doesNotContainNull().doesNotHaveDuplicates();
        assertThat(result).extracting(Participant::getLinkIssuedAt).containsOnly(NOW);
    }

    @Test
    @DisplayName("라벨이 없는 학부모는 GUARDIAN이 되고 학생 번호가 없으면 학부모만 만든다")
    void 라벨_없으면_보호자_학생_번호_없으면_학부모만() {
        // given
        List<ParentContact> parents = List.of(new ParentContact(null, "01011112222"));

        // when
        List<Participant> result = participantInviter.invite(tutoring, parents, null, NOW);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLabel()).isEqualTo(ParticipantLabel.GUARDIAN);
        assertThat(result.get(0).getLinkToken()).isNotNull();
    }
}
