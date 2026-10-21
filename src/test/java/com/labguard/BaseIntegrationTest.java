package com.labguard;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * 集成测试基类，所有成员的业务集成测试继承本类。
 *
 * <p>提供与开发环境隔离的测试配置：
 * 内存 H2 数据库、独立的上传目录（target/test-uploads）。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:labguard-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "labguard.storage.upload-dir=target/test-uploads"
})
public abstract class BaseIntegrationTest {
}
