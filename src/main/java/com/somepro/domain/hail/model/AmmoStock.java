package com.somepro.domain.hail.model;

import com.somepro.common.exception.BizException;
import com.somepro.domain.shared.model.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 弹药库存聚合根（防雹增雨限界上下文）。
 *
 * 纯领域对象：不带任何持久化注解（表映射在基础设施层的 AmmoStockPO）。
 *
 * 关键业务规则：
 * - 库存按「作业点 + 弹型 + 批次」唯一（库表 uk_stock(site_id, ammo_type, batch_no)）。
 *   同一作业点、同一种弹型、同一个批次再次入库，不另起一条，而是在原记录上累加发数。
 * - 入库发数必须为正；累加由仓储用原子 UPDATE ... SET quantity = quantity + ? 完成，防并发丢更新。
 * - 生产日期 / 有效期为可选项；给了有效期，就不应早于生产日期。
 */
@Getter
@Setter
public class AmmoStock extends BaseEntity {

    private Long id;

    /** 作业点 id（t_operation_site.id），必填。 */
    private Long siteId;

    /** 弹型，如 BL-1A。 */
    private String ammoType;

    /** 批次号。 */
    private String batchNo;

    /** 当前结存发数。 */
    private Integer quantity;

    /** 生产日期。 */
    private LocalDate produceDate;

    /** 有效期至（含当日）。 */
    private LocalDate expireDate;

    /** 工厂方法：首次入库时建立这条库存记录。 */
    public static AmmoStock newStock(Long siteId, String ammoType, String batchNo, int inboundQty,
                                     LocalDate produceDate, LocalDate expireDate) {
        AmmoStock stock = new AmmoStock();
        stock.assignTo(siteId);
        stock.specifyType(ammoType);
        stock.specifyBatch(batchNo);
        stock.validateDates(produceDate, expireDate);
        stock.checkInboundQty(inboundQty);
        stock.quantity = inboundQty;
        stock.produceDate = produceDate;
        stock.expireDate = expireDate;
        return stock;
    }

    /** 领域行为：同点 + 同弹型 + 同批次再次入库，在本记录上累加，不另起一条。 */
    public void addInbound(int inboundQty, LocalDate produceDate, LocalDate expireDate) {
        checkInboundQty(inboundQty);
        this.quantity += inboundQty;
        // 再次入库若补了日期信息，用新值校准（同批次的日期理应一致，以最新登记为准）。
        if (produceDate != null) {
            this.produceDate = produceDate;
        }
        if (expireDate != null) {
            this.expireDate = expireDate;
        }
    }

    public void assignTo(Long siteId) {
        if (siteId == null) {
            throw new BizException("弹药必须归属一个作业点，siteId 不能为空");
        }
        this.siteId = siteId;
    }

    public void specifyType(String ammoType) {
        if (ammoType == null || ammoType.isBlank()) {
            throw new BizException("弹型不能为空，如 BL-1A");
        }
        this.ammoType = ammoType.trim();
    }

    public void specifyBatch(String batchNo) {
        if (batchNo == null || batchNo.isBlank()) {
            throw new BizException("批次号不能为空");
        }
        this.batchNo = batchNo.trim();
    }

    private void checkInboundQty(int inboundQty) {
        if (inboundQty <= 0) {
            throw new BizException("入库发数必须为正整数");
        }
    }

    private void validateDates(LocalDate produceDate, LocalDate expireDate) {
        if (produceDate != null && expireDate != null && expireDate.isBefore(produceDate)) {
            throw new BizException("有效期不能早于生产日期");
        }
    }
}
