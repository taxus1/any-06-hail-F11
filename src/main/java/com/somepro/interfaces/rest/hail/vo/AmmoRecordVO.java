package com.somepro.interfaces.rest.hail.vo;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 弹药出入库流水对外对象（VO，用户接口层，不可变 record）。
 * changeQty 正入负出；bizType IN/OUT/RETURN/SCRAP；refNo 关联指令编号。
 */
public record AmmoRecordVO(Long id,
                           Long siteId,
                           String ammoType,
                           String batchNo,
                           Integer changeQty,
                           String bizType,
                           String refNo,
                           LocalDateTime createTime) implements Serializable {
}
