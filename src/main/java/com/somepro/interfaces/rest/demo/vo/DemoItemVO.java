package com.somepro.interfaces.rest.demo.vo;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * demo 对外返回对象（VO，用户接口层）—— 不可变 record。
 *
 * 用 record 的理由：VO 是纯数据载体，没有行为、创建后不再修改，也不需要被框架反射注入，
 * record 正好表达「一组不可变的对外字段」。Jackson（Spring Boot 3.4 自带 2.18+）原生支持
 * record 的序列化与反序列化，无需任何额外配置。
 *
 * 只暴露允许外部看到的字段。刻意不含：
 * - delFlag：内部软删状态
 * - createBy / updateBy：内部审计人
 * - updateTime：内部维护时间
 * 这些字段留在领域对象与 PO 里，不进 API 契约 —— 改库表不会连带改接口。
 */
public record DemoItemVO(Long id, String name, Integer score, LocalDateTime createTime) implements Serializable {
}
