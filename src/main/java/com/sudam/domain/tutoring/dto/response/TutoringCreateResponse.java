package com.sudam.domain.tutoring.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sudam.domain.participant.enums.ParticipantLabel;
import com.sudam.domain.participant.enums.ParticipantRole;
import com.sudam.domain.tutoring.enums.PresetType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record TutoringCreateResponse(
        Long tutoringId,
        Long cycleId,
        PresetType presetType,
        @JsonProperty("isFirstTutoring") boolean isFirstTutoring,
        FirstLesson firstLesson,
        List<ParticipantItem> participants,
        @JsonProperty("hasPlan") boolean hasPlan,
        // 후불이면 null.
        FirstInvoice firstInvoice,
        boolean studentInviteQueued
) {

    public record FirstLesson(
            LocalDate date,
            int dayOfWeek,
            LocalTime startTime
    ) {
    }

    public record ParticipantItem(
            Long participantId,
            ParticipantRole role,
            // 학생은 null.
            ParticipantLabel label,
            String linkUrl
    ) {
    }

    public record FirstInvoice(
            Long invoiceId,
            String invoiceName,
            int amount,
            String bankCode,
            String bankName,
            String accountLast4,
            String accountHolder
    ) {
    }
}
