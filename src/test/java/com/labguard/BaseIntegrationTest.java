package com.labguard;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * 集成测试基类，所有成员的业务集成测试继承本类。
 *
 * <p>提供与开发环境隔离的测试配置：SQLite 临时文件库（target/ 下，随构建清理）、
 * 独立的上传目录（target/test-uploads），启动时自动执行 classpath:schema.sql。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite:target/labguard-integration-test.db",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema.sql",
        "labguard.storage.root=target/test-uploads"
})
public abstract class BaseIntegrationTest {
}
