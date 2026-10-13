package com.dianping.comment;

import com.dianping.application.comment.create.CreateCommentCommand;
import com.dianping.application.comment.create.CreateCommentService;
import com.dianping.application.comment.query.CommentPage;
import com.dianping.application.comment.query.CommentView;
import com.dianping.application.comment.query.QueryCommentsService;
import com.dianping.application.comment.like.*;
import com.dianping.application.comment.port.*;
import com.dianping.domain.comment.*;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.dianping.config.MybatisConfig;
import com.dianping.config.MapperConfiguration;
import com.dianping.config.SecurityConfig;
import com.dianping.controller.BlogCommentsController;
import com.dianping.dto.UserDTO;
import com.dianping.exception.GlobalExceptionHandler;
import com.dianping.facade.content.comment.CommentFacade;
import com.dianping.filter.JwtAuthenticationFilter;
import com.dianping.infrastructure.identity.comment.CurrentCommentAuthorAdapter;
import com.dianping.infrastructure.persistence.adapter.comment.BlogCommentTargetAdapter;
import com.dianping.infrastructure.persistence.adapter.comment.CommentAuthorDirectoryAdapter;
import com.dianping.infrastructure.persistence.dataobject.comment.CommentDO;
import com.dianping.infrastructure.persistence.mapper.comment.CommentMapper;
import com.dianping.infrastructure.persistence.repository.comment.CommentRepositoryImpl;
import com.dianping.mapper.BlogCommentsMapper;
import com.dianping.security.JwtProperties;
import com.dianping.security.JwtUtils;
import com.dianping.utils.UserHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import javax.sql.DataSource;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 只使用随机名称的内存数据库，不加载生产 YAML、Redis 或完整应用启动。 */
@SpringJUnitConfig(CommentPilotIntegrationTest.Config.class)
@WebAppConfiguration
class CommentPilotIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired CreateCommentService create;
    @Autowired QueryCommentsService query;
    @Autowired BlogCommentTargetAdapter blogs;
    @Autowired CommentMapper comments;
    @Autowired BlogCommentsMapper legacyComments;
    @Autowired WebApplicationContext context;
    @Autowired JwtUtils jwt;
    @Autowired MemoryLikePorts likes;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        UserDTO user = new UserDTO();
        user.setId(7L);
        UserHolder.saveUser(user);
        likes.unavailable = false;
    }

    @AfterEach
    void clearIdentity() { UserHolder.removeUser(); }

    // 防止丢失事务代理、计数 SQL 的 COALESCE 或持久化一级评论字段。
    @Test
    void createsCommentAndTurnsNullCountIntoOneInTheRealTransaction() {
        jdbc.update("INSERT INTO tb_blog(id,comments) VALUES(101,NULL)");
        Long id = assertDoesNotThrow(() -> create.create(new CreateCommentCommand(101L, "  值得去  ")));
        assertThat(id).isNotNull();
        assertThat(jdbc.queryForMap("SELECT user_id,blog_id,parent_id,answer_id,status,content FROM tb_blog_comments WHERE id=?", id))
                .containsEntry("USER_ID", 7L).containsEntry("BLOG_ID", 101L)
                .containsEntry("PARENT_ID", 0L).containsEntry("ANSWER_ID", 0L)
                .containsEntry("STATUS", 0).containsEntry("CONTENT", "值得去");
        assertThat(count(101)).isEqualTo(1);
    }

    @Test
    void existingCountIsIncrementedRatherThanReplaced() {
        jdbc.update("INSERT INTO tb_blog(id,comments) VALUES(102,4)");
        assertDoesNotThrow(() -> create.create(new CreateCommentCommand(102L, "第一条")));
        assertDoesNotThrow(() -> create.create(new CreateCommentCommand(102L, "第二条")));
        assertThat(count(102)).isEqualTo(6);
        assertThat(commentCount(102)).isEqualTo(2);
    }

    // 共用生产扫描配置，确认新旧映射读取同一张表并保留数据库默认值。
    @Test
    void productionMapperScanKeepsBothMappingsOnTheExistingTable() {
        jdbc.update("INSERT INTO tb_blog(id,comments) VALUES(111,0)");
        Long id = create.create(new CreateCommentCommand(111L, "迁移后仍是同一条评论"));
        CommentDO persisted = comments.selectById(id);
        assertThat(persisted.getContent()).isEqualTo("迁移后仍是同一条评论");
        assertThat(persisted.getLiked()).isZero();
        assertThat(persisted.getCreateTime()).isNotNull();
        assertThat(persisted.getUpdateTime()).isNotNull();
        assertThat(legacyComments.selectById(id).getContent()).isEqualTo(persisted.getContent());
        assertThat(commentCount(111)).isEqualTo(1);
    }

    // CHECK 在评论插入后阻止计数写入；数据库中没有残留评论才证明事务有效。
    @Test
    void countSqlFailureRollsBackAlreadyInsertedComment() {
        jdbc.update("INSERT INTO tb_blog(id,comments) VALUES(103,0)");
        assertThatThrownBy(() -> create.create(new CreateCommentCommand(103L, "必须回滚")))
                .isInstanceOf(DataAccessException.class);
        assertThat(commentCount(103)).isZero();
        assertThat(count(103)).isZero();
    }

    @Test
    void counterAdapterReportsZeroRowsForMissingBlog() {
        assertThat(blogs.incrementCount(9999L)).isFalse();
    }

    @Test void commentLikeRequiresLoginAndTwoClicksToggleSelfLike() throws Exception {
        jdbc.update("INSERT INTO tb_blog(id,comments) VALUES(201,0)");
        Long id = create.create(new CreateCommentCommand(201L, "自己的评论"));
        UserHolder.removeUser();
        mvc.perform(post("/blog/201/comments/" + id + "/like")).andExpect(status().isUnauthorized());
        mvc.perform(post("/blog/201/comments/" + id + "/like").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.liked").value(1)).andExpect(jsonPath("$.data.isLike").value(true));
        mvc.perform(post("/blog/201/comments/" + id + "/like").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.liked").value(0)).andExpect(jsonPath("$.data.isLike").value(false));
    }

    @Test void wrongBlogAndHiddenCommentAndUnverifiedHistoryAreRejected() throws Exception {
        jdbc.update("INSERT INTO tb_blog(id,comments) VALUES(202,0)");
        Long id = create.create(new CreateCommentCommand(202L, "评论"));
        UserHolder.removeUser();
        mvc.perform(post("/blog/999/comments/" + id + "/like").header("Authorization", bearer()))
                .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.msg").value("评论不属于该博客"));
        jdbc.update("UPDATE tb_blog_comments SET status=2 WHERE id=?", id);
        mvc.perform(post("/blog/202/comments/" + id + "/like").header("Authorization", bearer()))
                .andExpect(jsonPath("$.msg").value("评论不允许点赞"));
        jdbc.update("UPDATE tb_blog_comments SET status=0,like_epoch=0,liked=19 WHERE id=?", id);
        mvc.perform(post("/blog/202/comments/" + id + "/like").header("Authorization", bearer()))
                .andExpect(jsonPath("$.msg").value("评论点赞数据待核对"));
        assertThat(jdbc.queryForObject("SELECT liked FROM tb_blog_comments WHERE id=?", Long.class, id)).isEqualTo(19L);
    }

    @Test void realProjectionSqlIsIdempotentAndFencesVersionsAndRecoveryEpochs() {
        jdbc.update("INSERT INTO tb_blog(id,comments) VALUES(203,0)");
        Long id = create.create(new CreateCommentCommand(203L, "计数"));
        jdbc.update("UPDATE tb_blog_comments SET like_initialized=TRUE WHERE id=?", id);
        var projection = new com.dianping.infrastructure.persistence.adapter.comment.CommentLikeCountSyncAdapter(null, comments);
        var newest = new com.dianping.application.comment.like.sync.CommentLikeCountSnapshot(id, 1, 4, 3);
        assertThat(projection.writeCount(newest)).isTrue();
        assertThat(projection.writeCount(newest)).isTrue();
        assertThat(projection.writeCount(new com.dianping.application.comment.like.sync.CommentLikeCountSnapshot(id, 1, 2, 9))).isTrue();
        assertThat(comments.selectById(id).getLiked()).isEqualTo(3);
        jdbc.update("UPDATE tb_blog_comments SET like_epoch=2,like_version=0 WHERE id=?", id);
        assertThat(projection.writeCount(newest)).isFalse();
        assertThat(projection.writeCount(new com.dianping.application.comment.like.sync.CommentLikeCountSnapshot(id, 2, 1, 2))).isTrue();
        assertThat(comments.selectById(id).getLiked()).isEqualTo(2);
        jdbc.update("UPDATE tb_blog_comments SET like_epoch=0 WHERE id=?", id);
        assertThat(projection.writeCount(newest)).isFalse();
    }

    @Test void likeFailureReturns503ButCommentListRetainsCountAndUnknownStatus() throws Exception {
        jdbc.update("INSERT INTO tb_blog(id,comments) VALUES(204,0)");
        Long id = create.create(new CreateCommentCommand(204L, "仍可浏览"));
        jdbc.update("UPDATE tb_blog_comments SET liked=6 WHERE id=?", id);
        likes.unavailable = true; UserHolder.removeUser();
        mvc.perform(post("/blog/204/comments/" + id + "/like").header("Authorization", bearer()))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.msg").value("点赞服务暂时不可用"));
        mvc.perform(get("/blog/204/comments").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.records[0].liked").value(6))
                .andExpect(jsonPath("$.data.records[0].isLike").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.records[0].likeAvailable").value(false));
        mvc.perform(get("/blog/204/comments"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.records[0].isLike").value(false));
    }

    // 手工预期：12 个正常一级评论；最新时间 id=10 优先，同时间 id 降序。
    @Test
    void realSqlFiltersAndSortsBothKeysAndPaginatesAtTen() {
        jdbc.update("INSERT INTO tb_blog(id,comments) VALUES(104,0),(105,0)");
        for (int id = 1; id <= 12; id++) {
            jdbc.update("INSERT INTO tb_blog_comments(id,blog_id,user_id,parent_id,answer_id,content,status,create_time,update_time) VALUES(?,104,7,0,0,?,0,'2026-10-12 10:00:00','2026-10-12 10:00:01')", 1000 + id, "评论" + id);
        }
        jdbc.update("UPDATE tb_blog_comments SET create_time='2026-10-12 11:00:00',status=NULL WHERE id=1010");
        jdbc.update("UPDATE tb_blog_comments SET user_id=999 WHERE id=1012");
        jdbc.update("INSERT INTO tb_blog_comments(id,blog_id,parent_id,status,content,create_time) VALUES(1100,104,1,0,'回复','2026-10-12 12:00:00'),(1101,104,0,1,'举报','2026-10-12 12:00:00'),(1102,104,0,2,'隐藏','2026-10-12 12:00:00'),(1103,105,0,0,'另一博客','2026-10-12 12:00:00'),(1104,105,0,NULL,'另一博客旧状态','2026-10-12 12:00:00')");
        CommentPage<CommentView> first = query.query(104L, 1);
        assertThat(first.total()).isEqualTo(12);
        assertThat(first.size()).isEqualTo(10);
        assertThat(first.records()).extracting(CommentView::id)
                .containsExactly(1010L,1012L,1011L,1009L,1008L,1007L,1006L,1005L,1004L,1003L);
        assertThat(first.records().get(0).userName()).isEqualTo("小林");
        assertThat(first.records().get(1).userName()).isNull();
        assertThat(query.query(104L, 2).records()).extracting(CommentView::id).containsExactly(1002L,1001L);
        assertThat(query.query(104L, 3).records()).isEmpty();
    }

    @Test
    void publicGetDefaultsToPageOneAndPreservesPageJsonAndIsoTime() throws Exception {
        jdbc.update("INSERT INTO tb_blog(id,comments) VALUES(106,1)");
        jdbc.update("INSERT INTO tb_blog_comments(id,blog_id,user_id,parent_id,status,content,create_time,update_time) VALUES(1201,106,7,0,0,'很好','2026-10-12 10:00:00','2026-10-12 10:00:01')");
        UserHolder.removeUser();
        mvc.perform(get("/blog/106/comments"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.msg").isEmpty())
                .andExpect(jsonPath("$.data.current").value(1))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.pages").value(1))
                .andExpect(jsonPath("$.data", org.hamcrest.Matchers.aMapWithSize(5)))
                .andExpect(jsonPath("$.data.records[0].userName").value("小林"))
                .andExpect(jsonPath("$.data.records[0].userIcon").value("/avatar.png"))
                .andExpect(jsonPath("$.data.records[0].createTime").value("2026-10-12T10:00:00"))
                .andExpect(jsonPath("$.data.records[0].updateTime").value("2026-10-12T10:00:01"));
    }

    @Test
    void authenticatedPostIgnoresClientIdentityAndPersistsTrimmedContent() throws Exception {
        jdbc.update("INSERT INTO tb_blog(id,comments) VALUES(107,0)");
        UserHolder.removeUser();
        mvc.perform(post("/blog/107/comments").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"  很好  \",\"userId\":999}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data").isNumber());
        assertThat(jdbc.queryForObject("SELECT user_id FROM tb_blog_comments WHERE blog_id=107",Long.class)).isEqualTo(7L);
        assertThat(jdbc.queryForObject("SELECT content FROM tb_blog_comments WHERE blog_id=107",String.class)).isEqualTo("很好");
        assertThat(count(107)).isEqualTo(1);
        assertThat(UserHolder.getUser()).isNull();
    }

    @Test
    void postRequiresAuthenticationEvenWithAValidRequest() throws Exception {
        UserHolder.removeUser();
        mvc.perform(post("/blog/108/comments").contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"很好\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(commentCount(108)).isZero();
    }

    @Test
    void httpRejectsRaw256CharactersEvenWhenDomainTrimWouldAccept254() throws Exception {
        jdbc.update("INSERT INTO tb_blog(id,comments) VALUES(109,0)");
        UserHolder.removeUser();
        mvc.perform(post("/blog/109/comments").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent(" " + "a".repeat(254) + " ")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("评论内容不能超过255个字符"));
        assertThat(commentCount(109)).isZero();
    }

    @Test
    void httpAcceptsRaw255Utf16UnitsAndRejects256Utf16Units() throws Exception {
        jdbc.update("INSERT INTO tb_blog(id,comments) VALUES(110,0)");
        UserHolder.removeUser();
        mvc.perform(post("/blog/110/comments").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(jsonContent("😀".repeat(127)+"好")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        mvc.perform(post("/blog/110/comments").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(jsonContent("😀".repeat(128))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.msg").value("评论内容不能超过255个字符"));
        assertThat(commentCount(110)).isEqualTo(1);
    }

    @Test
    void httpPreservesBlankBodyAndBusinessErrorContracts() throws Exception {
        UserHolder.removeUser();
        mvc.perform(post("/blog/999/comments").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\" \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.msg").value("评论内容不能为空"));
        mvc.perform(post("/blog/999/comments").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"评论\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("博客不存在"));
        mvc.perform(get("/blog/0/comments").param("current","0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.msg").value("非法博客Id"));
        mvc.perform(get("/blog/999/comments").param("current","0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.msg").value("页码无效"));
        mvc.perform(get("/blog/999/comments"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.msg").value("博客不存在"));
    }

    private int count(long blogId) { return jdbc.queryForObject("SELECT comments FROM tb_blog WHERE id=?", Integer.class, blogId); }
    private int commentCount(long blogId) { return jdbc.queryForObject("SELECT COUNT(*) FROM tb_blog_comments WHERE blog_id=?", Integer.class, blogId); }
    private String bearer() { return "Bearer " + jwt.generateAccessToken("7"); }
    private String jsonContent(String content) throws Exception { return new ObjectMapper().writeValueAsString(java.util.Map.of("content", content)); }

    @Configuration
    @EnableWebMvc
    @EnableTransactionManagement
    @Import({MybatisConfig.class, MapperConfiguration.class, CreateCommentService.class, QueryCommentsService.class, ToggleCommentLikeService.class,
            CommentRepositoryImpl.class, BlogCommentTargetAdapter.class, CommentAuthorDirectoryAdapter.class,
            CurrentCommentAuthorAdapter.class, CommentFacade.class, BlogCommentsController.class,
            GlobalExceptionHandler.class, SecurityConfig.class, JwtAuthenticationFilter.class})
    static class Config implements WebMvcConfigurer {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:comment_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        }
        @Bean JdbcTemplate jdbcTemplate(DataSource ds) {
            JdbcTemplate jdbc = new JdbcTemplate(ds);
            jdbc.execute("CREATE TABLE tb_blog(id BIGINT AUTO_INCREMENT PRIMARY KEY,shop_id BIGINT,user_id BIGINT,title VARCHAR(255),images VARCHAR(255),content TEXT,liked INT,comments INT,create_time TIMESTAMP,update_time TIMESTAMP, CONSTRAINT reject_count CHECK(id<>103 OR comments<1))");
            jdbc.execute("CREATE TABLE tb_blog_comments(id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT,blog_id BIGINT,parent_id BIGINT,answer_id BIGINT,content VARCHAR(255),liked BIGINT DEFAULT 0,like_epoch BIGINT DEFAULT 0,like_version BIGINT DEFAULT 0,like_initialized BOOLEAN DEFAULT FALSE,status INT,create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            jdbc.execute("CREATE TABLE tb_user(id BIGINT PRIMARY KEY,phone VARCHAR(20),password VARCHAR(255),nick_name VARCHAR(255),icon VARCHAR(255),create_time TIMESTAMP,update_time TIMESTAMP)");
            jdbc.update("INSERT INTO tb_user(id,nick_name,icon) VALUES(7,'小林','/avatar.png')");
            return jdbc;
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(DataSource ds, MybatisConfig config, JdbcTemplate initialized) throws Exception {
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(ds);
            factory.setConfiguration(configuration);
            factory.setPlugins(config.mybatisPlusInterceptor());
            factory.afterPropertiesSet();
            return new SqlSessionTemplate(factory.getObject());
        }
        @Bean DataSourceTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean MemoryLikePorts likePorts(JdbcTemplate jdbc) { return new MemoryLikePorts(jdbc); }
        @Bean JwtUtils jwtUtils() {
            JwtProperties properties = new JwtProperties();
            // 固定内容仅供隔离测试签名，不能使用生产密钥。
            properties.setSecret(Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]));
            return new JwtUtils(properties);
        }
        @Override public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
            converters.add(new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                    .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build()));
        }
    }

    /** HTTP 测试用关系能力；真实 Lua 另由隔离 Redis 测试验证。 */
    static class MemoryLikePorts implements CommentLikeRepository, CommentLikeReadPort {
        private final JdbcTemplate jdbc;
        private final java.util.Map<CommentLikeId, Boolean> relations = new java.util.HashMap<>();
        boolean unavailable;
        MemoryLikePorts(JdbcTemplate jdbc) { this.jdbc = jdbc; }
        public CommentLike findState(CommentLikeId id, long epoch, boolean initialize) {
            if (unavailable) throw new CommentLikeUnavailableException();
            return CommentLike.restore(id, relations.getOrDefault(id, false));
        }
        public CommentLikeApplyResult tryApply(CommentLikeChange change, long epoch) {
            if (unavailable) throw new CommentLikeUnavailableException();
            boolean current = relations.getOrDefault(change.id(), false);
            if (current != change.expectedLiked()) return new CommentLikeApplyResult(false, current, 0);
            relations.put(change.id(), change.targetLiked());
            long count = relations.entrySet().stream().filter(e -> e.getKey().commentId().equals(change.id().commentId()) && e.getValue()).count();
            return new CommentLikeApplyResult(true, change.targetLiked(), count);
        }
        public java.util.Map<Long, CommentLikeStatus> findStates(List<Long> ids, Long userId) {
            if (unavailable) throw new CommentLikeUnavailableException();
            java.util.Map<Long, CommentLikeStatus> states = new java.util.HashMap<>();
            ids.forEach(id -> states.put(id, jdbc.queryForObject("SELECT like_epoch FROM tb_blog_comments WHERE id=?", Long.class, id) > 0
                    ? new CommentLikeStatus(userId != null && relations.getOrDefault(new CommentLikeId(id, userId), false), true)
                    : CommentLikeStatus.unavailable(userId)));
            return states;
        }
    }
}
