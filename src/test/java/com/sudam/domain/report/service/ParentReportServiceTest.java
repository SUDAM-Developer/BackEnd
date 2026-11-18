package com.sudam.domain.report.service;

import com.sudam.domain.cycle.entity.Cycle;
import com.sudam.domain.participant.entity.Participant;
import com.sudam.domain.participant.enums.ParticipantLabel;
import com.sudam.domain.participant.exception.ParticipantException;
import com.sudam.domain.participant.exception.code.ParticipantErrorCode;
import com.sudam.domain.participant.service.ParticipantLinkService;
import com.sudam.domain.report.dto.response.ParentReportResponse;
import com.sudam.domain.report.dto.response.ReportResponse;
import com.sudam.domain.report.entity.SettlementReport;
import com.sudam.domain.report.exception.ReportException;
import com.sudam.domain.report.repository.SettlementReportRepository;
import com.sudam.domain.report.repository.SettlementReportViewRepository;
import com.sudam.domain.tutoring.entity.Tutoring;
import com.sudam.domain.user.entity.TeacherProfile;
import com.sudam.domain.user.entity.User;
import com.sudam.domain.user.enums.Provider;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("ParentReportService 학부모 링크로 리포트 열기")
class ParentReportServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 1, 21, 5);
    private static final String TOKEN = "mother-token";
    private static final Long REPORT_ID = 21L;
    private static final Long PARENT_ID = 31L;

    @Mock private ParticipantLinkService participantLinkService;
    @Mock private SettlementReportRepository settlementReportRepository;
    @Mock private SettlementReportViewRepository settlementReportViewRepository;
    @Mock private ReportService reportService;

    private ParentReportService parentReportService;
    private Participant parent;
    private SettlementReport report;

    @BeforeEach
    void setUp() {
        parentReportService = new ParentReportService(participantLinkService, settlementReportRepository,
                settlementReportViewRepository, reportService, Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL));

        TeacherProfile teacher = TeacherProfile.create(
                User.createTeacher(Provider.KAKAO, "kakao-1", "teacher@sudam.app"), "한서윤", List.of(Subject.ENGLISH), null);
        Tutoring tutoring = withId(Tutoring.builder().teacher(teacher).subject(Subject.ENGLISH)
                .studentName("윤서아").gradeLevel(9).gradeSchoolYear(2026).build(), 10L);
        parent = withId(Participant.createParent(tutoring, ParticipantLabel.MOTHER, "01000000002"), PARENT_ID);
        report = withId(SettlementReport.builder().tutoring(tutoring)
                .cycle(withId(Cycle.open(tutoring, 1, LocalDateTime.of(2026, 9, 1, 0, 0)), 88L))
                .completedCount(8).targetCount(8).canceledCount(0)
                .understandingHigh(2).understandingMedium(5).understandingLow(1).build(), REPORT_ID);
    }

    @Test
    @DisplayName("보낸 리포트면 요약 · 코멘트 · 선생님 이름을 돌려주고 지금 시각으로 첫 열람을 남긴다")
    void 보낸_리포트_열기() {
        // given
        report.reviewComment("이번 달 서아는 …", NOW.minusHours(3));
        report.markSent(NOW.minusHours(3));
        ReportResponse summary = new ReportResponse(REPORT_ID, report.getStatus(), "윤서아",
                new ReportResponse.Period(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)),
                null, List.of(), null);
        given(participantLinkService.resolveParent(TOKEN)).willReturn(parent);
        given(settlementReportRepository.findWithTutoringById(REPORT_ID)).willReturn(Optional.of(report));
        given(reportService.toResponse(report)).willReturn(summary);

        // when
        ParentReportResponse response = parentReportService.get(TOKEN, REPORT_ID);

        // then
        assertThat(response.report()).isSameAs(summary);
        assertThat(response.comment()).isEqualTo("이번 달 서아는 …");
        assertThat(response.teacherName()).isEqualTo("한서윤");
        assertThat(response.sentAt()).isEqualTo(NOW.minusHours(3));
        then(settlementReportViewRepository).should().insertFirstView(REPORT_ID, PARENT_ID, NOW);
    }

    @Test
    @DisplayName("아직 보내지 않은 리포트면 REPORT404_1이고 열람을 남기지 않는다")
    void 보내기_전_리포트() {
        // given
        given(participantLinkService.resolveParent(TOKEN)).willReturn(parent);
        given(settlementReportRepository.findWithTutoringById(REPORT_ID)).willReturn(Optional.of(report));

        // when & then
        assertReportNotFound();
        then(settlementReportViewRepository).should(never()).insertFirstView(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("링크의 과외가 아닌 리포트면 REPORT404_1")
    void 다른_과외_리포트() {
        // given
        Tutoring other = withId(Tutoring.builder().subject(Subject.ENGLISH).studentName("박지후")
                .gradeLevel(8).gradeSchoolYear(2026).build(), 11L);
        Participant otherParent = withId(Participant.createParent(other, ParticipantLabel.MOTHER, "01000000001"), 30L);
        report.reviewComment("코멘트", NOW);
        report.markSent(NOW);
        given(participantLinkService.resolveParent(TOKEN)).willReturn(otherParent);
        given(settlementReportRepository.findWithTutoringById(REPORT_ID)).willReturn(Optional.of(report));

        // when & then
        assertReportNotFound();
    }

    @Test
    @DisplayName("링크가 올바르지 않으면 PARTICIPANT404_1이고 리포트를 조회하지 않는다")
    void 잘못된_링크() {
        // given
        given(participantLinkService.resolveParent(TOKEN))
                .willThrow(new ParticipantException(ParticipantErrorCode.LINK_NOT_FOUND));

        // when & then
        assertThatThrownBy(() -> parentReportService.get(TOKEN, REPORT_ID))
                .isInstanceOf(ParticipantException.class);
        then(settlementReportRepository).should(never()).findWithTutoringById(anyLong());
    }

    private void assertReportNotFound() {
        assertThatThrownBy(() -> parentReportService.get(TOKEN, REPORT_ID))
                .isInstanceOf(ReportException.class)
                .extracting(e -> ((ProjectException) e).getErrorCode().getCode())
                .isEqualTo("REPORT404_1");
    }

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
