package com.dianping.comment;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.dianping.config.MybatisConfig;
import com.dianping.dto.UserDTO;
import com.dianping.entity.BlogComments;
import com.dianping.mapper.BlogCommentsMapper;
import com.dianping.service.BlogCommentsService;
import com.dianping.service.UserService;
import com.dianping.service.impl.BlogCommentsServiceImpl;
import com.dianping.utils.UserHolder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** 使用专用 Redis、真实 Redisson 锁和随机 H2；不加载生产配置，不需要同步字段。 */
@SpringJUnitConfig(CommentLikePersistenceRedisIntegrationTest.Config.class)
@EnabledIfSystemProperty(named = "commentLikeRedisTest", matches = "true")
class CommentLikePersistenceRedisIntegrationTest {
    @Autowired BlogCommentsService service;
    @Autowired BlogCommentsMapper mapper;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate jdbc;
    private Long id;
    private long blogId;

    @BeforeEach void createComment() {
        blogId = 1000000L + Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 1000000000000L);
        jdbc.update("INSERT INTO tb_blog(id,comments) VALUES(?,0)", blogId);
        BlogComments comment = new BlogComments().setBlogId(blogId).setUserId(7L).setParentId(0L)
                .setAnswerId(0L).setContent("隔离测试").setStatus(0).setLiked(0L);
        mapper.insert(comment);
        id = comment.getId();
        identity(7);
    }
    @AfterEach void clearIdentity() { UserHolder.removeUser(); }
    private static void identity(long userId) {
        UserDTO user = new UserDTO(); user.setId(userId); UserHolder.saveUser(user);
    }
    private String key() { return "{comment:likes:" + Math.floorMod(id, 4) + "}:" + id + ":users"; }

    @Test void twoClicksUpdateDatabaseImmediatelyAndDoNotCreateSyncMetadata() {
        assertThat(service.toggleCommentLike(blogId, id).getData()).isEqualTo(new BlogCommentsService.LikeResult(1, true));
        assertThat(mapper.selectById(id).getLiked()).isEqualTo(1);
        assertThat(redis.opsForSet().members(key())).containsExactly("7");
        assertThat(service.toggleCommentLike(blogId, id).getData()).isEqualTo(new BlogCommentsService.LikeResult(0, false));
        assertThat(mapper.selectById(id).getLiked()).isZero();
        assertThat(redis.opsForSet().isMember(key(), "7")).isFalse();
        assertThat(redis.hasKey(key().replace(":users", ":meta"))).isFalse();
        assertThat(redis.hasKey("{comment:likes:" + Math.floorMod(id, 4) + "}:pending")).isFalse();
    }

    @Test void oldRelationSetIsUsedWithoutReinitializationOrMigration() {
        redis.opsForSet().add(key(), "7");
        jdbc.update("UPDATE tb_blog_comments SET liked=1 WHERE id=?", id);
        var page = service.queryComments(blogId, 1).getData();
        var item = page.getRecords().stream().filter(vo -> vo.getId().equals(id)).findFirst().orElseThrow();
        assertThat(item.getIsLike()).isTrue();
        assertThat(item.isLikeAvailable()).isTrue();
        assertThat(service.toggleCommentLike(blogId, id).getData().isLike()).isFalse();
        assertThat(mapper.selectById(id).getLiked()).isZero();
    }

    @Test void concurrentUsersDoNotLoseDatabaseIncrements() throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            List<Callable<Boolean>> calls = new ArrayList<>();
            for (long userId = 1; userId <= 24; userId++) {
                long current = userId;
                calls.add(() -> {
                    identity(current);
                    try { return service.toggleCommentLike(blogId, id).getCode() == 1; }
                    finally { UserHolder.removeUser(); }
                });
            }
            for (Future<Boolean> done : pool.invokeAll(calls)) assertThat(done.get()).isTrue();
        }
        assertThat(mapper.selectById(id).getLiked()).isEqualTo(24);
        assertThat(redis.opsForSet().size(key())).isEqualTo(24);
    }

    @Test void simultaneousClicksFromOneUserAreTwoSerializedSwitches() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> click = () -> {
            start.await(); identity(7);
            try { return service.toggleCommentLike(blogId, id).getCode() == 1; }
            finally { UserHolder.removeUser(); }
        };
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(click); var second = pool.submit(click); start.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(5, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(mapper.selectById(id).getLiked()).isZero();
        assertThat(redis.opsForSet().isMember(key(), "7")).isFalse();
    }

    @Test void rejectedDatabaseWriteDoesNotCreateARedisRelation() {
        jdbc.update("UPDATE tb_blog_comments SET reject_likes=TRUE WHERE id=?", id);
        assertThatThrownBy(() -> service.toggleCommentLike(blogId, id)).isInstanceOf(ResponseStatusException.class);
        assertThat(mapper.selectById(id).getLiked()).isZero();
        assertThat(redis.opsForSet().isMember(key(), "7")).isFalse();
    }

    @Test void lostRelationsKeepCountAndDisableLikesWithoutRebuilding() {
        service.toggleCommentLike(blogId, id);
        redis.rename(key(), key() + ":lost");
        assertThat(service.toggleCommentLike(blogId, id).getMsg()).isEqualTo("评论点赞数据待核对");
        assertThat(mapper.selectById(id).getLiked()).isEqualTo(1);
        var item = service.queryComments(blogId, 1).getData().getRecords().stream()
                .filter(vo -> vo.getId().equals(id)).findFirst().orElseThrow();
        assertThat(item.getLiked()).isEqualTo(1);
        assertThat(item.getIsLike()).isNull();
        assertThat(item.isLikeAvailable()).isFalse();
        assertThat(redis.hasKey(key())).isFalse();
    }

    @Test void corruptRedisKeyLeavesDatabaseUntouchedAndListStillLoads() {
        redis.opsForValue().set(key(), "隔离测试错误类型");
        assertThatThrownBy(() -> service.toggleCommentLike(blogId, id)).isInstanceOf(ResponseStatusException.class);
        assertThat(mapper.selectById(id).getLiked()).isZero();
        var page = service.queryComments(blogId, 1);
        assertThat(page.getCode()).isEqualTo(1);
        assertThat(page.getData().getRecords()).allSatisfy(vo -> assertThat(vo.isLikeAvailable()).isFalse());
    }

    @Configuration
    @MapperScan("com.dianping.mapper")
    @Import({BlogCommentsServiceImpl.class, MybatisConfig.class})
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:simple_likes_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        }
        @Bean JdbcTemplate jdbc(DataSource ds) {
            JdbcTemplate jdbc = new JdbcTemplate(ds);
            jdbc.execute("CREATE TABLE tb_blog(id BIGINT PRIMARY KEY,shop_id BIGINT,user_id BIGINT,title VARCHAR(255),images VARCHAR(255),content TEXT,liked INT,comments INT,create_time TIMESTAMP,update_time TIMESTAMP)");
            jdbc.update("INSERT INTO tb_blog(id,comments) VALUES(9,0)");
            // 使用随机起始 ID，避免与其他测试用例的 Redis Set 冲突。
            long seed = 1000000L + Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 1000000000000L);
            jdbc.execute("CREATE TABLE tb_blog_comments(id BIGINT GENERATED BY DEFAULT AS IDENTITY(START WITH " + seed + ") PRIMARY KEY,user_id BIGINT,blog_id BIGINT,parent_id BIGINT,answer_id BIGINT,content VARCHAR(255),liked BIGINT DEFAULT 0,status INT,create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,reject_likes BOOLEAN DEFAULT FALSE,CONSTRAINT reject_increment CHECK(reject_likes=FALSE OR liked=0))");
            return jdbc;
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(DataSource ds, JdbcTemplate initialized, MybatisConfig plugins) throws Exception {
            MybatisConfiguration configuration = new MybatisConfiguration(); configuration.setMapUnderscoreToCamelCase(true);
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(ds); factory.setConfiguration(configuration); factory.setPlugins(plugins.mybatisPlusInterceptor()); factory.afterPropertiesSet();
            return new SqlSessionTemplate(factory.getObject());
        }
        @Bean UserService users() { return mock(UserService.class); }
        private static int port() {
            int port = Integer.getInteger("commentLikeRedisPort", 0);
            if (port < 1024 || port == 6379) throw new IllegalArgumentException("必须使用专用隔离端口");
            return port;
        }
        @Bean(destroyMethod = "destroy") LettuceConnectionFactory redisFactory() { return new LettuceConnectionFactory("127.0.0.1", port()); }
        @Bean StringRedisTemplate redis(LettuceConnectionFactory factory) { return new StringRedisTemplate(factory); }
        @Bean(destroyMethod = "shutdown") RedissonClient redisson() {
            org.redisson.config.Config config = new org.redisson.config.Config();
            config.setThreads(2).setNettyThreads(2);
            config.useSingleServer().setAddress("redis://127.0.0.1:" + port()).setConnectionMinimumIdleSize(1).setConnectionPoolSize(8)
                    .setSubscriptionConnectionMinimumIdleSize(1).setSubscriptionConnectionPoolSize(2);
            return Redisson.create(config);
        }
    }
}
