package com.dianping.feed;

import com.dianping.dto.UserDTO;
import com.dianping.entity.Blog;
import com.dianping.entity.FeedTask;
import com.dianping.mapper.BlogMapper;
import com.dianping.mapper.FeedTaskMapper;
import com.dianping.mapper.FollowMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.dianping.service.BlogService;
import com.dianping.service.impl.BlogServiceImpl;
import com.dianping.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/** 使用隔离数据库和真实 MyBatis Mapper/XML 验证 SQL 与 Spring 事务；JDBC 仅负责测试建表与断言。 */
class FeedPersistenceTest {
    private JdbcTemplate jdbc;
    private FeedTaskRepository tasks;
    private BlogService publisher;
    private BlogMapper blogs;
    private FollowMapper follows;
    private FeedTaskMapper taskMapper;
    private FeedRepository repository;

    @BeforeEach void setup() throws Exception {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:feed_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds);
        String migration;
        try (var input = new ClassPathResource("db/migrations/20261005_feed.sql").getInputStream()) {
            migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        jdbc.execute(migration.substring(0, migration.indexOf(';') + 1));
        jdbc.execute("""
                CREATE TABLE tb_blog(id BIGINT AUTO_INCREMENT PRIMARY KEY,shop_id BIGINT,user_id BIGINT,
                  title VARCHAR(255),images VARCHAR(255),content TEXT,liked INT,comments INT,
                  create_time TIMESTAMP,update_time TIMESTAMP)
                """);
        jdbc.execute("CREATE TABLE tb_follow(id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT,follow_user_id BIGINT,create_time TIMESTAMP)");
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(ds);
        factory.setConfiguration(configuration);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/*.xml"));
        factory.afterPropertiesSet();
        SqlSessionTemplate session = new SqlSessionTemplate(factory.getObject());
        blogs = session.getMapper(BlogMapper.class);
        follows = session.getMapper(FollowMapper.class);
        taskMapper = session.getMapper(FeedTaskMapper.class);
        tasks = new FeedTaskRepository(taskMapper, follows, new FeedProperties());
        repository = new FeedRepository(blogs, follows);
        BlogServiceImpl service = new BlogServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", blogs);
        ReflectionTestUtils.setField(service, "feedTasks", tasks);
        ProxyFactory proxy = new ProxyFactory(service);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds), new AnnotationTransactionAttributeSource()));
        publisher = (BlogService) proxy.getProxy();
        UserDTO user = new UserDTO(); user.setId(5L); UserHolder.saveUser(user);
    }

    @AfterEach void cleanup() { UserHolder.removeUser(); }

    @Test void publicationCommitsBlogAndDurableTaskTogetherAndIgnoresForgedMetadata() {
        Blog request = new Blog().setId(99L).setUserId(99L).setCreateTime(FeedTimeline.time(1000)).setLiked(999);
        assertThat(publisher.publishBlog(request).getData()).isEqualTo(request.getId()).isNotNull().isNotEqualTo(99L);
        assertThat(jdbc.queryForObject("SELECT user_id FROM tb_blog", Long.class)).isEqualTo(5L);
        assertThat(tasks.due(10)).hasSize(1);
        assertThat(tasks.due(10).get(0).getBlogId()).isEqualTo(request.getId());
        assertThat(tasks.due(10).get(0).getUserId()).isNull();
        assertThat(request.getLiked()).isZero();
        assertThat(request.getCreateTime().getYear()).isGreaterThan(2025);
    }

    @Test void taskWriteFailureRollsBackPublishedBlog() {
        jdbc.execute("ALTER TABLE tb_feed_task ADD CONSTRAINT reject_publish CHECK (kind<>'PUBLISH')");
        assertThatThrownBy(() -> publisher.publishBlog(new Blog())).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_blog", Integer.class)).isZero();
    }

    @Test void expiredLeaseIsRecoverableAndProgressIsDurableAcrossRepositoryInstances() {
        tasks.publish(42, 5);
        var task = tasks.due(1).get(0);
        assertThat(tasks.claim(task, "worker-a", 300)).isTrue();
        assertThat(tasks.claim(task, "worker-b", 300)).isFalse();
        jdbc.update("UPDATE tb_feed_task SET lease_until=? WHERE id=?", Timestamp.from(Instant.now().minusSeconds(1)), task.getId());
        FeedTaskRepository restarted = new FeedTaskRepository(taskMapper, follows, new FeedProperties());
        var recovered = restarted.due(1).get(0);
        assertThat(restarted.claim(recovered, "worker-b", 300)).isTrue();
        assertThatThrownBy(() -> tasks.progress(task, "worker-a", 10)).isInstanceOf(IllegalStateException.class);
        restarted.progress(recovered, "worker-b", 200);
        assertThat(tasks.claim(task, "stale-worker", 300)).isFalse();
        assertThat(new FeedTaskRepository(taskMapper, follows, new FeedProperties()).due(1).get(0).getCursorId()).isEqualTo(200L);
    }

    @Test void failedTaskRetainsCursorAndRecordsRetryDelay() {
        tasks.publish(42, 5);
        var task = tasks.due(1).get(0);
        tasks.claim(task, "worker", 300);
        tasks.retry(task, "worker", new IllegalStateException("redis offline"));
        assertThat(tasks.due(10)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT attempts FROM tb_feed_task", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM tb_feed_task", String.class)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT last_error FROM tb_feed_task", String.class)).isEqualTo("IllegalStateException");
    }

    @Test void relationshipMaintenanceOnlyBroadcastsAtConfiguredClassificationBoundary() {
        jdbc.update("INSERT INTO tb_follow(user_id,follow_user_id) VALUES (1,10),(1,20),(2,20),(1,30)");
        FeedProperties properties = new FeedProperties(); properties.setHotAuthorThreshold(2);
        FeedTaskRepository configuredTasks = new FeedTaskRepository(taskMapper, follows, properties);
        configuredTasks.relationship(1, 10, true);
        configuredTasks.relationship(2, 20, true);
        configuredTasks.relationship(2, 30, false);
        configuredTasks.relationship(1, 40, false);
        assertThat(configuredTasks.due(10).stream().map(FeedTask::getKind))
                .containsExactly("BACKFILL", "BACKFILL", "RECLASSIFY", "CLEANUP", "RECLASSIFY", "CLEANUP");
    }

    @Test void repeatedFailuresBackOffToFiveMinutesWithoutClaimingStaleAttempts() {
        tasks.publish(42, 5);
        var stale = tasks.due(1).get(0);
        jdbc.update("UPDATE tb_feed_task SET attempts=8 WHERE id=?", stale.getId());
        assertThat(tasks.claim(stale, "stale-worker", 300)).isFalse();
        var task = tasks.due(1).get(0);
        assertThat(tasks.claim(task, "worker", 300)).isTrue();
        Instant before = Instant.now();
        tasks.retry(task, "worker", new IllegalStateException());
        Instant next = jdbc.queryForObject("SELECT next_attempt FROM tb_feed_task", Timestamp.class).toInstant();
        assertThat(next).isAfterOrEqualTo(before.plusSeconds(299)).isBefore(Instant.now().plusSeconds(301));
    }

    @Test void feedQueriesMapAuthorsFilterRelationsAndAdvanceFanCursor() {
        jdbc.update("INSERT INTO tb_follow(user_id,follow_user_id) VALUES (5,10),(5,20),(6,20),(8,20)");
        assertThat(repository.authors(5, 2))
                .containsExactly(new FeedRepository.Author(10, false), new FeedRepository.Author(20, true));
        assertThat(repository.hot(10, 2)).isFalse();
        assertThat(repository.hot(20, 2)).isTrue();
        assertThat(repository.follows(5, 20)).isTrue();
        assertThat(repository.follows(5, 30)).isFalse();
        assertThat(repository.fans(20, 0, 2)).containsExactly(5L, 6L);
        assertThat(repository.fans(20, 6, 2)).containsExactly(8L);
    }

    @Test void recentAndHistoryXmlPreserveWindowStringOrderingAndBlogFields() {
        var time = FeedTimeline.time(System.currentTimeMillis() / 1000 * 1000);
        for (long id : new long[]{9, 80, 7, 100}) {
            blogs.insert(new Blog().setId(id).setUserId(10L).setShopId(3L).setTitle("blog-" + id)
                    .setContent("content").setImages("image.jpg").setLiked(2).setComments(1)
                    .setCreateTime(time).setUpdateTime(time));
        }
        blogs.insert(new Blog().setId(200L).setUserId(20L).setCreateTime(time));
        blogs.insert(new Blog().setId(201L).setUserId(10L).setCreateTime(time.minusDays(31)));
        blogs.insert(new Blog().setId(202L).setUserId(10L).setCreateTime(time.plusSeconds(1)));
        var recent = repository.recent(java.util.List.of(10L), time.minusDays(30), time, 10);
        assertThat(recent).extracting(Blog::getId).containsExactly(9L, 80L, 7L, 100L);
        assertThat(repository.recent(java.util.List.of(10L), time.minusDays(30), time, 2))
                .extracting(Blog::getId).containsExactly(9L, 80L);
        assertThat(recent.get(0).getUserId()).isEqualTo(10L);
        assertThat(recent.get(0).getShopId()).isEqualTo(3L);
        assertThat(recent.get(0).getCreateTime()).isEqualTo(time);
        assertThat(recent.get(0).getContent()).isEqualTo("content");
        assertThat(repository.history(java.util.List.of(10L), time.minusDays(30), 9, 2))
                .extracting(Blog::getId).containsExactly(80L, 100L);
        assertThat(repository.blog(80).getTitle()).isEqualTo("blog-80");
        assertThat(repository.blog(999)).isNull();
        assertThat(repository.blogByIds(java.util.List.of(9L, 80L)))
                .extracting(Blog::getId).containsExactlyInAnyOrder(9L, 80L);
        assertThat(repository.blogByIds(java.util.List.of())).isEmpty();
        assertThat(repository.recent(java.util.List.of(), time.minusDays(30), time, 10)).isEmpty();
        assertThat(blogs.selectFeedRecent(java.util.List.of(), time.minusDays(30), time, 10)).isEmpty();
    }

    @Test void onlyCurrentLeaseOwnerCanFinishAndCompletedTaskIsNotRetried() {
        tasks.publish(42, 5);
        var task = tasks.due(1).get(0);
        tasks.claim(task, "worker", 300);
        assertThatThrownBy(() -> tasks.finish(task, "other", "DONE", null)).isInstanceOf(IllegalStateException.class);
        tasks.finish(task, "worker", "DONE", null);
        tasks.retry(task, "worker", new IllegalStateException());
        assertThat(tasks.due(10)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT status FROM tb_feed_task", String.class)).isEqualTo("DONE");
        assertThat(jdbc.queryForObject("SELECT finished_at FROM tb_feed_task", Timestamp.class)).isNotNull();
    }
}
