package com.somepro.interfaces.rest.demo.vo;

import java.io.Serializable;
import java.util.List;

/**
 * 对外分页返回对象（VO，用户接口层）—— 不可变 record。
 *
 * 与领域层 {@code PageResult} 的分工：
 * - PageResult 只有 4 个组件（content/total/pageNum/pageSize），因为 record 只序列化组件，
 *   若为了让 totalPages 出现在 JSON 里而给它加 {@code @JsonProperty}，就会把 Jackson 引进领域层。
 * - 所以派生字段放在接口层的 VO 上：这里额外提供 totalPages，方便前端直接渲染分页器。
 *
 * 领域层保持零框架依赖，序列化相关的取舍留在接口层 —— 这是分层的意义。
 */
public record PageVO<T>(List<T> content, long total, int pageNum, int pageSize, int totalPages)
        implements Serializable {
}
