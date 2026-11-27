ALTER TABLE teacher_profiles ADD COLUMN first_tutoring_created_at datetime(6) NULL;

-- 이미 과외가 있는 선생님은 가장 이른 과외 생성 시각으로 채운다 (삭제된 과외 포함).
UPDATE teacher_profiles tp
SET tp.first_tutoring_created_at = (SELECT MIN(t.created_at) FROM tutorings t WHERE t.teacher_id = tp.user_id)
WHERE tp.first_tutoring_created_at IS NULL;
