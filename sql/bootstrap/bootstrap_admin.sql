-- Explicit one-time administrator bootstrap.
--
-- Set these variables in the same interactive MySQL session before sourcing:
--   @bootstrap_admin_username      required, 4-32 letters/digits/underscores
--   @bootstrap_admin_password_hash required, 60-character BCrypt hash, cost >= 12
--   @bootstrap_admin_nickname      optional
--   @bootstrap_admin_email         optional
--
-- This script never accepts a plaintext password. It refuses to create another
-- administrator when an enabled ADMIN user already exists. A schema-scoped
-- advisory lock serializes concurrent bootstrap attempts.

SET @bootstrap_admin_username =
    NULLIF(TRIM(@bootstrap_admin_username), '');
SET @bootstrap_admin_password_hash =
    NULLIF(TRIM(@bootstrap_admin_password_hash), '');
SET @bootstrap_admin_nickname =
    COALESCE(NULLIF(TRIM(@bootstrap_admin_nickname), ''), @bootstrap_admin_username);
SET @bootstrap_admin_email =
    NULLIF(TRIM(@bootstrap_admin_email), '');

DELIMITER //
DROP PROCEDURE IF EXISTS bootstrap_codecoachai_admin//
CREATE PROCEDURE bootstrap_codecoachai_admin()
bootstrap: BEGIN
    DECLARE current_schema VARCHAR(64) DEFAULT NULL;
    DECLARE admin_role_id BIGINT DEFAULT NULL;
    DECLARE bootstrap_user_id BIGINT DEFAULT NULL;
    DECLARE active_admin_count BIGINT DEFAULT 0;
    DECLARE username_count BIGINT DEFAULT 0;
    DECLARE bcrypt_cost INT DEFAULT 0;
    DECLARE bootstrap_lock_acquired INT DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        IF bootstrap_lock_acquired = 1 THEN
            DO RELEASE_LOCK(CONCAT('codecoachai:bootstrap-admin:', current_schema));
        END IF;
        SET @bootstrap_admin_username = NULL;
        SET @bootstrap_admin_password_hash = NULL;
        SET @bootstrap_admin_nickname = NULL;
        SET @bootstrap_admin_email = NULL;
        RESIGNAL;
    END;

    SET current_schema = DATABASE();
    IF current_schema IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'select the CodeCoachAI database before running bootstrap';
    END IF;

    IF @bootstrap_admin_username IS NULL
       OR @bootstrap_admin_username NOT REGEXP '^[A-Za-z0-9_]{4,32}$' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'bootstrap username must contain 4-32 letters, digits, or underscores';
    END IF;

    IF @bootstrap_admin_password_hash IS NULL
       OR CHAR_LENGTH(@bootstrap_admin_password_hash) <> 60
       OR LEFT(@bootstrap_admin_password_hash, 4) NOT IN ('$2a$', '$2b$', '$2y$')
       OR SUBSTRING(@bootstrap_admin_password_hash, 5, 2) NOT REGEXP '^[0-9]{2}$'
       OR SUBSTRING(@bootstrap_admin_password_hash, 7, 1) <> '$'
       OR SUBSTRING(@bootstrap_admin_password_hash, 8, 53)
            NOT REGEXP '^[./A-Za-z0-9]{53}$' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'bootstrap password must be supplied as a valid BCrypt hash';
    END IF;

    SET bcrypt_cost = CAST(SUBSTRING(@bootstrap_admin_password_hash, 5, 2) AS UNSIGNED);
    IF bcrypt_cost < 12 OR bcrypt_cost > 31 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'bootstrap BCrypt cost must be between 12 and 31';
    END IF;

    IF CHAR_LENGTH(@bootstrap_admin_nickname) > 50 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'bootstrap nickname must be at most 50 characters';
    END IF;

    IF @bootstrap_admin_email IS NOT NULL
       AND CHAR_LENGTH(@bootstrap_admin_email) > 100 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'bootstrap email must be at most 100 characters';
    END IF;

    SELECT GET_LOCK(
        CONCAT('codecoachai:bootstrap-admin:', current_schema),
        10
    )
    INTO bootstrap_lock_acquired;

    IF COALESCE(bootstrap_lock_acquired, 0) <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'another administrator bootstrap is in progress';
    END IF;

    START TRANSACTION;

    SELECT MIN(id)
    INTO admin_role_id
    FROM sys_role
    WHERE role_code = 'ADMIN'
      AND status = 1
      AND deleted = 0;

    IF admin_role_id IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'enabled ADMIN role does not exist; run Flyway migrations first';
    END IF;

    SELECT COUNT(1)
    INTO active_admin_count
    FROM sys_user u
    JOIN sys_user_role ur
      ON ur.user_id = u.id
     AND ur.deleted = 0
    JOIN sys_role r
      ON r.id = ur.role_id
     AND r.deleted = 0
     AND r.status = 1
     AND r.role_code = 'ADMIN'
    WHERE u.deleted = 0
      AND u.status = 1;

    IF active_admin_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'an enabled administrator already exists; use the normal admin workflow';
    END IF;

    SELECT COUNT(1)
    INTO username_count
    FROM sys_user
    WHERE username = @bootstrap_admin_username;

    IF username_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'bootstrap username already exists; choose another username or use account recovery';
    END IF;

    INSERT INTO sys_user (
        username,
        password,
        nickname,
        email,
        status
    ) VALUES (
        @bootstrap_admin_username,
        @bootstrap_admin_password_hash,
        @bootstrap_admin_nickname,
        @bootstrap_admin_email,
        1
    );

    SET bootstrap_user_id = LAST_INSERT_ID();

    INSERT INTO sys_user_role (user_id, role_id)
    VALUES (bootstrap_user_id, admin_role_id);

    COMMIT;

    DO RELEASE_LOCK(CONCAT('codecoachai:bootstrap-admin:', current_schema));
    SET bootstrap_lock_acquired = 0;
END//
DELIMITER ;

CALL bootstrap_codecoachai_admin();
DROP PROCEDURE IF EXISTS bootstrap_codecoachai_admin;

SET @bootstrap_admin_username = NULL;
SET @bootstrap_admin_password_hash = NULL;
SET @bootstrap_admin_nickname = NULL;
SET @bootstrap_admin_email = NULL;
