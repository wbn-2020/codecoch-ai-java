package com.codecoachai.search.config;

import com.codecoachai.search.service.IndexManageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 服务启动后自动确保 ES 索引存在。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IndexInitRunner implements ApplicationRunner {

    private final IndexManageService indexManageService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            indexManageService.ensureIndices();
            log.info("ES 索引初始化检查完成");
        } catch (Exception ex) {
            log.warn("ES 索引初始化检查失败（ES 可能未启动），服务仍可运行", ex);
        }
    }
}
