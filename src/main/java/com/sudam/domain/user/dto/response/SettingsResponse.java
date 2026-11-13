package com.sudam.domain.user.dto.response;

import com.sudam.domain.user.entity.TeacherProfile;
import com.sudam.domain.user.enums.Provider;
import com.sudam.domain.user.enums.Subject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// profile · account만 DB 값이다.
// TODO: 나머지 묶음은 해당 도메인(소개서 · 결제 · 알림)이 생기기 전까지 고정 기본값을 내려준다.
public record SettingsResponse(
        Profile profile,
        Account account,
        Intro intro,
        CurriculumPass curriculumPass,
        Subscription subscription,
        Notification notification
) {

    // 내 정보 - 이름,과목
    public record Profile(String name, List<SubjectItem> subjects) {
    }

    public record SubjectItem(Subject code, String name) {
    }
    // 내 정보 - 로그인
    public record Account(Provider provider) {
    }
    // 결제, 이용권 - 소개서 전문
    public record Intro(String status, LocalDateTime unlockedAt) {
    }
    // 결제, 이용권 - 커리큘럼 이용권
    public record CurriculumPass(int availableCount, LocalDateTime nearestExpiresAt, boolean hasPendingRequest) {
    }
    // 결제, 이용권 - 구록
    public record Subscription(String status, LocalDate nextBillingOn, boolean hasPendingRequest) {
    }
    // 알림
    public record Notification(int sentCount, boolean pushEnabled) {
    }

    public static SettingsResponse from(TeacherProfile profile, Provider provider) {
        // OTHER는 enum 한글명("기타") 대신 선생님이 직접 입력한 과목명을 보여준다.
        List<SubjectItem> subjects = profile.getSubjects().stream()
                .map(subject -> new SubjectItem(subject,
                        subject == Subject.OTHER ? profile.getSubjectOther() : subject.getName()))
                .toList();

        return new SettingsResponse(
                new Profile(profile.getName(), subjects),
                new Account(provider),
                new Intro("NONE", null),
                new CurriculumPass(0, null, false),
                new Subscription("NONE", null, false),
                new Notification(0, false)
        );
    }
}
