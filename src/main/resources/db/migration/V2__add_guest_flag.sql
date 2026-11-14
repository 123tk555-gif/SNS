-- V2: お試しログイン(登録せずに試せる、みんなで使う 1 つのアカウント)の印。
-- 今までのユーザーは、すべて false(お試し用ではない)
ALTER TABLE users ADD COLUMN guest boolean NOT NULL DEFAULT false;
