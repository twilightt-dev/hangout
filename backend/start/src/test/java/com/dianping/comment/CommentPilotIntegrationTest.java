package com.dianping.comment;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.dianping.config.MybatisConfig;
import com.dianping.config.MapperConfiguration;
import com.dianping.config.SecurityConfig;
import com.dianping.controller.BlogCommentsController;
import com.dianping.dto.UserDTO;
import com.dianping.exception.GlobalExceptionHandler;
import com.dianping.filter.JwtAuthenticationFilter;
import com.dianping.mapper.BlogCommentsMapper;
import com.dianping.mapper.BlogMapper;
import com.dianping.mapper.UserMapper;
import com.dianping.service.BlogCommentsService;
import com.dianping.service.UserService;
import com.dianping.service.impl.BlogCommentsServiceImpl;
import org.redisson.api.RedissonClient;
import org.redisson.api.RLock;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisSetCommands;
import org.springframework.data.redis.RedisConnectionFailureException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import com.dianping.entity.BlogComments;
import com.dianping.VO.CommentVO;
import com.dianping.dto.CreateCommentDTO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import static org.mockito.Mockito.*;
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
    @Autowired BlogCommentsService service;
    @Autowired BlogCommentsMapper comments;
    @Autowired BlogMapper blogs;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired WebApplicationContext context;
    @Autowired JwtUtils jwt;
    @Autowired MemoryLikes likes;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        UserDTO user = new UserDTO();
        user.setId(7L);
        UserHolder.saveUser(user);
        likes.unavailable = false;
        likes.failWrites = false;
    }

    @AfterEach
    void clearIdentity() { UserHolder.removeUser(); }

    // 防止丢失事务代理、计数 SQL 的 COALESCE 或持久化一级评论字段。
    @Test
    void createsCommentAndTurnsNullCountIntoOneInTheRealTransaction() {
        jdbc.update("INSERT INTO tb_blog(id,comments) VALUES(101,NULL)");
        Long id = assertDoesNotThrow(() -> createComment(101L, "  值得去  "));
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
        assertDoesNotThrow(() -> createComment(102L, "第一条"));
        assertDoesNotThrow(() -> createComment(102L, "第二条"));
        assertThat(count(102)).isEqualTo(6);
        assertThat(commentCount(102)).isEqualTo(2);
    }

    // 共用生产扫描配置，确认传统评论映射保留数据库默认值。
    @Test
    void productionMapperScanUsesTheExistingCommentMapping() {
        jdbc.update("INSERT INTO tb_blog(id,comments) VALUES(111,0)");
        Long id = createComment(111L, "迁移后仍是同一条评论");
        BlogComments persisted = comments.selectById(id);
        assertThat(persisted.getContent()).isEqualTo("迁移后仍是同一条评论");
        assertThat(persisted.getLiked()).isZero();
        assertThat(persisted.getCreateTime()).isNotNull();
        assertThat(persisted.getUpdateTime()).isNotNull();
        assertThat(comments.selectById(id).getContent()).isEqualTo(persisted.getContent());
        assertThat(commentCount(111)).isEqualTo(1);
    }

    // CHECK 在评论插入后阻止计数写入；数据库中没有残留评论才证明事务有效。
    @Test
    void countSqlFailureRollsBackAlreadyInsertedComment() {
        jdbc.update("INSERT INTO tb_blog(id,comments) VALUES(103,0)");
        assertThatThrownBy(() -> createComment(103L, "必须回滚"))
                .isInstanceOf(DataAccessException.class);
        assertThat(commentCount(103)).isZero();
        assertThat(count(103)).isZero();
    }

    @Test
    void counterAdapterReportsZeroRowsForMissingBlog() {
        assertThat(blogs.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<com.dianping.entity.Blog>().eq("id", 9999L).setSql("comments = COALESCE(comments, 0) + 1")) > 0).isFalse();
    }

    @Test void commentLikeRequiresLoginAndTwoClicksToggleSelfLike() throws Exception {
        jdbc.update("INSERT INTO tb_blog(id,comments) VALUES(201,0)");
        Long id = createComment(201L, "自己的评论");
        UserHolder.removeUser();
        mvc.perform(post("/blog/201/comments/" + id + "/like")).andExpect(status().isUnauthorized());
        mvc.perform(post("/blog/201/comments/" + id + "/like").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.liked").value(1)).andExpect(jsonPath("$.data.isLike").value(true));
        assertThat(jdbc.queryForObject("SELECT liked FROM tb_blog_comments WHERE id=?", Long.class, id)).isEqualTo(1L);
        mvc.perform(post("/blog/201/comments/" + id + "/like").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.liked").value(0)).andExpect(jsonPath("$.data.isLike").value(false));
        assertThat(jdbc.queryForObject("SELECT liked FROM tb_blog_comments WHERE id=?", Long.class, id)).isZero();
    }

    @Test void wrongBlogAndHiddenCommentAndUnverifiedHistoryAreRejected() throws Exception {
        jdbc.update("INSERT INTO tb_blog(id,comments) VALUES(202,0)");
        Long id = createComment(202L, "评论");
        UserHolder.removeUser();
        mvc.perform(post("/blog/999/comments/" + id + "/like").header("Authorization", bearer()))
                .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.msg").value("评论不属于该博客"));
        jdbc.update("UPDATE tb_blog_comments SET status=2 WHERE id=?", id);
        mvc.perform(post("/blog/202/comments/" + id + "/like").header("Authorization", bearer()))
                .andExpect(jsonPath("$.msg").value("评论不允许点赞"));
        jdbc.update("UPDATE tb_blog_comments SET status=0,liked=19 WHERE id=?", id);
        mvc.perform(post("/blog/202/comments/" + id + "/like").header("Authorization", bearer()))
                .andExpect(jsonPath("$.msg").value("评论点赞数据待核对"));
        assertThat(jdbc.queryForObject("SELECT liked FROM tb_blog_comments WHERE id=?", Long.class, id)).isEqualTo(19L);
    }

    @Test void realCountSqlHandlesNullAndPreventsUnderflowOverflowAndHiddenWrites() {
        jdbc.update("INSERT INTO tb_blog(id,comments) VALUES(203,0)");
        Long id = createComment(203L, "计数");
        jdbc.update("UPDATE tb_blog_comments SET liked=NULL WHERE id=?", id);
        assertThat(comments.incrementLiked(id, 203L)).isEqualTo(1);
        assertThat(comments.selectById(id).getLiked()).isEqualTo(1L);
        assertThat(comments.decrementLiked(id, 203L)).isEqualTo(1);
        assertThat(comments.decrementLiked(id, 203L)).isZero();
        assertThat(comments.incrementLiked(id, 999L)).isZero();
        jdbc.update("UPDATE tb_blog_comments SET status=2 WHERE id=?", id);
        assertThat(comments.incrementLiked(id, 203L)).isZero();
        jdbc.update("UPDATE tb_blog_comments SET status=0,liked=4294967295 WHERE id=?", id);
        assertThat(comments.incrementLiked(id, 203L)).isZero();
        assertThat(comments.selectById(id).getLiked()).isEqualTo(4294967295L);
    }

    @Test void likeFailureReturns503ButCommentListRetainsCountAndUnknownStatus() throws Exception {
        jdbc.update("INSERT INTO tb_blog(id,comments) VALUES(204,0)");
        Long id = createComment(204L, "仍可浏览");
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

    @Test void failedRedisWriteLeavesCountForManualCheckingWithoutReplaying() throws Exception {
        jdbc.update("INSERT INTO tb_blog(id,comments) VALUES(205,0)");
        Long id = createComment(205L, "部分失败");
        likes.failWrites = true;
        UserHolder.removeUser();
        mvc.perform(post("/blog/205/comments/" + id + "/like").header("Authorization", bearer()))
                .andExpect(status().isServiceUnavailable());
        // 明确接受部分成功：数据库只加一次，不自动补偿、不重试切换。
        assertThat(jdbc.queryForObject("SELECT liked FROM tb_blog_comments WHERE id=?", Long.class, id)).isEqualTo(1L);
        mvc.perform(get("/blog/205/comments").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.records[0].liked").value(1))
                .andExpect(jsonPath("$.data.records[0].isLike").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.records[0].likeAvailable").value(false));
    }

    @Test void historicalMissingRelationsDisableOnlyThatComment() {
        jdbc.update("INSERT INTO tb_blog(id,comments) VALUES(206,0)");
        Long historical = createComment(206L, "待核对评论");
        Long healthy = createComment(206L, "正常评论");
        jdbc.update("UPDATE tb_blog_comments SET liked=19 WHERE id=?", historical);
        var page = service.queryComments(206L, 1).getData();
        var normal = page.getRecords().stream().filter(vo -> vo.getId().equals(healthy)).findFirst().orElseThrow();
        var paused = page.getRecords().stream().filter(vo -> vo.getId().equals(historical)).findFirst().orElseThrow();
        assertThat(normal.isLikeAvailable()).isTrue();
        assertThat(normal.getIsLike()).isFalse();
        assertThat(paused.isLikeAvailable()).isFalse();
        assertThat(paused.getIsLike()).isNull();
        assertThat(paused.getLiked()).isEqualTo(19);
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
        Page<CommentVO> first = queryPage(104L, 1);
        assertThat(first.getTotal()).isEqualTo(12);
        assertThat(first.getSize()).isEqualTo(10);
        assertThat(first.getRecords()).extracting(CommentVO::getId)
                .containsExactly(1010L,1012L,1011L,1009L,1008L,1007L,1006L,1005L,1004L,1003L);
        assertThat(first.getRecords().get(0).getUserName()).isEqualTo("小林");
        assertThat(first.getRecords().get(1).getUserName()).isNull();
        assertThat(queryPage(104L, 2).getRecords()).extracting(CommentVO::getId).containsExactly(1002L,1001L);
        assertThat(queryPage(104L, 3).getRecords()).isEmpty();
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

    private Long createComment(Long blogId, String content) {
        CreateCommentDTO request = new CreateCommentDTO(); request.setContent(content);
        var result = service.createComment(blogId, request);
        assertThat(result.getCode()).isEqualTo(1);
        return result.getData();
    }
    private Page<CommentVO> queryPage(Long blogId, Integer page) { return service.queryComments(blogId, page).getData(); }

    private int count(long blogId) { return jdbc.queryForObject("SELECT comments FROM tb_blog WHERE id=?", Integer.class, blogId); }
    private int commentCount(long blogId) { return jdbc.queryForObject("SELECT COUNT(*) FROM tb_blog_comments WHERE blog_id=?", Integer.class, blogId); }
    private String bearer() { return "Bearer " + jwt.generateAccessToken("7"); }
    private String jsonContent(String content) throws Exception { return new ObjectMapper().writeValueAsString(java.util.Map.of("content", content)); }

    @Configuration
    @EnableWebMvc
    @EnableTransactionManagement
    @Import({MybatisConfig.class, MapperConfiguration.class, BlogCommentsServiceImpl.class,
            BlogCommentsController.class,
            GlobalExceptionHandler.class, SecurityConfig.class, JwtAuthenticationFilter.class})
    static class Config implements WebMvcConfigurer {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:comment_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        }
        @Bean JdbcTemplate jdbcTemplate(DataSource ds) {
            JdbcTemplate jdbc = new JdbcTemplate(ds);
            jdbc.execute("CREATE TABLE tb_blog(id BIGINT AUTO_INCREMENT PRIMARY KEY,shop_id BIGINT,user_id BIGINT,title VARCHAR(255),images VARCHAR(255),content TEXT,liked INT,comments INT,create_time TIMESTAMP,update_time TIMESTAMP, CONSTRAINT reject_count CHECK(id<>103 OR comments<1))");
            jdbc.execute("CREATE TABLE tb_blog_comments(id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT,blog_id BIGINT,parent_id BIGINT,answer_id BIGINT,content VARCHAR(255),liked BIGINT DEFAULT 0,status INT,create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
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
        @Bean MemoryLikes memoryLikes() { return new MemoryLikes(); }
        @Bean StringRedisTemplate redis(MemoryLikes memory) { return memory.template(); }
        @Bean RedissonClient redisson() throws InterruptedException {
            RedissonClient client = mock(RedissonClient.class);
            RLock lock = mock(RLock.class);
            when(client.getLock(anyString())).thenReturn(lock);
            when(lock.tryLock(1, TimeUnit.SECONDS)).thenReturn(true);
            return client;
        }
        @Bean UserService users(UserMapper mapper) {
            UserService users = mock(UserService.class);
            when(users.listByIds(anyCollection())).thenAnswer(call -> mapper.selectByIds(call.getArgument(0)));
            return users;
        }
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

    /** 仅替代 Redis 网络边界；数据库、事务、Service 和 HTTP 安全使用真实实现。 */
    static class MemoryLikes {
        private final java.util.Map<String, java.util.Set<String>> relations = new java.util.HashMap<>();
        boolean unavailable;
        boolean failWrites;
        void check() { if (unavailable) throw new RedisConnectionFailureException("隔离测试故障"); }
        java.util.Set<String> members(String key) {
            check();
            return relations.computeIfAbsent(key, ignored -> new java.util.HashSet<>());
        }
        @SuppressWarnings("unchecked")
        StringRedisTemplate template() {
            StringRedisTemplate redis = mock(StringRedisTemplate.class);
            SetOperations<String, String> sets = mock(SetOperations.class, call -> {
                Object[] args = call.getRawArguments();
                String key = args.length == 0 ? null : (String) args[0];
                return switch (call.getMethod().getName()) {
                    case "size" -> (long) members(key).size();
                    case "isMember" -> members(key).contains(args[1]);
                    case "add", "remove" -> {
                        if (failWrites) throw new RedisConnectionFailureException("隔离测试写入故障");
                        long changed = 0;
                        for (Object member : (Object[]) args[1]) {
                            boolean applied = call.getMethod().getName().equals("add")
                                    ? members(key).add((String) member) : members(key).remove(member);
                            if (applied) changed++;
                        }
                        yield changed;
                    }
                    default -> RETURNS_DEFAULTS.answer(call);
                };
            });
            when(redis.opsForSet()).thenReturn(sets);
            when(redis.executePipelined(any(RedisCallback.class))).thenAnswer(call -> {
                check();
                java.util.List<Object> results = new java.util.ArrayList<>();
                RedisConnection connection = mock(RedisConnection.class);
                RedisSetCommands commands = mock(RedisSetCommands.class);
                when(connection.setCommands()).thenReturn(commands);
                when(commands.sCard(any())).thenAnswer(command -> {
                    Long value = (long) members(new String(command.getArgument(0), StandardCharsets.UTF_8)).size();
                    results.add(value); return value;
                });
                when(commands.sIsMember(any(), any())).thenAnswer(command -> {
                    Boolean value = members(new String(command.getArgument(0), StandardCharsets.UTF_8))
                            .contains(new String(command.getArgument(1), StandardCharsets.UTF_8));
                    results.add(value); return value;
                });
                ((RedisCallback<?>) call.getArgument(0)).doInRedis(connection);
                return results;
            });
            return redis;
        }
    }
}
