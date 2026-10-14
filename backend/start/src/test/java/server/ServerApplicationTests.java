package server;

import com.dianping.CommentApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;

// 显式开启完整外部环境检查，需要测试专用的数据库及 Redis。
@EnabledIfSystemProperty(named = "legacy.context.enabled", matches = "true")
@SpringBootTest(
        classes = CommentApplication.class,
        properties = "security.jwt.secret=dGhpcy1pcy1hLXRlc3Qtc2VjcmV0LXdpdGgtMzItYnl0ZXM=")
class ServerApplicationTests {

    @Test
    void contextLoads() {
        Instant now = Instant.now();
        System.out.println(now.toString());
    }

}
