-- 초기 스키마. dev 브랜치 엔티티에서 Hibernate가 만든 DDL을 옮겼다 (#57).
-- 이미 공유된 마이그레이션이므로 수정하지 않는다. 스키마를 바꾸려면 새 버전 파일을 추가한다.

CREATE TABLE `terms` (
    `is_required` bit(1) NOT NULL,
    `version` int NOT NULL,
    `effective_at` datetime(6) NOT NULL,
    `id` bigint NOT NULL AUTO_INCREMENT,
    `title` varchar(100) NOT NULL,
    `code` enum('CONTACT','PRIVACY','SERVICE') NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_terms_code_version` (`code`,`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `users` (
    `is_admin` bit(1) NOT NULL,
    `created_at` datetime(6) NOT NULL,
    `id` bigint NOT NULL AUTO_INCREMENT,
    `updated_at` datetime(6) NOT NULL,
    `withdrawn_at` datetime(6) DEFAULT NULL,
    `email` varchar(255) DEFAULT NULL,
    `provider_id` varchar(255) DEFAULT NULL,
    `provider` enum('KAKAO') NOT NULL,
    `role` enum('PARENT','STUDENT','TEACHER') NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_users_provider_provider_id` (`provider`,`provider_id`),
    UNIQUE KEY `uk_users_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `teacher_profiles` (
    `created_at` datetime(6) NOT NULL,
    `updated_at` datetime(6) NOT NULL,
    `user_id` bigint NOT NULL,
    `name` varchar(10) NOT NULL,
    `subject_other` varchar(20) DEFAULT NULL,
    `subjects` json NOT NULL,
    PRIMARY KEY (`user_id`),
    CONSTRAINT `fk_teacher_profiles_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `user_terms_agreements` (
    `agreed_at` datetime(6) NOT NULL,
    `id` bigint NOT NULL AUTO_INCREMENT,
    `terms_id` bigint NOT NULL,
    `user_id` bigint NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_terms_agreements_user_id_terms_id` (`user_id`,`terms_id`),
    KEY `fk_user_terms_agreements_terms_id` (`terms_id`),
    CONSTRAINT `fk_user_terms_agreements_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
    CONSTRAINT `fk_user_terms_agreements_terms_id` FOREIGN KEY (`terms_id`) REFERENCES `terms` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `tutorings` (
    `grade_level` tinyint NOT NULL,
    `grade_school_year` smallint NOT NULL,
    `created_at` datetime(6) NOT NULL,
    `deleted_at` datetime(6) DEFAULT NULL,
    `ended_at` datetime(6) DEFAULT NULL,
    `id` bigint NOT NULL AUTO_INCREMENT,
    `teacher_id` bigint NOT NULL,
    `updated_at` datetime(6) NOT NULL,
    `grade_other` varchar(10) DEFAULT NULL,
    `student_name` varchar(10) DEFAULT NULL,
    `subject_other` varchar(10) DEFAULT NULL,
    `status` enum('ACTIVE','ENDED') NOT NULL,
    `subjects` json NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_tutorings_teacher_status` (`teacher_id`,`status`),
    CONSTRAINT `fk_tutorings_teacher_id` FOREIGN KEY (`teacher_id`) REFERENCES `teacher_profiles` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `cycles` (
    `cycle_no` int NOT NULL,
    `id` bigint NOT NULL AUTO_INCREMENT,
    `settled_at` datetime(6) DEFAULT NULL,
    `started_at` datetime(6) NOT NULL,
    `tutoring_id` bigint NOT NULL,
    `status` enum('IN_PROGRESS','SETTLED') NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cycles_tutoring_cycle_no` (`tutoring_id`,`cycle_no`),
    CONSTRAINT `fk_cycles_tutoring_id` FOREIGN KEY (`tutoring_id`) REFERENCES `tutorings` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `participants` (
    `created_at` datetime(6) NOT NULL,
    `guardian_consent_at` datetime(6) DEFAULT NULL,
    `guardian_consent_by` bigint DEFAULT NULL,
    `id` bigint NOT NULL AUTO_INCREMENT,
    `link_expires_at` datetime(6) DEFAULT NULL,
    `link_issued_at` datetime(6) DEFAULT NULL,
    `notification_consent_at` datetime(6) DEFAULT NULL,
    `removed_at` datetime(6) DEFAULT NULL,
    `tutoring_id` bigint NOT NULL,
    `updated_at` datetime(6) NOT NULL,
    `user_id` bigint DEFAULT NULL,
    `phone` varchar(11) DEFAULT NULL,
    `link_token` varchar(64) DEFAULT NULL,
    `label` enum('FATHER','GUARDIAN','MOTHER') DEFAULT NULL,
    `notification_consent` enum('AGREED','PENDING','REJECTED') NOT NULL DEFAULT 'PENDING',
    `receive_method` enum('APP','LINK','NONE') NOT NULL DEFAULT 'LINK',
    `role` enum('PARENT','STUDENT') NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_participants_tutoring_user` (`tutoring_id`,`user_id`),
    UNIQUE KEY `uk_participants_link_token` (`link_token`),
    KEY `idx_participants_tutoring_role` (`tutoring_id`,`role`),
    KEY `fk_participants_guardian_consent_by` (`guardian_consent_by`),
    KEY `fk_participants_user_id` (`user_id`),
    CONSTRAINT `fk_participants_guardian_consent_by` FOREIGN KEY (`guardian_consent_by`) REFERENCES `participants` (`id`),
    CONSTRAINT `fk_participants_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
    CONSTRAINT `fk_participants_tutoring_id` FOREIGN KEY (`tutoring_id`) REFERENCES `tutorings` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `textbooks` (
    `start_position` smallint NOT NULL DEFAULT '0',
    `custom_unit_label` varchar(5) DEFAULT NULL,
    `created_at` datetime(6) NOT NULL,
    `ended_at` datetime(6) DEFAULT NULL,
    `id` bigint NOT NULL AUTO_INCREMENT,
    `tutoring_id` bigint NOT NULL,
    `name` varchar(20) NOT NULL,
    `progress_unit` enum('CHAPTER','CUSTOM','PAGE','ROUND','SECTION','UNIT') NOT NULL,
    PRIMARY KEY (`id`),
    KEY `fk_textbooks_tutoring_id` (`tutoring_id`),
    CONSTRAINT `fk_textbooks_tutoring_id` FOREIGN KEY (`tutoring_id`) REFERENCES `tutorings` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `tutoring_policies` (
    `effective_cycle_no` int NOT NULL,
    `same_day_cancel_recognized` bit(1) NOT NULL DEFAULT b'0',
    `settlement_session_cnt` tinyint NOT NULL,
    `tuition_fee` int NOT NULL,
    `created_at` datetime(6) NOT NULL,
    `id` bigint NOT NULL AUTO_INCREMENT,
    `tutoring_id` bigint NOT NULL,
    `account_holder` varchar(20) NOT NULL,
    `bank_name` varchar(20) NOT NULL,
    `account_last4` char(4) NOT NULL,
    `account_number` varchar(255) NOT NULL,
    `bank_code` char(3) DEFAULT NULL,
    `payment_timing` enum('POSTPAID','PREPAID') NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_tutoring_policies_tutoring_cycle` (`tutoring_id`,`effective_cycle_no`,`id`),
    CONSTRAINT `fk_tutoring_policies_tutoring_id` FOREIGN KEY (`tutoring_id`) REFERENCES `tutorings` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `tutoring_schedules` (
    `day_of_week` tinyint NOT NULL,
    `effective_from` date NOT NULL,
    `effective_to` date DEFAULT NULL,
    `end_time` time NOT NULL,
    `start_time` time NOT NULL,
    `created_at` datetime(6) NOT NULL,
    `id` bigint NOT NULL AUTO_INCREMENT,
    `tutoring_id` bigint NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_tutoring_schedules_tutoring_effective_to` (`tutoring_id`,`effective_to`),
    CONSTRAINT `fk_tutoring_schedules_tutoring_id` FOREIGN KEY (`tutoring_id`) REFERENCES `tutorings` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `tutoring_settings` (
    `homework_notify_enabled` bit(1) NOT NULL,
    `unpaid_reminder_enabled` bit(1) NOT NULL DEFAULT b'0',
    `tutoring_id` bigint NOT NULL,
    `updated_at` datetime(6) NOT NULL,
    `parent_update_frequency` enum('AT_SETTLEMENT','EVERY_SESSION','NONE') NOT NULL,
    `preset_type` enum('MINIMAL','SIMPLE','THOROUGH') DEFAULT NULL,
    PRIMARY KEY (`tutoring_id`),
    CONSTRAINT `fk_tutoring_settings_tutoring_id` FOREIGN KEY (`tutoring_id`) REFERENCES `tutorings` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `invoices` (
    `amount` int NOT NULL,
    `calculated_amount` int NOT NULL,
    `recognized_count` tinyint DEFAULT NULL,
    `refundable_amount` int DEFAULT NULL,
    `settlement_session_cnt` tinyint NOT NULL,
    `tuition_fee` int NOT NULL,
    `unit_price` int NOT NULL,
    `billed_cycle_id` bigint NOT NULL,
    `canceled_at` datetime(6) DEFAULT NULL,
    `confirm_requested_at` datetime(6) DEFAULT NULL,
    `confirm_requested_by` bigint DEFAULT NULL,
    `created_at` datetime(6) NOT NULL,
    `id` bigint NOT NULL AUTO_INCREMENT,
    `notified_at` datetime(6) DEFAULT NULL,
    `policy_id` bigint NOT NULL,
    `reminder_sent_at` datetime(6) DEFAULT NULL,
    `renotified_at` datetime(6) DEFAULT NULL,
    `report_id` bigint DEFAULT NULL,
    `tutoring_id` bigint NOT NULL,
    `updated_at` datetime(6) NOT NULL,
    `account_holder` varchar(20) NOT NULL,
    `bank_name` varchar(20) NOT NULL,
    `account_last4` char(4) NOT NULL,
    `account_number` varchar(255) NOT NULL,
    `bank_code` char(3) DEFAULT NULL,
    `kind` enum('CURRENT','FINAL','FIRST','NEXT') NOT NULL,
    `payment_timing` enum('POSTPAID','PREPAID') NOT NULL,
    `snapshot` json NOT NULL,
    `status` enum('CANCELED','CONFIRM_REQUESTED','PAID','PARTIAL','PENDING') NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_invoices_tutoring_status` (`tutoring_id`,`status`),
    KEY `idx_invoices_status_notified` (`status`,`notified_at`),
    KEY `fk_invoices_confirm_requested_by` (`confirm_requested_by`),
    KEY `fk_invoices_policy_id` (`policy_id`),
    CONSTRAINT `fk_invoices_policy_id` FOREIGN KEY (`policy_id`) REFERENCES `tutoring_policies` (`id`),
    CONSTRAINT `fk_invoices_confirm_requested_by` FOREIGN KEY (`confirm_requested_by`) REFERENCES `participants` (`id`),
    CONSTRAINT `fk_invoices_tutoring_id` FOREIGN KEY (`tutoring_id`) REFERENCES `tutorings` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `link_shares` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `participant_id` bigint DEFAULT NULL,
    `requested_at` datetime(6) NOT NULL,
    `sent_at` datetime(6) DEFAULT NULL,
    `source_id` bigint DEFAULT NULL,
    `teacher_id` bigint NOT NULL,
    `tutoring_id` bigint DEFAULT NULL,
    `chat_type` varchar(20) DEFAULT NULL,
    `hash_chat_id` varchar(100) DEFAULT NULL,
    `channel` enum('APP','KAKAO_SHARE','LINK_PAGE') NOT NULL,
    `kind` enum('EVENT_SHARE','FIRST_INVOICE','HOMEWORK','INVITE','INVITE_ACCEPTED','INVITE_RESEND','LESSON_PLAN','LESSON_UPDATE','MAKEUP','PAYMENT_RESEND','SETTLEMENT_REPORT','TIME_CHANGE') NOT NULL,
    `sent_via` enum('MANUAL','SYSTEM','WEBHOOK') DEFAULT NULL,
    `source_type` enum('CURRICULUM','INVOICE','MAKEUP_REQUEST','REPORT','SESSION','TEACHER_EVENT','TIME_CHANGE') DEFAULT NULL,
    `status` enum('DISMISSED','REQUESTED','SENT','VIEWED') NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_link_shares_tutoring_kind_requested` (`tutoring_id`,`kind`,`requested_at`),
    KEY `idx_link_shares_source` (`source_type`,`source_id`),
    KEY `idx_link_shares_teacher_status_sent` (`teacher_id`,`status`,`sent_at`),
    KEY `fk_link_shares_participant_id` (`participant_id`),
    CONSTRAINT `fk_link_shares_tutoring_id` FOREIGN KEY (`tutoring_id`) REFERENCES `tutorings` (`id`),
    CONSTRAINT `fk_link_shares_teacher_id` FOREIGN KEY (`teacher_id`) REFERENCES `teacher_profiles` (`user_id`),
    CONSTRAINT `fk_link_shares_participant_id` FOREIGN KEY (`participant_id`) REFERENCES `participants` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sessions` (
    `end_time` time DEFAULT NULL,
    `is_recognized` bit(1) DEFAULT NULL,
    `original_date` date DEFAULT NULL,
    `session_date` date NOT NULL,
    `start_time` time DEFAULT NULL,
    `created_at` datetime(6) NOT NULL,
    `cycle_id` bigint DEFAULT NULL,
    `id` bigint NOT NULL AUTO_INCREMENT,
    `schedule_id` bigint DEFAULT NULL,
    `tutoring_id` bigint NOT NULL,
    `updated_at` datetime(6) NOT NULL,
    `session_type` enum('EXTRA','MAKEUP','REGULAR') NOT NULL,
    `status` enum('CANCELED','COMPLETED','SCHEDULED') NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sessions_schedule_original_date` (`schedule_id`,`original_date`),
    KEY `idx_sessions_tutoring_date` (`tutoring_id`,`session_date`),
    KEY `idx_sessions_cycle_recognized_date` (`cycle_id`,`is_recognized`,`session_date`),
    CONSTRAINT `fk_sessions_cycle_id` FOREIGN KEY (`cycle_id`) REFERENCES `cycles` (`id`),
    CONSTRAINT `fk_sessions_tutoring_id` FOREIGN KEY (`tutoring_id`) REFERENCES `tutorings` (`id`),
    CONSTRAINT `fk_sessions_schedule_id` FOREIGN KEY (`schedule_id`) REFERENCES `tutoring_schedules` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `settlement_reports` (
    `canceled_count` tinyint NOT NULL,
    `completed_count` tinyint NOT NULL,
    `homework_rate` tinyint DEFAULT NULL,
    `prev_homework_rate` tinyint DEFAULT NULL,
    `target_count` tinyint NOT NULL,
    `understanding_high` tinyint NOT NULL,
    `understanding_low` tinyint NOT NULL,
    `understanding_medium` tinyint NOT NULL,
    `comment_reviewed_at` datetime(6) DEFAULT NULL,
    `created_at` datetime(6) NOT NULL,
    `cycle_id` bigint NOT NULL,
    `id` bigint NOT NULL AUTO_INCREMENT,
    `sent_at` datetime(6) DEFAULT NULL,
    `tutoring_id` bigint NOT NULL,
    `updated_at` datetime(6) NOT NULL,
    `comment` text,
    `comment_source` enum('AI_DRAFT','AI_EDITED','TEACHER') DEFAULT NULL,
    `delivery_method` enum('KAKAO_SHARE','MANUAL','SCHEDULED_PUSH') DEFAULT NULL,
    `status` enum('FAILED','GENERATED','SCHEDULED','SENT') NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_settlement_reports_cycle_id` (`cycle_id`),
    KEY `idx_settlement_reports_tutoring_status` (`tutoring_id`,`status`),
    CONSTRAINT `fk_settlement_reports_cycle_id` FOREIGN KEY (`cycle_id`) REFERENCES `cycles` (`id`),
    CONSTRAINT `fk_settlement_reports_tutoring_id` FOREIGN KEY (`tutoring_id`) REFERENCES `tutorings` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `invoice_amount_adjustments` (
    `after_amount` int NOT NULL,
    `before_amount` int NOT NULL,
    `created_at` datetime(6) NOT NULL,
    `id` bigint NOT NULL AUTO_INCREMENT,
    `invoice_id` bigint NOT NULL,
    `reason` varchar(200) NOT NULL,
    `adjusted_by` enum('SYSTEM','TEACHER') NOT NULL DEFAULT 'TEACHER',
    PRIMARY KEY (`id`),
    KEY `fk_invoice_amount_adjustments_invoice_id` (`invoice_id`),
    CONSTRAINT `fk_invoice_amount_adjustments_invoice_id` FOREIGN KEY (`invoice_id`) REFERENCES `invoices` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `invoice_payments` (
    `amount` int NOT NULL,
    `canceled_at` datetime(6) DEFAULT NULL,
    `confirmed_at` datetime(6) NOT NULL,
    `id` bigint NOT NULL AUTO_INCREMENT,
    `invoice_id` bigint NOT NULL,
    PRIMARY KEY (`id`),
    KEY `fk_invoice_payments_invoice_id` (`invoice_id`),
    CONSTRAINT `fk_invoice_payments_invoice_id` FOREIGN KEY (`invoice_id`) REFERENCES `invoices` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `session_progress` (
    `end_position` smallint NOT NULL,
    `start_position` smallint NOT NULL,
    `id` bigint NOT NULL AUTO_INCREMENT,
    `session_id` bigint NOT NULL,
    `textbook_id` bigint NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session_progress_session_textbook` (`session_id`,`textbook_id`),
    KEY `fk_session_progress_textbook_id` (`textbook_id`),
    CONSTRAINT `fk_session_progress_textbook_id` FOREIGN KEY (`textbook_id`) REFERENCES `textbooks` (`id`),
    CONSTRAINT `fk_session_progress_session_id` FOREIGN KEY (`session_id`) REFERENCES `sessions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `session_records` (
    `created_at` datetime(6) NOT NULL,
    `id` bigint NOT NULL AUTO_INCREMENT,
    `session_id` bigint NOT NULL,
    `updated_at` datetime(6) NOT NULL,
    `next_homework` varchar(100) DEFAULT NULL,
    `observation_memo` varchar(100) DEFAULT NULL,
    `previous_homework_result` enum('DONE','NOT_DONE','PARTIAL') DEFAULT NULL,
    `understanding` enum('HIGH','LOW','MEDIUM') NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session_records_session_id` (`session_id`),
    CONSTRAINT `fk_session_records_session_id` FOREIGN KEY (`session_id`) REFERENCES `sessions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `settlement_report_progress` (
    `sort_order` tinyint NOT NULL,
    `id` bigint NOT NULL AUTO_INCREMENT,
    `report_id` bigint NOT NULL,
    `textbook_id` bigint NOT NULL,
    `textbook_name` varchar(20) NOT NULL,
    `end_label` varchar(30) NOT NULL,
    `start_label` varchar(30) NOT NULL,
    `textbook_status` enum('ACTIVE','ADDED','ENDED') NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_settlement_report_progress_report_textbook` (`report_id`,`textbook_id`),
    KEY `fk_settlement_report_progress_textbook_id` (`textbook_id`),
    CONSTRAINT `fk_settlement_report_progress_report_id` FOREIGN KEY (`report_id`) REFERENCES `settlement_reports` (`id`),
    CONSTRAINT `fk_settlement_report_progress_textbook_id` FOREIGN KEY (`textbook_id`) REFERENCES `textbooks` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `settlement_report_views` (
    `first_viewed_at` datetime(6) NOT NULL,
    `participant_id` bigint NOT NULL,
    `report_id` bigint NOT NULL,
    PRIMARY KEY (`participant_id`,`report_id`),
    KEY `fk_settlement_report_views_report_id` (`report_id`),
    CONSTRAINT `fk_settlement_report_views_participant_id` FOREIGN KEY (`participant_id`) REFERENCES `participants` (`id`),
    CONSTRAINT `fk_settlement_report_views_report_id` FOREIGN KEY (`report_id`) REFERENCES `settlement_reports` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
