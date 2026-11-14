package com.sudam.global.seed;

import com.sudam.global.security.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 부팅 직후 시연용 가상 데이터를 만든다. sudam.seed.enabled = true일 때만 빈이 생긴다.
 * 배포 프로파일이 따로 없어 프로파일이 아니라 이 설정값 하나로 켜고 끈다.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "sudam.seed", name = "enabled", havingValue = "true")
public class DemoSeedRunner implements ApplicationRunner {

    static final String DEFAULT_TEACHER_KAKAO_ID = "seed-teacher-1";

    private final DemoSeedService demoSeedService;
    private final JwtUtil jwtUtil;
    private final String teacherKakaoId;

    public DemoSeedRunner(DemoSeedService demoSeedService, JwtUtil jwtUtil,
                          @Value("${sudam.seed.teacher-kakao-id:}") String teacherKakaoId) {
        this.demoSeedService = demoSeedService;
        this.jwtUtil = jwtUtil;
        this.teacherKakaoId = teacherKakaoId == null || teacherKakaoId.isBlank()
                ? DEFAULT_TEACHER_KAKAO_ID
                : teacherKakaoId.trim();
    }

    /***
     * 함수 기능: 가상 데이터를 만들고 시연에 필요한 값을 로그로 남긴다.
     *          기본 회원번호(가짜 계정)일 때만 액세스 토큰을 콘솔 로그에 한 번 출력한다.
     *          카카오 회원번호를 넣었으면 실제 사람 계정이므로 토큰을 출력하지 않는다. 토큰은 파일 · DB · 응답에 남기지 않는다.
     * @param args 실행 인자 (쓰지 않음)
     */
    @Override
    public void run(ApplicationArguments args) {
        DemoSeedResult result = demoSeedService.seed(teacherKakaoId);
        if (!result.created()) {
            log.info("[가상 데이터] 가상 데이터가 이미 있어 건너뜀. teacherUserId={}", result.teacherUserId());
            return;
        }

        log.info("[가상 데이터] 시연용 가상 데이터를 만들었습니다. teacherUserId={}, 김서아 tutoringId={}, 오늘 scheduleId={}, originalDate={}",
                result.teacherUserId(), result.demoTutoringId(), result.todayScheduleId(), result.today());
        if (DEFAULT_TEACHER_KAKAO_ID.equals(teacherKakaoId)) {
            // 커밋이 끝난 뒤 발급한다. 데이터 생성이 실패하면 여기까지 오지 않는다.
            log.info("[가상 데이터] 시연용 액세스 토큰 (30분 뒤 만료, 다시 받으려면 재부팅): {}",
                    jwtUtil.createAccessToken(result.teacherUserId(), result.teacherRole()));
        } else {
            log.info("[가상 데이터] 카카오 회원번호로 지정한 계정입니다. 카카오 로그인으로 들어가세요.");
        }
    }
}
