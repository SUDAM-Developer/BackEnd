-- 약관 시드. ddl-auto: create라 부팅마다 스키마 생성 뒤 다시 들어간다.
-- 내용이 바뀌면 같은 code의 version을 올린 행을 추가한다 (기존 행은 그대로 둔다).
INSERT INTO terms (code, version, title, is_required, effective_at) VALUES
    ('SERVICE', 1, '서비스 이용약관', true, '2026-01-01 00:00:00'),
    ('PRIVACY', 1, '개인정보 수집 · 이용', true, '2026-01-01 00:00:00'),
    ('CONTACT', 1, '학부모 · 학생 연락처 처리', true, '2026-01-01 00:00:00');
