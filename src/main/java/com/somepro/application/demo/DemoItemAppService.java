package com.somepro.application.demo;

import com.somepro.application.demo.port.DemoCachePort;
import com.somepro.domain.demo.model.DemoItem;
import com.somepro.domain.demo.repository.DemoItemRepository;
import com.somepro.domain.shared.model.PageResult;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 应用层：编排用例、定事务边界，不写业务规则（规则在领域层）。
 * 依赖领域端口与自身端口，不直接依赖基础设施实现。
 *
 * 出入参都是领域对象（DemoItem），不认识 PO、也不认识 VO：
 * PO 只在基础设施层出现，VO 只在接口层出现。
 */
@Service
public class DemoItemAppService {

    private final DemoItemRepository demoItemRepository;
    private final DemoCachePort demoCachePort;

    public DemoItemAppService(DemoItemRepository demoItemRepository, DemoCachePort demoCachePort) {
        this.demoItemRepository = demoItemRepository;
        this.demoCachePort = demoCachePort;
    }

    public Mono<DemoItem> createItem(String name, Integer score) {
        DemoItem item = DemoItem.create(name, score);
        return demoItemRepository.save(item);
    }

    public Mono<PageResult<DemoItem>> pageItems(int pageNum, int pageSize, String name) {
        return demoItemRepository.page(pageNum, pageSize, name);
    }

    public Mono<String> cacheDemo(String key, String value) {
        return demoCachePort.setAndGet("demo:" + key, value);
    }
}
