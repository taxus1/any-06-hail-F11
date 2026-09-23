package com.somepro.interfaces.rest.demo;

import com.somepro.application.demo.DemoItemAppService;
import com.somepro.common.Result;
import com.somepro.interfaces.rest.demo.converter.DemoItemVoConverter;
import com.somepro.interfaces.rest.demo.vo.DemoItemVO;
import com.somepro.interfaces.rest.demo.vo.PageVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 用户接口层（示例，可删）：只做协议适配（参数解析、VO 转换、返回包装），业务编排交给应用层。
 *  - 统一返回 Mono<Result<T>>
 *  - 分页透传 pageNum/pageSize，不要写死
 *  - ⚠️ 不直接返回领域对象：一律经 DemoItemVoConverter 转成 VO，
 *    否则 delFlag / createBy / updateBy 等内部字段会被序列化出去
 * 实际业务模块按 interfaces/rest/<context> 组织。
 */
@RestController
@RequestMapping("/api/demo")
public class DemoController {

    private final DemoItemAppService demoItemAppService;

    public DemoController(DemoItemAppService demoItemAppService) {
        this.demoItemAppService = demoItemAppService;
    }

    @GetMapping("/ping")
    public Mono<Result<String>> ping() {
        return Mono.just(Result.ok("pong"));
    }

    @PostMapping("/item")
    public Mono<Result<DemoItemVO>> create(@RequestParam String name,
                                          @RequestParam(required = false) Integer score) {
        return demoItemAppService.createItem(name, score)
                .map(DemoItemVoConverter::toVo)
                .map(Result::ok);
    }

    @GetMapping("/list")
    public Mono<Result<PageVO<DemoItemVO>>> list(@RequestParam(defaultValue = "1") int pageNum,
                                                     @RequestParam(defaultValue = "20") int pageSize,
                                                     @RequestParam(required = false) String name) {
        return demoItemAppService.pageItems(pageNum, pageSize, name)
                .map(DemoItemVoConverter::toPageVo)
                .map(Result::ok);
    }

    @GetMapping("/cache")
    public Mono<Result<String>> cache(@RequestParam String key, @RequestParam String value) {
        return demoItemAppService.cacheDemo(key, value).map(Result::ok);
    }
}
