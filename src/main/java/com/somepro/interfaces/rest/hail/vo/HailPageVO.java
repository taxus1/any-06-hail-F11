package com.somepro.interfaces.rest.hail.vo;

import java.io.Serializable;
import java.util.List;

/**
 * 防雹增雨模块对外分页结构（用户接口层，不可变 record）。
 * 独立于 demo 模块自带的那份，含义相同：额外带 totalPages 方便前端直接渲染分页器。
 */
public record HailPageVO<T>(List<T> content, long total, int pageNum, int pageSize, int totalPages)
        implements Serializable {
}
