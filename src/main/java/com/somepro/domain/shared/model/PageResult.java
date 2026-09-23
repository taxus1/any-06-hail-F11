package com.somepro.domain.shared.model;

import java.util.List;

/**
 * 领域分页值对象（不可变 record）。
 *
 * 存在的原因：领域层不能依赖 Spring Data 的 {@code org.springframework.data.domain.Page}
 * （那是框架类型，会让领域层反向依赖基础设施）。所以仓储端口用它返回分页结果，
 * 由基础设施层把 PageHelper 的结果转换过来，接口层再转成 VO 返回。
 *
 * 用 record 的理由：它是纯数据载体、无行为、创建后不再变化，正适合不可变值对象。
 * 只承载「分页」这一通用概念，不含业务规则，因此接口层直接复用它也是允许的
 * （依赖方向 interfaces → domain 合法）。
 *
 * 访问器是 content() / total() / pageNum() / pageSize()（record 风格，不是 getXxx）。
 */
public record PageResult<T>(List<T> content, long total, int pageNum, int pageSize) {

    /** 紧凑构造器：防御性拷贝，避免外部持有引用后篡改分页内容。 */
    public PageResult {
        content = (content == null) ? List.of() : List.copyOf(content);
    }

    /** 总页数；pageSize 非法时为 0。 */
    public int totalPages() {
        if (pageSize <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) total / pageSize);
    }
}
