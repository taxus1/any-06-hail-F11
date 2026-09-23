-- any-06-hail · 气象防雹增雨作业调度 · 建表 SQL
-- 字符集 utf8mb4，时区 Asia/Shanghai。create 阶段建好，模型只写业务代码，不碰建表。
-- 列名即契约：del_flag 由 @TableLogic 自动拼接（查询带 del_flag=0，删除置 1），
-- create_by/update_by/create_time/update_time 由 AutoFillMetaObjectHandler 自动填充，业务代码不要手写。
-- 主键 id 由应用侧雪花分配（IdType.INPUT），不依赖自增。

-- 1) 作业点档案
CREATE TABLE IF NOT EXISTS t_operation_site (
    id            BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花 ID，应用层分配',
    site_code     VARCHAR(32)  NOT NULL COMMENT '作业点编号，全局唯一（如 YY-013）',
    site_name     VARCHAR(128) NOT NULL COMMENT '作业点名称',
    county        VARCHAR(64)  DEFAULT NULL COMMENT '所属县区',
    altitude_m    INT          DEFAULT NULL COMMENT '海拔（米）',
    contact_name  VARCHAR(64)  DEFAULT NULL COMMENT '值守人姓名',
    contact_phone VARCHAR(32)  DEFAULT NULL COMMENT '值守人电话',
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE 在册 / SUSPENDED 封存 / CLOSED 撤销',
    del_flag      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除',
    create_by     VARCHAR(64)  DEFAULT NULL,
    create_time   DATETIME     DEFAULT NULL,
    update_by     VARCHAR(64)  DEFAULT NULL,
    update_time   DATETIME     DEFAULT NULL,
    UNIQUE KEY uk_site_code (site_code),
    KEY idx_county (county)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作业点档案';

-- 2) 发射装备台账（一台装备归属一个作业点）
CREATE TABLE IF NOT EXISTS t_launcher (
    id            BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花 ID，应用层分配',
    launcher_code VARCHAR(32)  NOT NULL COMMENT '装备编号，全局唯一（如 ZB-0007）',
    site_id       BIGINT       NOT NULL COMMENT '归属作业点 id（t_operation_site.id）',
    model         VARCHAR(64)  DEFAULT NULL COMMENT '装备型号',
    barrel_count  INT          DEFAULT NULL COMMENT '发射管数',
    status        VARCHAR(16)  NOT NULL DEFAULT 'READY' COMMENT 'READY 待命 / IN_USE 作业中 / MAINTENANCE 检修 / RETIRED 退役',
    check_date    DATE         DEFAULT NULL COMMENT '最近检验日期',
    del_flag      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除',
    create_by     VARCHAR(64)  DEFAULT NULL,
    create_time   DATETIME     DEFAULT NULL,
    update_by     VARCHAR(64)  DEFAULT NULL,
    update_time   DATETIME     DEFAULT NULL,
    UNIQUE KEY uk_launcher_code (launcher_code),
    KEY idx_site (site_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发射装备台账';

-- 3) 弹药库存（按作业点 + 弹型 + 批次唯一）
CREATE TABLE IF NOT EXISTS t_ammo_stock (
    id           BIGINT      NOT NULL PRIMARY KEY COMMENT '雪花 ID，应用层分配',
    site_id      BIGINT      NOT NULL COMMENT '作业点 id（t_operation_site.id）',
    ammo_type    VARCHAR(32) NOT NULL COMMENT '弹型（如 BL-1A）',
    batch_no     VARCHAR(32) NOT NULL COMMENT '批次号',
    quantity     INT         NOT NULL DEFAULT 0 COMMENT '当前结存发数',
    produce_date DATE        DEFAULT NULL COMMENT '生产日期',
    expire_date  DATE        DEFAULT NULL COMMENT '有效期至（含当日）',
    del_flag     TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除',
    create_by    VARCHAR(64) DEFAULT NULL,
    create_time  DATETIME    DEFAULT NULL,
    update_by    VARCHAR(64) DEFAULT NULL,
    update_time  DATETIME    DEFAULT NULL,
    UNIQUE KEY uk_stock (site_id, ammo_type, batch_no),
    KEY idx_expire (expire_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='弹药库存';

-- 4) 空域申请
CREATE TABLE IF NOT EXISTS t_airspace_apply (
    id            BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花 ID，应用层分配',
    apply_no      VARCHAR(32)  NOT NULL COMMENT '申请编号，全局唯一（如 KQ-2026-0101）',
    site_id       BIGINT       NOT NULL COMMENT '作业点 id（t_operation_site.id）',
    purpose       VARCHAR(16)  NOT NULL COMMENT 'HAIL 防雹 / RAIN 增雨',
    plan_start    DATETIME     NOT NULL COMMENT '拟作业开始时刻',
    plan_end      DATETIME     NOT NULL COMMENT '拟作业结束时刻',
    max_altitude  INT          DEFAULT NULL COMMENT '请求高度上限（米）',
    status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING 待批 / APPROVED 已批 / REJECTED 已驳 / CANCELLED 已撤 / EXPIRED 已失效',
    reject_reason VARCHAR(255) DEFAULT NULL COMMENT '驳回原因',
    approve_time  DATETIME     DEFAULT NULL COMMENT '批复时刻',
    del_flag      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除',
    create_by     VARCHAR(64)  DEFAULT NULL,
    create_time   DATETIME     DEFAULT NULL,
    update_by     VARCHAR(64)  DEFAULT NULL,
    update_time   DATETIME     DEFAULT NULL,
    UNIQUE KEY uk_apply_no (apply_no),
    KEY idx_site (site_id),
    KEY idx_status (status),
    KEY idx_plan (plan_start, plan_end)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='空域申请';

-- 5) 作业指令
CREATE TABLE IF NOT EXISTS t_fire_order (
    id          BIGINT      NOT NULL PRIMARY KEY COMMENT '雪花 ID，应用层分配',
    order_no    VARCHAR(32) NOT NULL COMMENT '指令编号，全局唯一（如 ZY-2026-0101）',
    apply_id    BIGINT      NOT NULL COMMENT '空域申请 id（t_airspace_apply.id）',
    site_id     BIGINT      NOT NULL COMMENT '作业点 id（t_operation_site.id）',
    launcher_id BIGINT      DEFAULT NULL COMMENT '执行装备 id（t_launcher.id）',
    ammo_type   VARCHAR(32) DEFAULT NULL COMMENT '弹型',
    plan_rounds INT         NOT NULL DEFAULT 0 COMMENT '计划用弹发数',
    used_rounds INT         NOT NULL DEFAULT 0 COMMENT '实际用弹发数',
    status      VARCHAR(16) NOT NULL DEFAULT 'ISSUED' COMMENT 'ISSUED 已下达 / EXECUTING 作业中 / DONE 已完成 / VOID 已作废',
    start_time  DATETIME    DEFAULT NULL COMMENT '实际作业开始时刻',
    end_time    DATETIME    DEFAULT NULL COMMENT '实际作业结束时刻',
    void_reason VARCHAR(255) DEFAULT NULL COMMENT '作废原因',
    del_flag    TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除',
    create_by   VARCHAR(64) DEFAULT NULL,
    create_time DATETIME    DEFAULT NULL,
    update_by   VARCHAR(64) DEFAULT NULL,
    update_time DATETIME    DEFAULT NULL,
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_apply (apply_id),
    KEY idx_site (site_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作业指令';

-- 6) 弹药出入库流水（正数入库、负数出库）
CREATE TABLE IF NOT EXISTS t_ammo_record (
    id         BIGINT      NOT NULL PRIMARY KEY COMMENT '雪花 ID，应用层分配',
    site_id    BIGINT      NOT NULL COMMENT '作业点 id（t_operation_site.id）',
    ammo_type  VARCHAR(32) NOT NULL COMMENT '弹型',
    batch_no   VARCHAR(32) DEFAULT NULL COMMENT '批次号',
    change_qty INT         NOT NULL COMMENT '变动发数：正入负出',
    biz_type   VARCHAR(16) NOT NULL COMMENT 'IN 入库 / OUT 领用 / RETURN 退回 / SCRAP 报废',
    ref_no     VARCHAR(32) DEFAULT NULL COMMENT '关联单据号（入库单号或指令编号）',
    del_flag   TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除',
    create_by  VARCHAR(64) DEFAULT NULL,
    create_time DATETIME   DEFAULT NULL,
    update_by  VARCHAR(64) DEFAULT NULL,
    update_time DATETIME   DEFAULT NULL,
    KEY idx_site (site_id),
    KEY idx_ref (ref_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='弹药出入库流水';

-- 7) 作业效果上报（一条指令一份）
CREATE TABLE IF NOT EXISTS t_effect_report (
    id           BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花 ID，应用层分配',
    order_id     BIGINT       NOT NULL COMMENT '作业指令 id（t_fire_order.id）',
    report_time  DATETIME     DEFAULT NULL COMMENT '上报时刻',
    rainfall_mm  DECIMAL(8,2) DEFAULT NULL COMMENT '过程降雨量（毫米）',
    hail_size_mm DECIMAL(6,2) DEFAULT NULL COMMENT '最大冰雹粒径（毫米）',
    area_km2     DECIMAL(10,2) DEFAULT NULL COMMENT '影响面积（平方公里）',
    remark       VARCHAR(255) DEFAULT NULL COMMENT '备注',
    del_flag     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常 / 1 已删除',
    create_by    VARCHAR(64)  DEFAULT NULL,
    create_time  DATETIME     DEFAULT NULL,
    update_by    VARCHAR(64)  DEFAULT NULL,
    update_time  DATETIME     DEFAULT NULL,
    UNIQUE KEY uk_order (order_id),
    KEY idx_report (report_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作业效果上报';
