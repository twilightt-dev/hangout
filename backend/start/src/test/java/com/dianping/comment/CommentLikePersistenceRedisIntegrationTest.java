package com.dianping.comment;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.dianping.application.comment.like.*;
import com.dianping.application.comment.like.sync.*;
import com.dianping.application.comment.port.CurrentCommentAuthor;
import com.dianping.domain.comment.*;
import com.dianping.infrastructure.persistence.adapter.comment.*;
import com.dianping.infrastructure.persistence.mapper.comment.CommentMapper;
import com.dianping.infrastructure.persistence.repository.comment.CommentRepositoryImpl;
import com.dianping.infrastructure.redis.comment.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** 随机内存数据库与专用 Redis 实例，验证生产适配器之间的协议。 */
@SpringJUnitConfig(CommentLikePersistenceRedisIntegrationTest.Config.class)
@EnabledIfSystemProperty(named = "commentLikeRedisTest", matches = "true")
class CommentLikePersistenceRedisIntegrationTest {
    @Autowired CommentRepositoryImpl comments;
    @Autowired ToggleCommentLikeService toggle;
    @Autowired SyncCommentLikeCountsService sync;
    @Autowired CommentLikeRedisStore store;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate jdbc;
    @Autowired CommentLikeReadAdapter read;

    @Test void unsynchronizedFirstLikeCannotBeReinitializedAfterRedisLoss() {
        Long id = comments.insert(Comment.create(9L, 7L, CommentContent.of("第一条")));
        assertThat(toggle.toggle(9L, id).isLike()).isTrue();
        assertThat(comments.metadata(id).initialized()).isTrue();
        assertThat(comments.metadata(id).version()).isZero();
        String prefix = store.pendingKey(id).replace("pending", id.toString());
        // 移到隔离备查键模拟丢失，不触碰已有环境数据。
        redis.rename(prefix + ":meta", prefix + ":meta:lost");
        redis.rename(prefix + ":users", prefix + ":users:lost");
        assertThatThrownBy(() -> toggle.toggle(9L, id)).isInstanceOf(CommentLikeUnavailableException.class);
        assertThat(read.findStates(List.of(id), 7L).get(id).available()).isFalse();
        assertThat(comments.metadata(id).liked()).isZero();
    }

    @Test void failedMySqlProjectionIsRetriedAndOldAckDoesNotDiscardNewLike() throws Exception {
        Long id = comments.insert(Comment.create(9L, 7L, CommentContent.of("重试")));
        toggle.toggle(9L, id);
        jdbc.update("UPDATE tb_blog_comments SET sync_blocked=TRUE WHERE id=?", id);
        sync.synchronize(100);
        assertThat(comments.metadata(id).version()).isZero();
        jdbc.update("UPDATE tb_blog_comments SET sync_blocked=FALSE WHERE id=?", id);
        Thread.sleep(1100);
        sync.synchronize(100);
        assertThat(comments.metadata(id).liked()).isEqualTo(1); assertThat(comments.metadata(id).version()).isEqualTo(1);
        assertThat(store.pending(100)).noneMatch(s -> s.commentId().equals(id));
        toggle.toggle(9L, id);
        sync.synchronize(100);
        assertThat(comments.metadata(id).liked()).isZero(); assertThat(comments.metadata(id).version()).isEqualTo(2);
    }

    @Test void auditedRecoveryRebuildsPendingAndFencesOldEpoch() {
        Long id = comments.insert(Comment.create(9L, 7L, CommentContent.of("恢复")));
        toggle.toggle(9L, id); sync.synchronize(100);
        var old = new CommentLikeCountSnapshot(id, 1, 100, 19);
        DefaultRedisScript<List> recovery = new DefaultRedisScript<>();
        recovery.setLocation(new ClassPathResource("lua/comment-like-recover.lua")); recovery.setResultType(List.class);
        String prefix = store.pendingKey(id).replace("pending", id.toString());
        List<String> keys = List.of(prefix + ":users", prefix + ":meta", store.pendingKey(id));
        assertThat(redis.execute(recovery, keys, "1", "1", "2", "0", id.toString())).containsExactly(-1L);
        assertThat(redis.execute(recovery, keys, "1", "1", "2", "1", id.toString())).containsExactly(1L, 1L);
        jdbc.update("UPDATE tb_blog_comments SET like_epoch=2,like_version=0,like_initialized=TRUE WHERE id=?", id);
        var projection = new CommentLikeCountSyncAdapter(store, mapper);
        assertThat(projection.writeCount(old)).isFalse();
        assertThatThrownBy(() -> store.apply(new CommentLikeChange(new CommentLikeId(id, 7L), true, false), 1)).isInstanceOf(CommentLikeUnavailableException.class);
        sync.synchronize(100);
        assertThat(comments.metadata(id).epoch()).isEqualTo(2); assertThat(comments.metadata(id).version()).isEqualTo(1);
        assertThat(comments.metadata(id).liked()).isEqualTo(1);
    }
    @Autowired CommentMapper mapper;

    @Configuration
    @EnableTransactionManagement
    @MapperScan("com.dianping.infrastructure.persistence.mapper.comment")
    @Import({CommentRepositoryImpl.class, RedisCommentLikeRepository.class, CommentLikeReadAdapter.class,
            CommentLikeCountSyncAdapter.class, ToggleCommentLikeService.class, SyncCommentLikeCountsService.class})
    static class Config {
        @Bean DataSource dataSource() { return new DriverManagerDataSource("jdbc:h2:mem:likes_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""); }
        @Bean JdbcTemplate jdbc(DataSource ds) {
            JdbcTemplate jdbc = new JdbcTemplate(ds);
            jdbc.execute("CREATE TABLE tb_blog_comments(id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT,blog_id BIGINT,parent_id BIGINT,answer_id BIGINT,content VARCHAR(255),liked BIGINT DEFAULT 0,like_epoch BIGINT DEFAULT 0,like_version BIGINT DEFAULT 0,like_initialized BOOLEAN DEFAULT FALSE,status INT,create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,sync_blocked BOOLEAN DEFAULT FALSE,CONSTRAINT reject_sync CHECK(sync_blocked=FALSE OR liked=0))");
            return jdbc;
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(DataSource ds, JdbcTemplate initialized) throws Exception {
            var configuration = new MybatisConfiguration(); configuration.setMapUnderscoreToCamelCase(true);
            var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(ds); factory.setConfiguration(configuration); factory.afterPropertiesSet();
            return new SqlSessionTemplate(factory.getObject());
        }
        @Bean DataSourceTransactionManager transactions(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean CurrentCommentAuthor identity() { return () -> 7L; }
        @Bean(destroyMethod = "destroy") LettuceConnectionFactory redisFactory() {
            int port = Integer.getInteger("commentLikeRedisPort", 0);
            if (port < 1024 || port == 6379) throw new IllegalArgumentException("必须使用专用隔离端口");
            return new LettuceConnectionFactory("127.0.0.1", port);
        }
        @Bean StringRedisTemplate redis(LettuceConnectionFactory factory) { return new StringRedisTemplate(factory); }
        @Bean CommentLikeRedisStore store(StringRedisTemplate redis) { return new CommentLikeRedisStore(redis, "integration:" + UUID.randomUUID()); }
    }
}
