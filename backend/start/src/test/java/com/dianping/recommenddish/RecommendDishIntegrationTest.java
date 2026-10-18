package com.dianping.recommenddish;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.dianping.config.MapperConfiguration;
import com.dianping.config.SecurityConfig;
import com.dianping.dto.UserDTO;
import com.dianping.exception.GlobalExceptionHandler;
import com.dianping.filter.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 隔离数据库验证真实接口、事务及并发；不连接业务 MySQL 或 Redis。 */
@WebAppConfiguration
@SpringJUnitConfig(RecommendDishIntegrationTest.Config.class)
class RecommendDishIntegrationTest {
    private static final AtomicLong IDS = new AtomicLong(5000);
    private static final String COVER = "/imgs/blogs/a/b/550e8400-e29b-41d4-a716-446655440000.png";
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean DataSource dataSource;
    @MockitoBean JwtAuthenticationFilter authenticationFilter;
    private final ObjectMapper json = new ObjectMapper();
    private MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(authenticationFilter).doFilter(any(), any(), any());
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void guestCanReadAnEmptyExistingShopAndDistinguishMissingShop() throws Exception {
        long shop = newShop();
        mvc.perform(get(path(shop)).param("size", "6"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.records").isEmpty());
        mvc.perform(get(path(999999))).andExpect(status().isNotFound());
    }

    @Test
    void writeRequiresAuthenticationAndInvalidInputsAreRejected() throws Exception {
        long shop = newShop();
        mvc.perform(post(path(shop)).contentType(MediaType.APPLICATION_JSON).content(body(UUID.randomUUID().toString(), "烤鱼")))
                .andExpect(status().isUnauthorized());
        mvc.perform(put(path(shop) + "/1/recommendation")).andExpect(status().isUnauthorized());
        for (String name : List.of(" ", "菜".repeat(101))) {
            mvc.perform(post(path(shop)).with(actor(7)).contentType(MediaType.APPLICATION_JSON)
                    .content(body(UUID.randomUUID().toString(), name))).andExpect(status().isBadRequest());
        }
        mvc.perform(get(path(shop)).param("current", "0")).andExpect(status().isBadRequest());
        mvc.perform(get(path(shop)).param("size", "51")).andExpect(status().isBadRequest());
        mvc.perform(get(path(0))).andExpect(status().isBadRequest());
    }

    @Test
    void publishingCountsCreatorOnceAndSameSubmissionReplays() throws Exception {
        long shop = newShop();
        String key = UUID.randomUUID().toString();
        JsonNode first = publish(shop, 7, key, "  招牌烤鱼  ");
        JsonNode replay = publish(shop, 7, key, "招牌烤鱼");
        assertThat(replay.get("id").asLong()).isEqualTo(first.get("id").asLong());
        assertThat(first.get("recommendCount").asLong()).isEqualTo(1);
        assertThat(first.get("recommended").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_recommend_dish WHERE shop_id=?", Long.class, shop)).isEqualTo(1);
        mvc.perform(get(path(shop))).andExpect(jsonPath("$.data.records[0].name").value("招牌烤鱼"))
                .andExpect(jsonPath("$.data.records[0].recommended").value(false));
        mvc.perform(get(path(shop)).with(actor(7))).andExpect(jsonPath("$.data.records[0].recommended").value(true));
        mvc.perform(post(path(shop)).with(actor(7)).contentType(MediaType.APPLICATION_JSON).content(body(key, "另一道菜")))
                .andExpect(status().isConflict());
    }

    @Test
    void sameShopNameConflictsButAnotherShopCanUseTheName() throws Exception {
        long shop = newShop();
        publish(shop, 7, UUID.randomUUID().toString(), "烤鱼");
        mvc.perform(post(path(shop)).with(actor(8)).contentType(MediaType.APPLICATION_JSON)
                .content(body(UUID.randomUUID().toString(), " 烤鱼 "))).andExpect(status().isConflict());
        publish(newShop(), 8, UUID.randomUUID().toString(), "烤鱼");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_recommend_dish WHERE shop_id=?", Long.class, shop)).isEqualTo(1);
    }

    @Test
    void recommendationIsIdempotentAndCannotTargetAnotherShop() throws Exception {
        long shop = newShop();
        long dish = publish(shop, 7, UUID.randomUUID().toString(), "鱼头").get("id").asLong();
        for (int i = 0; i < 2; i++) {
            mvc.perform(put(path(shop) + "/" + dish + "/recommendation").with(actor(8)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.recommendCount").value(2))
                    .andExpect(jsonPath("$.data.recommended").value(true));
        }
        mvc.perform(put(path(newShop()) + "/" + dish + "/recommendation").with(actor(9)))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_recommend_dish_user WHERE dish_id=?", Long.class, dish)).isEqualTo(2);
    }

    @Test
    void concurrentRecommendationsKeepCountEqualToDistinctUsers() throws Exception {
        long shop = newShop();
        long dish = publish(shop, 7, UUID.randomUUID().toString(), "并发菜品").get("id").asLong();
        try (var pool = Executors.newFixedThreadPool(6)) {
            var start = new CountDownLatch(1);
            var futures = new java.util.ArrayList<Future<Integer>>();
            for (int i = 0; i < 12; i++) {
                long user = 20 + i % 4;
                futures.add(pool.submit(() -> {
                    start.await();
                    return mvc.perform(put(path(shop) + "/" + dish + "/recommendation").with(actor(user)))
                            .andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            for (var future : futures) assertThat(future.get(15, TimeUnit.SECONDS)).isEqualTo(200);
        }
        assertThat(jdbc.queryForObject("SELECT recommend_count FROM tb_recommend_dish WHERE id=?", Long.class, dish)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_recommend_dish_user WHERE dish_id=?", Long.class, dish)).isEqualTo(5);
    }

    @Test
    void pagesOrderByPeopleThenTimeThenIdWithoutLeakingOtherShops() throws Exception {
        long shop = newShop();
        long first = publish(shop, 7, UUID.randomUUID().toString(), "第一道").get("id").asLong();
        long second = publish(shop, 7, UUID.randomUUID().toString(), "第二道").get("id").asLong();
        long third = publish(shop, 7, UUID.randomUUID().toString(), "第三道").get("id").asLong();
        publish(newShop(), 7, UUID.randomUUID().toString(), "其他店");
        jdbc.update("UPDATE tb_recommend_dish SET create_time='2026-10-18 12:00:00' WHERE shop_id=?", shop);
        mvc.perform(put(path(shop) + "/" + first + "/recommendation").with(actor(8))).andExpect(status().isOk());
        mvc.perform(get(path(shop)).param("size", "2"))
                .andExpect(jsonPath("$.data.total").value(3)).andExpect(jsonPath("$.data.pages").value(2))
                .andExpect(jsonPath("$.data.records[0].id").value(first))
                .andExpect(jsonPath("$.data.records[1].id").value(third));
        mvc.perform(get(path(shop)).param("size", "2").param("current", "2"))
                .andExpect(jsonPath("$.data.records[0].id").value(second));
        mvc.perform(get(path(shop)).param("current", "99"))
                .andExpect(jsonPath("$.data.records").isEmpty()).andExpect(jsonPath("$.data.total").value(3));
        jdbc.update("UPDATE tb_recommend_dish SET create_time='2026-10-18 12:01:00' WHERE id=?", second);
        mvc.perform(get(path(shop)).param("size", "2"))
                .andExpect(jsonPath("$.data.records[0].id").value(first))
                .andExpect(jsonPath("$.data.records[1].id").value(second));
    }

    @Test
    void concurrentSameNameCreatesOneDishAndOneCreatorRecommendation() throws Exception {
        long shop = newShop();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var requests = new java.util.ArrayList<Future<Integer>>();
            for (long user : List.of(7L, 8L)) {
                requests.add(pool.submit(() -> {
                    start.await();
                    return mvc.perform(post(path(shop)).with(actor(user)).contentType(MediaType.APPLICATION_JSON)
                            .content(body(UUID.randomUUID().toString(), "同名菜"))).andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            assertThat(List.of(requests.get(0).get(15, TimeUnit.SECONDS), requests.get(1).get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_recommend_dish WHERE shop_id=?", Long.class, shop)).isEqualTo(1);
        mvc.perform(get(path(shop))).andExpect(jsonPath("$.data.records[0].recommendCount").value(1));
    }

    @Test
    void concurrentIdenticalSubmissionReturnsSameDish() throws Exception {
        long shop = newShop();
        String key = UUID.randomUUID().toString();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Long> request = () -> { start.await(); return publish(shop, 7, key, "重试菜品").get("id").asLong(); };
            var first = pool.submit(request); var second = pool.submit(request); start.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS)).isEqualTo(second.get(15, TimeUnit.SECONDS));
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_recommend_dish WHERE shop_id=?", Long.class, shop)).isEqualTo(1);
    }

    @Test
    void counterFailureRollsBackRecommendationAndDatabaseFailureIsNotEmptyData() throws Exception {
        long shop = newShop();
        long dish = publish(shop, 7, UUID.randomUUID().toString(), "回滚菜品").get("id").asLong();
        // 仅在隔离测试数据库中注入计数写失败，验证先写入的推荐关系也会回滚。
        jdbc.execute("ALTER TABLE tb_recommend_dish ADD CONSTRAINT rollback_count_" + dish
                + " CHECK (id<>" + dish + " OR recommend_count<=1)");
        mvc.perform(put(path(shop) + "/" + dish + "/recommendation").with(actor(8)))
                .andExpect(status().isServiceUnavailable());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_recommend_dish_user WHERE dish_id=?", Long.class, dish)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT recommend_count FROM tb_recommend_dish WHERE id=?", Long.class, dish)).isEqualTo(1);
        doThrow(new java.sql.SQLException("模拟数据库不可用")).when(dataSource).getConnection();
        mvc.perform(get(path(shop))).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void rejectsInvalidCoverSubmissionAndOverflowingPage() throws Exception {
        long shop = newShop();
        for (String cover : List.of("", "https://example.com/a.png", "/imgs/blogs/../../secret.png")) {
            mvc.perform(post(path(shop)).with(actor(7)).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("requestId", UUID.randomUUID().toString(), "name", "菜品", "coverImage", cover))))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post(path(shop)).with(actor(7)).contentType(MediaType.APPLICATION_JSON).content(body("bad-id", "菜品")))
                .andExpect(status().isBadRequest());
        mvc.perform(post(path(999999)).with(actor(7)).contentType(MediaType.APPLICATION_JSON)
                .content(body(UUID.randomUUID().toString(), "菜品"))).andExpect(status().isNotFound());
        mvc.perform(get(path(shop)).param("current", String.valueOf(Long.MAX_VALUE)).param("size", "50"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void doesNotMergeDifferentCaseNamesOrAcceptClientAuthor() throws Exception {
        long shop = newShop();
        publish(shop, 7, UUID.randomUUID().toString(), "Fish");
        String body = json.writeValueAsString(Map.of("requestId", UUID.randomUUID().toString(), "name", "fish", "coverImage", COVER, "userId", 999));
        mvc.perform(post(path(shop)).with(actor(7)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tb_recommend_dish WHERE shop_id=? AND user_id=7", Long.class, shop)).isEqualTo(2);
    }

    private long newShop() { long id = IDS.incrementAndGet(); jdbc.update("INSERT INTO tb_shop(id) VALUES(?)", id); return id; }
    private static String path(long shop) { return "/shop/" + shop + "/recommend-dishes"; }
    private static RequestPostProcessor actor(long id) {
        var user = new UserDTO(); user.setId(id);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(user, null, List.of()));
    }
    private String body(String key, String name) throws Exception {
        return json.writeValueAsString(Map.of("requestId", key, "name", name, "coverImage", COVER));
    }
    private JsonNode publish(long shop, long user, String key, String name) throws Exception {
        var result = mvc.perform(post(path(shop)).with(actor(user)).contentType(MediaType.APPLICATION_JSON)
                .content(body(key, name))).andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("data");
    }

    @Configuration
    @EnableWebMvc
    @Import({MapperConfiguration.class, GlobalExceptionHandler.class, SecurityConfig.class})
    @ComponentScan(basePackages = "com.dianping", useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
                    pattern = "com[.]dianping[.](controller[.]RecommendDishController|shopdetail[.].*RecommendDish(FacadeImpl|ApplicationService|RepositoryImpl))"))
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:dish_" + UUID.randomUUID()
                    + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "sa", "");
        }
        @Bean JdbcTemplate jdbcTemplate(DataSource ds) {
            var jdbc = new JdbcTemplate(ds);
            jdbc.execute("CREATE TABLE tb_shop(id BIGINT PRIMARY KEY)");
            var schema = new ResourceDatabasePopulator(new ClassPathResource("db/migrations/20261018_recommend_dish.sql"));
            schema.setSqlScriptEncoding("UTF-8");
            schema.execute(ds);
            return jdbc;
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(DataSource ds, JdbcTemplate initialized) throws Exception {
            var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
            var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(ds);
            factory.setConfiguration(config); factory.afterPropertiesSet();
            return new SqlSessionTemplate(factory.getObject());
        }
        @Bean DataSourceTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
    }
}
