package com.somepro.infrastructure.config;

import com.github.pagehelper.PageInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

/**
 * MyBatis-Plus 装配（基础设施层）。
 *
 * - @MapperScan 显式指定 Mapper 所在包（com.somepro.infrastructure.persistence），
 *   避免依赖自动扫描的隐式行为。新增模块把 Mapper 放在该包下即可被扫描到。
 * - 分页插件在这里手动注册为 Interceptor bean：
 *   没有用 pagehelper-spring-boot-starter（它会拖进 Spring Boot 2 体系的 mybatis-spring-boot-starter，
 *   和 MyBatis-Plus 抢 SqlSessionFactory）。MyBatis-Plus 的自动装配会把 Interceptor bean
 *   通过 setPlugins(...) 挂到插件链上，效果等价。
 * - 这里**故意不注册** MyBatis-Plus 的 PaginationInnerInterceptor：
 *   本项目统一用 PageHelper 做分页，两套分页插件共存会互相干扰 SQL。
 *   分页一律走 PageHelper.startPage(...)，不要用 MyBatis-Plus 的 IPage。
 * - 审计填充由 AutoFillMetaObjectHandler 承担。
 */
@Configuration
@MapperScan("com.somepro.infrastructure.persistence")
public class MybatisPlusConfig {

    @Bean
    public PageInterceptor pageInterceptor(
            @Value("${pagehelper.helper-dialect:mysql}") String helperDialect,
            @Value("${pagehelper.reasonable:false}") boolean reasonable,
            @Value("${pagehelper.support-methods-arguments:true}") boolean supportMethodsArguments) {
        PageInterceptor interceptor = new PageInterceptor();
        Properties properties = new Properties();
        properties.setProperty("helperDialect", helperDialect);
        properties.setProperty("reasonable", String.valueOf(reasonable));
        properties.setProperty("supportMethodsArguments", String.valueOf(supportMethodsArguments));
        properties.setProperty("params", "count=countSql");
        interceptor.setProperties(properties);
        return interceptor;
    }
}
