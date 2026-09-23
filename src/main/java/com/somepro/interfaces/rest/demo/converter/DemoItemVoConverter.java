package com.somepro.interfaces.rest.demo.converter;

import com.somepro.domain.demo.model.DemoItem;
import com.somepro.domain.shared.model.PageResult;
import com.somepro.interfaces.rest.demo.vo.DemoItemVO;
import com.somepro.interfaces.rest.demo.vo.PageVO;

import java.util.List;
import java.util.stream.Collectors;

/**
 * DemoItem（领域）→ DemoItemVO（对外）转换器（用户接口层）。
 *
 * 接口层是唯一做领域对象 → VO 转换的地方：Controller 不许直接把领域对象塞进 Result 返回，
 * 否则 delFlag / createBy / updateBy 等内部字段会被无意识序列化出去。
 */
public final class DemoItemVoConverter {

    private DemoItemVoConverter() {
    }

    public static DemoItemVO toVo(DemoItem domain) {
        return new DemoItemVO(domain.getId(), domain.getName(), domain.getScore(), domain.getCreateTime());
    }

    public static PageVO<DemoItemVO> toPageVo(PageResult<DemoItem> page) {
        List<DemoItemVO> content = page.content().stream()
                .map(DemoItemVoConverter::toVo)
                .collect(Collectors.toList());
        return new PageVO<>(content, page.total(), page.pageNum(), page.pageSize(), page.totalPages());
    }
}
