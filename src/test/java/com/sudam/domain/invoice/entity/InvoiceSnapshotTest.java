package com.sudam.domain.invoice.entity;

import com.sudam.domain.tutoring.entity.TutoringPolicy;
import com.sudam.domain.tutoring.enums.PaymentTiming;
import org.hibernate.type.format.jackson.Jackson3JsonFormatMapper;
import org.hibernate.type.format.jackson.JacksonJsonFormatMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InvoiceSnapshot 값 객체")
class InvoiceSnapshotTest {

    private static final InvoiceSnapshot SNAPSHOT = new InvoiceSnapshot(
            7, 45_000, 8, PaymentTiming.POSTPAID, LocalDate.of(2026, 10, 1),
            List.of(new CanceledLesson(LocalDate.of(2026, 10, 15), "TEACHER"))
    );

    // 클래스패스에 Jackson 2 · 3이 모두 있어 Hibernate가 어느 쪽을 고르든 같은 JSON이 나와야 한다.
    // 두 매퍼의 (Object, Type) 변환은 구체 클래스에서만 공개라 람다로 감싸 넘긴다.
    static Stream<Arguments> hibernateJsonMappers() {
        JacksonJsonFormatMapper jackson2 = new JacksonJsonFormatMapper();
        Jackson3JsonFormatMapper jackson3 = new Jackson3JsonFormatMapper();
        return Stream.of(
                Arguments.of("Jackson 2", new JsonCodec(
                        value -> jackson2.toString(value, InvoiceSnapshot.class),
                        json -> jackson2.fromString(json, InvoiceSnapshot.class))),
                Arguments.of("Jackson 3", new JsonCodec(
                        value -> jackson3.toString(value, InvoiceSnapshot.class),
                        json -> jackson3.fromString(json, InvoiceSnapshot.class)))
        );
    }

    private record JsonCodec(Function<InvoiceSnapshot, String> write, Function<String, InvoiceSnapshot> read) {
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("hibernateJsonMappers")
    @DisplayName("Hibernate JSON 매퍼로 저장했다가 읽으면 같은 스냅샷이 나온다")
    void JSON_왕복하면_같은_값이다(String name, JsonCodec codec) {
        // when
        String json = codec.write().apply(SNAPSHOT);
        InvoiceSnapshot restored = codec.read().apply(json);

        // then
        assertThat(restored).isEqualTo(SNAPSHOT);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("hibernateJsonMappers")
    @DisplayName("JSON 키는 고정된 6개이고 날짜는 yyyy-MM-dd 문자열로 저장된다")
    void JSON_키와_날짜_형식이_고정된다(String name, JsonCodec codec) {
        // when
        String json = codec.write().apply(SNAPSHOT);

        // then
        assertThat(json)
                .contains("\"recognizedCount\":7")
                .contains("\"unitPrice\":45000")
                .contains("\"settlementSessionCount\":8")
                .contains("\"paymentTiming\":\"POSTPAID\"")
                .contains("\"cycleStartDate\":\"2026-10-01\"")
                .contains("\"canceledLessons\":[{\"date\":\"2026-10-15\",\"cancelReason\":\"TEACHER\"}]");
    }

    @Test
    @DisplayName("정책으로 만들면 회당 단가는 수업료 ÷ 정산 회차에서 원 미만을 버린다")
    void 정책으로_만들면_단가는_원_미만_버림() {
        // given
        TutoringPolicy policy = TutoringPolicy.builder()
                .effectiveCycleNo(1)
                .settlementSessionCnt(3)
                .paymentTiming(PaymentTiming.PREPAID)
                .tuitionFee(100_000)
                .bankName("국민")
                .accountNumber("1234567890")
                .accountHolder("한서윤")
                .build();

        // when
        InvoiceSnapshot snapshot = InvoiceSnapshot.of(policy, null, null, null);

        // then
        assertThat(snapshot.unitPrice()).isEqualTo(33_333);
        assertThat(snapshot.settlementSessionCount()).isEqualTo(3);
        assertThat(snapshot.paymentTiming()).isEqualTo(PaymentTiming.PREPAID);
        assertThat(snapshot.canceledLessons()).isEmpty();
    }
}
