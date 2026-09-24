package com.somepro.interfaces.rest.hail.vo;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 作业点对外对象（VO，用户接口层，不可变 record）。
 * 只暴露业务字段，不含 delFlag / 审计人等内部字段。
 */
public record OperationSiteVO(Long id,
                              String siteCode,
                              String siteName,
                              String county,
                              Integer altitudeM,
                              String contactName,
                              String contactPhone,
                              String status,
                              LocalDateTime createTime) implements Serializable {
}
