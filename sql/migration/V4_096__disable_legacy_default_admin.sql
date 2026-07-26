-- Disable the exact historical repository-seeded administrator credential.
-- Password comparison is byte-exact because the sys_user column uses a
-- case-insensitive collation. A password rotated to any other BCrypt value is
-- therefore not touched.
--
-- The replacement is a syntactically valid cost-12 BCrypt hash generated from a
-- discarded random secret. Keeping a valid hash avoids malformed-hash warnings
-- because the current login flow verifies the password before checking status.

UPDATE sys_user
SET password = '$2b$12$l4hM4xS7DrLKQcz2.ymeAOZN/GICMLRL0vl6ogBs/B5Xpojkddvsq',
    status = 0,
    updated_at = CURRENT_TIMESTAMP
WHERE username = 'admin'
  AND BINARY password =
      BINARY '$2a$10$OuTN8naVk6kfkcyMNiSf.eO3rCVpGr2j7RL.iQvHkM6H/AJoFVtHG'
  AND deleted = 0;
