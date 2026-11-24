package com.sudam.domain.schedule.service;

import com.sudam.domain.cycle.entity.Cycle;
import com.sudam.domain.record.entity.SessionRecord;
import com.sudam.domain.record.enums.HomeworkResult;
import com.sudam.domain.record.enums.Understanding;
import com.sudam.domain.schedule.dto.response.DailyScheduleResponse;
import com.sudam.domain.schedule.dto.response.MonthlyScheduleResponse;
import com.sudam.domain.session.entity.Session;
import com.sudam.domain.session.service.LessonCalendar;
import com.sudam.domain.tutoring.entity.Tutoring;
import com.sudam.domain.tutoring.entity.TutoringPolicy;
import com.sudam.domain.tutoring.entity.TutoringSchedule;
import com.sudam.domain.tutoring.enums.PaymentTiming;
import com.sudam.domain.user.entity.TeacherProfile;
import com.sudam.domain.user.entity.User;
import com.sudam.domain.user.enums.Provider;
import com.sudam.domain.user.enums.Subject;
import com.sudam.global.config.ClockConfig;
import com.sudam.global.config.JpaConfig;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 일정 탭 조회의 쿼리 수 측정. 과외가 늘어도 쿼리 수가 그대로인지(N+1이 없는지) 실제 MySQL로 확인한다.
 * 각 테스트는 롤백되어 데이터를 남기지 않는다.
 */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class, ClockConfig.class, LessonCalendar.class, ScheduleService.class})
@DisplayName("ScheduleService 쿼리 수 (MySQL)")
class ScheduleServiceIntegrationTest {

    @Autowired private TestEntityManager em;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private ScheduleService scheduleService;
    @Autowired private Clock clock;

    @Test
    @DisplayName("daily는 과외가 1개든 5개든 같은 수의 쿼리로 끝난다")
    void daily_쿼리_수는_과외_수와_무관하다() {
        // given
        Long oneTeacher = teacherWithTutorings("one", 1);
        Long fiveTeacher = teacherWithTutorings("five", 5);

        // when
        long one = countQueries(() -> scheduleService.daily(oneTeacher, null, null));
        DailyScheduleResponse[] five = new DailyScheduleResponse[1];
        long fiveCount = countQueries(() -> five[0] = scheduleService.daily(fiveTeacher, null, null));

        // then
        assertThat(five[0].sessions()).hasSize(5);
        assertThat(five[0].sessions()).allSatisfy(lesson -> assertThat(lesson.hasRecord()).isTrue());
        assertThat(five[0].upcoming()).hasSize(3);
        assertThat(fiveCount).isEqualTo(one);
    }

    @Test
    @DisplayName("monthly는 과외가 1개든 5개든 같은 수의 쿼리로 끝난다")
    void monthly_쿼리_수는_과외_수와_무관하다() {
        // given
        Long oneTeacher = teacherWithTutorings("one", 1);
        Long fiveTeacher = teacherWithTutorings("five", 5);
        YearMonth thisMonth = YearMonth.now(clock);

        // when
        long one = countQueries(() -> scheduleService.monthly(oneTeacher, thisMonth));
        MonthlyScheduleResponse[] five = new MonthlyScheduleResponse[1];
        long fiveCount = countQueries(() -> five[0] = scheduleService.monthly(fiveTeacher, thisMonth));

        // then
        assertThat(five[0].students()).hasSize(5);
        assertThat(five[0].days()).isNotEmpty();
        assertThat(fiveCount).isEqualTo(one);
    }

    // 영속성 컨텍스트를 비운 뒤 실행해 1차 캐시 없이 실제로 나가는 쿼리만 센다.
    private long countQueries(Supplier<?> call) {
        em.flush();
        em.clear();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        call.get();
        return statistics.getPrepareStatementCount();
    }

    // 매일 수업하는 과외 count개. 사이클 1 진행 중, 정산 8회, 오늘 회차는 기록 완료.
    private Long teacherWithTutorings(String key, int count) {
        LocalDate today = LocalDate.now(clock);
        User user = em.persist(User.createTeacher(Provider.KAKAO, "query-count-" + key, key + "@sudam.app"));
        TeacherProfile teacher = em.persist(TeacherProfile.create(user, "한서윤", List.of(Subject.ENGLISH), null));
        for (int i = 0; i < count; i++) {
            Tutoring tutoring = em.persist(Tutoring.builder().teacher(teacher)
                    .subjects(List.of(Subject.ENGLISH, Subject.MATH)).studentName("학생" + i)
                    .gradeLevel(8).gradeSchoolYear(today.getYear()).build());
            TutoringSchedule todaySchedule = null;
            for (int day = 1; day <= 7; day++) {
                TutoringSchedule schedule = em.persist(TutoringSchedule.builder().tutoring(tutoring).dayOfWeek(day)
                        .startTime(LocalTime.of(23, 0)).endTime(LocalTime.of(23, 50))
                        .effectiveFrom(today.minusDays(7)).build());
                if (day == today.getDayOfWeek().getValue()) {
                    todaySchedule = schedule;
                }
            }
            em.persist(TutoringPolicy.builder().tutoring(tutoring).effectiveCycleNo(1).settlementSessionCnt(8)
                    .paymentTiming(PaymentTiming.POSTPAID).tuitionFee(300_000).bankName("국민")
                    .accountNumber("123-456-789012").accountHolder("한서윤").build());
            Cycle cycle = em.persist(Cycle.open(tutoring, 1, LocalDateTime.now(clock).minusDays(1)));
            Session session = em.persist(Session.completeRegular(tutoring, cycle, todaySchedule, today));
            em.persist(SessionRecord.create(session, Understanding.HIGH, HomeworkResult.DONE, null, null));
        }
        return user.getId();
    }
}
