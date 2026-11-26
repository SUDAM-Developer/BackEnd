package com.sudam.domain.record.service;

import com.sudam.domain.cycle.entity.Cycle;
import com.sudam.domain.record.dto.response.RecordFormResponse;
import com.sudam.domain.record.dto.response.RecordFormResponse.TextbookItem;
import com.sudam.domain.record.entity.SessionProgress;
import com.sudam.domain.record.entity.SessionRecord;
import com.sudam.domain.record.enums.HomeworkResult;
import com.sudam.domain.record.enums.Understanding;
import com.sudam.domain.record.exception.RecordException;
import com.sudam.domain.session.entity.Session;
import com.sudam.domain.session.service.LessonCalendar;
import com.sudam.domain.tutoring.entity.Textbook;
import com.sudam.domain.tutoring.entity.Tutoring;
import com.sudam.domain.tutoring.entity.TutoringPolicy;
import com.sudam.domain.tutoring.entity.TutoringSchedule;
import com.sudam.domain.tutoring.enums.PaymentTiming;
import com.sudam.domain.tutoring.enums.ProgressUnit;
import com.sudam.domain.user.entity.TeacherProfile;
import com.sudam.domain.user.entity.User;
import com.sudam.domain.user.enums.Provider;
import com.sudam.domain.user.enums.Subject;
import com.sudam.global.config.ClockConfig;
import com.sudam.global.config.JpaConfig;
import com.sudam.global.exception.ProjectException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 기록 화면 조회를 실제 MySQL로 확인한다. 새 JPQL(교재별 지난 진도 · 직전 숙제 limit 1)이 맞는 행을 고르는지,
 * '지난'이 이 회차 날짜보다 앞선 기록 기준인지(늦게 기록하는 경우)를 본다. 각 테스트는 롤백되어 데이터를 남기지 않는다.
 *
 * <p>날짜는 오늘 기준 주 단위로 잡는다: 매주 오늘 요일 수업, 과외 · 일정은 5주 전부터.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class, ClockConfig.class, LessonCalendar.class, RecordFormService.class})
@DisplayName("RecordFormService 기록 화면 조회 (MySQL)")
class RecordFormServiceIntegrationTest {

    @Autowired private TestEntityManager em;
    @Autowired private RecordFormService recordFormService;
    @Autowired private Clock clock;

    private LocalDate today;
    private Long teacherId;
    private Tutoring tutoring;
    private TutoringSchedule schedule;
    private Cycle cycle;
    private Textbook english;
    private Textbook grammar;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(clock);
        LocalDate start = today.minusWeeks(5);
        User user = em.persist(User.createTeacher(Provider.KAKAO, "record-form-it", "record-form@sudam.app"));
        teacherId = user.getId();
        TeacherProfile teacher = em.persist(TeacherProfile.create(user, "한서윤", List.of(Subject.ENGLISH), null));
        tutoring = em.persist(Tutoring.builder().teacher(teacher).subjects(List.of(Subject.ENGLISH))
                .studentName("윤서아").gradeLevel(9).gradeSchoolYear(today.getYear()).build());
        schedule = em.persist(TutoringSchedule.builder().tutoring(tutoring)
                .dayOfWeek(today.getDayOfWeek().getValue())
                .startTime(LocalTime.of(18, 0)).endTime(LocalTime.of(19, 30)).effectiveFrom(start).build());
        english = em.persist(Textbook.builder().tutoring(tutoring).name("수능특강 영어")
                .progressUnit(ProgressUnit.CUSTOM).customUnitLabel("강").build());
        grammar = em.persist(Textbook.builder().tutoring(tutoring).name("Grammar Zone 기본")
                .progressUnit(ProgressUnit.UNIT).startPosition(3).build());
        em.persist(TutoringPolicy.builder().tutoring(tutoring).effectiveCycleNo(1).settlementSessionCnt(8)
                .paymentTiming(PaymentTiming.POSTPAID).tuitionFee(320_000).bankName("국민")
                .accountNumber("123-456-789012").accountHolder("한서윤").build());
        cycle = em.persist(Cycle.open(tutoring, 1, start.atStartOfDay()));
        em.flush();
        // 생성 시각은 감사 기능이 지금으로 채운다. 과외 기간(생성일 ~) 안에 지난 회차가 들도록 일정 시작일로 당긴다.
        em.getEntityManager().createQuery("update Tutoring t set t.createdAt = :createdAt where t.id = :id")
                .setParameter("createdAt", start.atStartOfDay())
                .setParameter("id", tutoring.getId())
                .executeUpdate();
    }

    @Test
    @DisplayName("지난 수업을 늦게 기록하면 그 회차보다 앞선 기록의 진도 · 숙제를 주고, 뒤의 기록은 보지 않는다")
    void 늦게_기록하는_회차() {
        // given: 3주 전 기록, 2주 전 미기록(이 화면), 1주 전 기록
        LocalDate before = today.minusWeeks(3);
        LocalDate target = today.minusWeeks(2);
        LocalDate after = today.minusWeeks(1);
        record(before, "5강 복습", english, 4, 5);
        record(after, "7강 예습", english, 6, 7);
        clearContext();

        // when
        RecordFormResponse response = recordFormService.getRecordForm(teacherId, tutoring.getId(),
                schedule.getId(), target);

        // then
        assertThat(response.sessionId()).isNull();
        assertThat(response.sessionDate()).isEqualTo(target);
        assertThat(response.startTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(response.studentName()).isEqualTo("윤서아");
        assertThat(response.textbooks()).containsExactly(
                new TextbookItem(english.getId(), "수능특강 영어", ProgressUnit.CUSTOM, "강", 5),
                new TextbookItem(grammar.getId(), "Grammar Zone 기본", ProgressUnit.UNIT, null, 3));
        assertThat(response.previousHomework()).isEqualTo("5강 복습");
        // 지난 미기록 회차는 '지금 기록하면 될 번호' = 진행 중 인정 수(2) + 1
        assertThat(response.noInCycle()).isEqualTo(3);
        assertThat(response.cycle()).isEqualTo(new RecordFormResponse.CycleSummary(1, 2, 8));
        assertThat(response.record()).isNull();
    }

    @Test
    @DisplayName("직전 기록에 숙제가 비어 있으면 더 이전 기록으로 거슬러 가지 않고 previousHomework는 null이다")
    void 직전_숙제_없음() {
        // given
        record(today.minusWeeks(4), "4강 복습", english, 3, 4);
        record(today.minusWeeks(3), null, english, 4, 5);
        clearContext();

        // when
        RecordFormResponse response = recordFormService.getRecordForm(teacherId, tutoring.getId(),
                schedule.getId(), today.minusWeeks(2));

        // then
        assertThat(response.previousHomework()).isNull();
        assertThat(response.textbooks().get(0).lastPosition()).isEqualTo(5);
    }

    @Test
    @DisplayName("기록이 하나도 없으면 교재는 시작 진도, 지난 숙제는 null, 순번은 1이다")
    void 첫_기록() {
        // given
        clearContext();

        // when
        RecordFormResponse response = recordFormService.getRecordForm(teacherId, tutoring.getId(),
                schedule.getId(), today.minusWeeks(1));

        // then
        assertThat(response.textbooks()).extracting(TextbookItem::lastPosition).containsExactly(0, 3);
        assertThat(response.previousHomework()).isNull();
        assertThat(response.noInCycle()).isEqualTo(1);
    }

    @Test
    @DisplayName("이미 기록한 날짜로 열면 RECORD409_1이다")
    void 기록한_날짜() {
        // given
        LocalDate recorded = today.minusWeeks(1);
        record(recorded, "7강 예습", english, 6, 7);
        clearContext();

        // when & then
        assertThatThrownBy(() -> recordFormService.getRecordForm(teacherId, tutoring.getId(),
                schedule.getId(), recorded))
                .isInstanceOf(RecordException.class)
                .extracting(e -> ((ProjectException) e).getErrorCode().getCode())
                .isEqualTo("RECORD409_1");
    }

    // 정규 회차 하나를 기록한다: 회차(완료 · 인정) + 기록 + 교재 진도 1건.
    private void record(LocalDate date, String nextHomework, Textbook textbook, int startPosition, int endPosition) {
        Session session = em.persist(Session.completeRegular(tutoring, cycle, schedule, date));
        em.persist(SessionRecord.create(session, Understanding.HIGH, HomeworkResult.DONE, nextHomework, null));
        em.persist(SessionProgress.create(session, textbook, startPosition, endPosition));
    }

    // 1차 캐시 없이 서비스가 DB에서 다시 읽게 한다 (생성 시각을 바꾼 update는 영속성 컨텍스트를 거치지 않는다).
    private void clearContext() {
        em.flush();
        em.clear();
    }
}
