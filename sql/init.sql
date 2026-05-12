CREATE DATABASE IF NOT EXISTS codecoachai_v1
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE codecoachai_v1;

CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'primary id',
  username VARCHAR(64) NOT NULL COMMENT 'login username',
  password VARCHAR(255) NOT NULL COMMENT 'encrypted password hash',
  nickname VARCHAR(64) DEFAULT NULL COMMENT 'nickname',
  avatar VARCHAR(500) DEFAULT NULL COMMENT 'avatar url',
  email VARCHAR(128) DEFAULT NULL COMMENT 'email',
  phone VARCHAR(32) DEFAULT NULL COMMENT 'phone',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1 enabled, 0 disabled',
  last_login_time DATETIME DEFAULT NULL COMMENT 'last login time',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0 active, 1 deleted',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_user_username (username),
  KEY idx_sys_user_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='system user';

CREATE TABLE IF NOT EXISTS sys_role (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'primary id',
  role_code VARCHAR(64) NOT NULL COMMENT 'role code',
  role_name VARCHAR(64) NOT NULL COMMENT 'role name',
  description VARCHAR(255) DEFAULT NULL COMMENT 'role description',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1 enabled, 0 disabled',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0 active, 1 deleted',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_role_code (role_code),
  KEY idx_sys_role_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='system role';

CREATE TABLE IF NOT EXISTS sys_user_role (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'primary id',
  user_id BIGINT NOT NULL COMMENT 'user id',
  role_id BIGINT NOT NULL COMMENT 'role id',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '0 active, 1 deleted',
  PRIMARY KEY (id),
  UNIQUE KEY uk_sys_user_role_user_role (user_id, role_id),
  KEY idx_sys_user_role_user_id (user_id),
  KEY idx_sys_user_role_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='system user role';

INSERT INTO sys_role (id, role_code, role_name, description, status)
VALUES
  (1, 'USER', 'User', 'Default user role', 1),
  (2, 'ADMIN', 'Admin', 'Default admin role', 1)
ON DUPLICATE KEY UPDATE
  role_name = VALUES(role_name),
  description = VALUES(description),
  status = VALUES(status);

INSERT INTO sys_user (id, username, password, nickname, email, status)
VALUES
  (1, 'admin', '$2a$10$OuTN8naVk6kfkcyMNiSf.eO3rCVpGr2j7RL.iQvHkM6H/AJoFVtHG', 'System Admin', 'admin@codecoachai.local', 1)
ON DUPLICATE KEY UPDATE
  nickname = VALUES(nickname),
  email = VALUES(email),
  status = VALUES(status);

INSERT INTO sys_user_role (user_id, role_id)
VALUES
  (1, 2)
ON DUPLICATE KEY UPDATE
  role_id = VALUES(role_id);
