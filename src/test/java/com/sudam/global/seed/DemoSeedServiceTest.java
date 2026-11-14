package com.sudam.global.seed;

import com.sudam.domain.cycle.repository.CycleRepository;
import com.sudam.domain.invoice.repository.InvoiceRepository;
import com.sudam.domain.participant.repository.ParticipantRepository;
import com.sudam.domain.record.repository.SessionProgressRepository;
import com.sudam.domain.record.repository.SessionRecordRepository;
import com.sudam.domain.session.repository.SessionRepository;
import com.sudam.domain.tutoring.repository.TextbookRepository;
import com.sudam.domain.tutoring.repository.TutoringPolicyRepository;
import com.sudam.domain.tutoring.repository.TutoringRepository;
import com.sudam.domain.tutoring.repository.TutoringScheduleRepository;
import com.sudam.domain.tutoring.repository.TutoringSettingRepository;
import com.sudam.domain.user.entity.User;
import com.sudam.domain.user.enums.Provider;
import com.sudam.domain.user.repository.TeacherProfileRepository;
import com.sudam.domain.user.repository.TermsRepository;
import com.sudam.domain.user.repository.UserRepository;
import com.sudam.domain.user.repository.UserTermsAgreementRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class DemoSeedServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private TeacherProfileRepository teacherProfileRepository;
    @Mock private TermsRepository termsRepository;
    @Mock private UserTermsAgreementRepository userTermsAgreementRepository;
    @Mock private TutoringRepository tutoringRepository;
    @Mock private TutoringScheduleRepository tutoringScheduleRepository;
    @Mock private TextbookRepository textbookRepository;
    @Mock private TutoringPolicyRepository tutoringPolicyRepository;
    @Mock private TutoringSettingRepository tutoringSettingRepository;
    @Mock private ParticipantRepository participantRepository;
    @Mock private CycleRepository cycleRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private SessionRepository sessionRepository;
    @Mock private SessionRecordRepository sessionRecordRepository;
    @Mock private SessionProgressRepository sessionProgressRepository;
    @Mock private Clock clock;

    @InjectMocks
    private DemoSeedService demoSeedService;

    @Test
    @DisplayName("시드 선생님에게 과외가 이미 있으면 아무것도 만들지 않고 건너뛴다")
    void 이미_과외가_있으면_건너뜀() {
        // given
        User teacher = User.createTeacher(Provider.KAKAO, "seed-teacher-1", "seed-teacher@sudam.test");
        ReflectionTestUtils.setField(teacher, "id", 1L);
        given(userRepository.findByProviderAndProviderId(Provider.KAKAO, "seed-teacher-1"))
                .willReturn(Optional.of(teacher));
        given(tutoringRepository.existsByTeacherId(1L)).willReturn(true);

        // when
        DemoSeedResult result = demoSeedService.seed("seed-teacher-1");

        // then
        assertThat(result.created()).isFalse();
        assertThat(result.teacherUserId()).isEqualTo(1L);
        verify(userRepository).findByProviderAndProviderId(Provider.KAKAO, "seed-teacher-1");
        verify(tutoringRepository).existsByTeacherId(1L);
        verifyNoMoreInteractions(userRepository, tutoringRepository);
        verifyNoInteractions(teacherProfileRepository, termsRepository, userTermsAgreementRepository,
                tutoringScheduleRepository, textbookRepository, tutoringPolicyRepository, tutoringSettingRepository,
                participantRepository, cycleRepository, invoiceRepository, sessionRepository,
                sessionRecordRepository, sessionProgressRepository);
    }
}
