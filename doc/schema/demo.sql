-- demo 示例模块 · 建表 SQL
-- 字符集 utf8mb4，时区 Asia/Shanghai。create 阶段建好，模型只写业务代码，不碰建表。
-- 列名即契约：del_flag 由 @TableLogic 自动拼接（查询带 del_flag=0，删除置 1），
-- create_by/update_by/create_time/update_time 由 AutoFillMetaObjectHandler 自动填充，业务代码不要手写。

CREATE TABLE IF NOT EXISTS t_demo_item (
    id          BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花 ID，应用层分配（IdType.INPUT）',
    name        VARCHAR(128) NOT NULL,
    score       INT          DEFAULT NULL,
    del_flag    TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除',
    create_by   VARCHAR(64)  DEFAULT NULL,
    create_time DATETIME     DEFAULT NULL,
    update_by   VARCHAR(64)  DEFAULT NULL,
    update_time DATETIME     DEFAULT NULL,
    KEY idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
