-- 약관 시드. 부팅마다 실행된다. 로컬(ddl-auto: create)은 매번 새로 들어가고,
-- 개발 서버(update)는 이미 있는 행을 UNIQUE(code, version)로 건너뛴다 (INSERT IGNORE).
-- 내용이 바뀌면 같은 code의 version을 올린 행을 추가한다 (기존 행은 그대로 둔다).
INSERT IGNORE INTO terms (code, version, title, is_required, effective_at) VALUES
    ('SERVICE', 1, '서비스 이용약관', true, '2026-01-01 00:00:00'),
    ('PRIVACY', 1, '개인정보 수집 · 이용', true, '2026-01-01 00:00:00'),
    ('CONTACT', 1, '학부모 · 학생 연락처 처리', true, '2026-01-01 00:00:00');
