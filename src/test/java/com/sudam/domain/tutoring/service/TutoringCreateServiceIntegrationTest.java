package com.sudam.domain.tutoring.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sudam.domain.cycle.service.CycleOpener;
import com.sudam.domain.invoice.service.FirstInvoiceIssuer;
import com.sudam.domain.participant.enums.ParticipantLabel;
import com.sudam.domain.participant.service.LinkTokenGenerator;
import com.sudam.domain.participant.service.ParticipantInviter;
import com.sudam.domain.session.service.LessonCalendar;
import com.sudam.domain.share.service.ShareLinkFactory;
import com.sudam.domain.tutoring.dto.request.TutoringCreateRequest;
import com.sudam.domain.tutoring.dto.request.TutoringCreateRequest.Account;
import com.sudam.domain.tutoring.dto.request.TutoringCreateRequest.Invite;
import com.sudam.domain.tutoring.dto.request.TutoringCreateRequest.ParentItem;
import com.sudam.domain.tutoring.dto.request.TutoringCreateRequest.Policy;
import com.sudam.domain.tutoring.dto.request.TutoringCreateRequest.Preset;
import com.sudam.domain.tutoring.dto.request.TutoringCreateRequest.ScheduleItem;
import com.sudam.domain.tutoring.dto.request.TutoringCreateRequest.Settings;
import com.sudam.domain.tutoring.dto.request.TutoringCreateRequest.Student;
import com.sudam.domain.tutoring.dto.request.TutoringCreateRequest.TextbookItem;
import com.sudam.domain.tutoring.dto.response.TutoringCreateResponse;
import com.sudam.domain.tutoring.entity.TutoringPolicy;
import com.sudam.domain.tutoring.enums.ParentUpdateFrequency;
import com.sudam.domain.tutoring.enums.PaymentTiming;
import com.sudam.domain.tutoring.enums.ProgressUnit;
import com.sudam.domain.tutoring.exception.code.TutoringErrorCode;
import com.sudam.domain.user.entity.TeacherProfile;
import com.sudam.domain.user.entity.User;
import com.sudam.domain.user.enums.Provider;
import com.sudam.domain.user.enums.Subject;
import com.sudam.global.config.JpaConfig;
import com.sudam.global.exception.ProjectException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * 과외 만들기를 실제 MySQL로 확인한다. 행 수 · 계좌 암호화 · 첫 수업 날짜를 본다.
 * 각 테스트는 롤백되어 데이터를 남기지 않는다. 공유 DB라 전역 건수는 단정하지 않고 생성된 과외 ID로만 센다.
 */
@DataJpaTest(properties = "app.link.base-url=https://sudam.app")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class, TutoringCreateService.class, CycleOpener.class, ParticipantInviter.class,
        LinkTokenGenerator.class, FirstInvoiceIssuer.class, LessonCalendar.class, ShareLinkFactory.class,
        TutoringCreateServiceIntegrationTest.FixedClockConfig.class})
@DisplayName("TutoringCreateService 과외 만들기 (MySQL)")
class TutoringCreateServiceIntegrationTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    // 2026-12-01 화요일 20:00 (서울): 오늘이 수업 요일이고 17:00 시작은 이미 지났다.
    private static final Instant NOW = Instant.parse("2026-12-01T11:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 12, 1);

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, SEOUL);
        }
    }

    @Autowired private TestEntityManager em;
    @Autowired private TutoringCreateService service;

    private Long teacherId;

    @BeforeEach
    void setUp() {
        User user = em.persist(User.createTeacher(Provider.KAKAO, "tutoring-create-it", "tutoring-create@sudam.app"));
        teacherId = user.getId();
        em.persist(TeacherProfile.create(user, "한서윤", List.of(Subject.MATH), null));
        em.flush();
    }

    private TutoringCreateRequest request(PaymentTiming timing, String studentPhone) {
        return new TutoringCreateRequest(
                new Preset(false, ParentUpdateFrequency.EVERY_SESSION),
                new Student("박도윤", 8, null, List.of(Subject.MATH), null),
                List.of(new TextbookItem("쎈 중2-1", ProgressUnit.SECTION, null, 1),
                        new TextbookItem("개념원리", ProgressUnit.UNIT, null, 1)),
                List.of(new ScheduleItem(2, LocalTime.of(17, 0), LocalTime.of(18, 30)),
                        new ScheduleItem(5, LocalTime.of(17, 0), LocalTime.of(18, 30))),
                new Policy(8, timing, 360000),
                new Account("국민은행", "123456-01-401492", "한서윤"),
                new Settings(ParentUpdateFrequency.EVERY_SESSION, true, false),
                new Invite(List.of(new ParentItem("010-2468-0525", ParticipantLabel.MOTHER),
                        new ParentItem("010-2468-0526", ParticipantLabel.FATHER)), studentPhone),
                null, null, null);
    }

    private long count(String entity, String tutoringPath, Long tutoringId) {
        return em.getEntityManager()
                .createQuery("select count(e) from " + entity + " e where e." + tutoringPath + " = :id", Long.class)
                .setParameter("id", tutoringId).getSingleResult();
    }

    @Test
    @DisplayName("선불 + 학생 번호면 모든 행이 한 번에 만들어진다")
    void 선불_학생_번호_행_수() {
        // given / when
        TutoringCreateResponse response = service.create(teacherId, request(PaymentTiming.PREPAID, "010-5555-0192"));
        em.flush();
        em.clear();

        // then
        Long id = response.tutoringId();
        assertThat(count("Tutoring", "id", id)).isEqualTo(1);
        assertThat(count("TutoringSchedule", "tutoring.id", id)).isEqualTo(2);
        assertThat(count("Textbook", "tutoring.id", id)).isEqualTo(2);
        assertThat(count("TutoringPolicy", "tutoring.id", id)).isEqualTo(1);
        assertThat(count("TutoringSetting", "tutoring.id", id)).isEqualTo(1);
        assertThat(count("Cycle", "tutoring.id", id)).isEqualTo(1);
        assertThat(count("Participant", "tutoring.id", id)).isEqualTo(3);
        assertThat(count("Invoice", "tutoring.id", id)).isEqualTo(1);
        assertThat(em.getEntityManager().createQuery(
                        "select count(c) from Cycle c where c.id = :cid and c.tutoring.id = :id and c.cycleNo = 1 "
                                + "and c.status = com.sudam.domain.cycle.enums.CycleStatus.IN_PROGRESS", Long.class)
                .setParameter("cid", response.cycleId()).setParameter("id", id).getSingleResult()).isEqualTo(1);
        assertThat(em.getEntityManager().createQuery(
                        "select count(i) from Invoice i where i.id = :iid and i.billedCycleId = :cid "
                                + "and i.kind = com.sudam.domain.invoice.enums.InvoiceKind.FIRST", Long.class)
                .setParameter("iid", response.firstInvoice().invoiceId())
                .setParameter("cid", response.cycleId()).getSingleResult()).isEqualTo(1);
    }

    @Test
    @DisplayName("계좌번호는 DB에 암호화되어 저장되고 엔티티로는 하이픈 없는 평문으로 읽힌다")
    void 계좌번호_암호화() {
        // given / when
        TutoringCreateResponse response = service.create(teacherId, request(PaymentTiming.PREPAID, null));
        em.flush();
        em.clear();

        // then
        Object[] raw = (Object[]) em.getEntityManager()
                .createNativeQuery("select account_number, bank_code from tutoring_policies where tutoring_id = ?1")
                .setParameter(1, response.tutoringId()).getSingleResult();
        assertThat((String) raw[0]).isNotEqualTo("123456-01-401492").isNotEqualTo("12345601401492");
        assertThat((String) raw[1]).isEqualTo("004");
        TutoringPolicy policy = em.getEntityManager().createQuery(
                        "select p from TutoringPolicy p where p.tutoring.id = :id", TutoringPolicy.class)
                .setParameter("id", response.tutoringId()).getSingleResult();
        assertThat(policy.getAccountNumber()).isEqualTo("12345601401492");
    }

    @Test
    @DisplayName("오늘이 수업 요일이고 시작 시각이 지났어도 첫 수업은 오늘이다")
    void 첫_수업_오늘() {
        // given / when
        TutoringCreateResponse response = service.create(teacherId, request(PaymentTiming.POSTPAID, null));

        // then
        assertThat(response.firstLesson().date()).isEqualTo(TODAY);
        assertThat(response.firstLesson().dayOfWeek()).isEqualTo(2);
        assertThat(response.firstLesson().startTime()).isEqualTo(LocalTime.of(17, 0));
        assertThat(response.firstInvoice()).isNull();
    }

    @Test
    @DisplayName("프로필이 없는 선생님이면 TUTORING404_1이고 과외가 만들어지지 않는다")
    void 프로필_없음() {
        // given
        Long missingTeacherId = 999_999_999L;

        // when & then
        assertThatThrownBy(() -> service.create(missingTeacherId, request(PaymentTiming.PREPAID, null)))
                .isInstanceOfSatisfying(ProjectException.class, e -> assertThat(e.getErrorCode().getCode())
                        .isEqualTo(TutoringErrorCode.TEACHER_PROFILE_NOT_FOUND.getCode()));
        assertThat(count("Tutoring", "teacher.id", missingTeacherId)).isZero();
    }
}
