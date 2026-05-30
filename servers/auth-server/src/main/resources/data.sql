-- 初始用户数据（密码是 BCrypt 加密后的 "password"）
-- $2a$10$GRLdNijSQMUvl/au9ofL.eDwmoohzzS7.rmNSJZ.0FxO/BTk76klW = "password"
INSERT IGNORE INTO sys_user (username, password, nickname, roles, enabled)
VALUES ('admin', '$2a$10$GRLdNijSQMUvl/au9ofL.eDwmoohzzS7.rmNSJZ.0FxO/BTk76klW', '管理员', 'ROLE_ADMIN,ROLE_USER', 1);

INSERT IGNORE INTO sys_user (username, password, nickname, roles, enabled)
VALUES ('user', '$2a$10$GRLdNijSQMUvl/au9ofL.eDwmoohzzS7.rmNSJZ.0FxO/BTk76klW', '普通用户', 'ROLE_USER', 1);
