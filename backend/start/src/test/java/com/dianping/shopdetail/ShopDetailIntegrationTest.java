package com.dianping.shopdetail;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.dianping.config.*;
import com.dianping.controller.*;
import com.dianping.exception.GlobalExceptionHandler;
import com.dianping.filter.JwtAuthenticationFilter;
import com.dianping.shopdetail.impl.*;
import com.dianping.shopdetail.service.*;
import com.dianping.shopdetail.repository.*;
import com.dianping.shopdetail.task.*;
import com.dianping.shopdetail.config.ShopDetailConfiguration;
import com.dianping.shared.taskchain.config.TaskChainConfiguration;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 真实 HTTP 到 JDBC/MyBatis 的隔离集成测试，不访问用户数据库。 */
@WebAppConfiguration
@SpringJUnitConfig(ShopDetailIntegrationTest.Config.class)
class ShopDetailIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean JwtAuthenticationFilter filter;
    @MockitoBean StringRedisTemplate redis;
    MockMvc mvc;
    @BeforeEach void setup() throws Exception {
        doAnswer(call -> { ((FilterChain)call.getArgument(2)).doFilter(call.getArgument(0),call.getArgument(1));return null; })
                .when(filter).doFilter(any(),any(),any());
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    private void shop(long id, long type, Double x, Double y) {
        jdbc.update("INSERT INTO tb_shop(id,name,type_id,x,y,score,comments) VALUES(?,?,?,?,?,49,999)",id,"店铺"+id,type,x,y);
    }
    @Test void guestGetsAggregatedEmptySectionsAndFreshStatisticsInsteadOfOldShopFields() throws Exception {
        shop(101,1,120.0,30.0);
        mvc.perform(get("/shop/101/detail")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shop.id").value(101))
                .andExpect(jsonPath("$.data.statistics.status").value("EMPTY"))
                .andExpect(jsonPath("$.data.statistics.data.totalReviews").value(0))
                .andExpect(jsonPath("$.data.statistics.data.averageScore").isEmpty())
                .andExpect(jsonPath("$.data.reviews.data.size").value(3))
                .andExpect(jsonPath("$.data.recommendations.data.size").value(6))
                .andExpect(jsonPath("$.data.blogs.data.size").value(4))
                .andExpect(jsonPath("$.data.setmeals.data.size").value(4))
                .andExpect(jsonPath("$.data.relatedShops.status").value("EMPTY"));
    }
    @Test void relatedUsesShopCenterTypeRadiusAndStableDistanceOrder() throws Exception {
        shop(201,2,120.0,30.0); shop(202,2,120.001,30.0); shop(203,2,120.001,30.0);
        shop(204,3,120.0001,30.0);shop(205,2,121.0,30.0);
        mvc.perform(get("/shop/201/related-shops").param("size","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.data.total").value(2))
                .andExpect(jsonPath("$.data.data.records[0].id").value(202));
        mvc.perform(get("/shop/201/related-shops").param("size","1").param("current","2"))
                .andExpect(jsonPath("$.data.data.records[0].id").value(203));
    }
    @Test void missingCoordinatesAreDifferentFromEmptyResults() throws Exception {
        shop(301,3,null,null);
        mvc.perform(get("/shop/301/detail")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.relatedShops.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.relatedShops.message").value("缺少位置信息，暂无法推荐周边店铺"));
    }
    @Test void voucherPaginationFiltersExpiredAndOrdersPurchasableThenPriceAndId() throws Exception {
        shop(401,4,120.0,30.0);
        jdbc.update("INSERT INTO tb_voucher(id,shop_id,title,pay_value,actual_value,type,status) VALUES(401,401,'普通',1000,1200,0,1),(402,401,'预告',100,200,1,1),(403,401,'过期',10,20,1,1),(404,401,'售罄',100,200,1,1),(405,401,'下架',1,2,0,0)");
        LocalDateTime now=LocalDateTime.now();
        jdbc.update("INSERT INTO tb_seckill_voucher(voucher_id,stock,begin_time,end_time) VALUES(402,10,?,?),(403,10,?,?),(404,0,?,?)",
                now.plusDays(1),now.plusDays(2),now.minusDays(2),now.minusDays(1),now.minusDays(1),now.plusDays(1));
        mvc.perform(get("/shop/401/vouchers").param("size","2")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records[0].id").value(401))
                .andExpect(jsonPath("$.data.records[1].availability").value("UPCOMING"));
    }
    @Test void blogPreviewIsShopScopedOrderedAndUsesCurrentAuthor() throws Exception {
        shop(501,5,120.0,30.0);
        jdbc.update("INSERT INTO tb_user(id,nick_name,icon) VALUES(501,'当前昵称','/current.png')");
        jdbc.update("INSERT INTO tb_blog(id,shop_id,user_id,title,liked,create_time) VALUES(501,501,501,'较早',5,?),(502,501,501,'较新',5,?),(503,501,501,'低赞',1,?),(504,999,501,'别店',100,?)",
                LocalDateTime.now().minusDays(2),LocalDateTime.now().minusDays(1),LocalDateTime.now(),LocalDateTime.now());
        mvc.perform(get("/shop/501/blogs").param("size","1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records[0].id").value(502))
                .andExpect(jsonPath("$.data.records[0].name").value("当前昵称"))
                .andExpect(jsonPath("$.data.records[0].isLike").value(false));
    }
    @Test void rejectsInvalidPagesAndDistinguishesMissingShop() throws Exception {
        mvc.perform(get("/shop/0/detail")).andExpect(status().isBadRequest());
        mvc.perform(get("/shop/99999/detail")).andExpect(status().isNotFound());
        mvc.perform(get("/shop/1/blogs").param("size","51")).andExpect(status().isBadRequest());
        mvc.perform(get("/shop/1/setmeals").param("current","0")).andExpect(status().isBadRequest());
    }
    @Test void trustedPrincipalIsPassedToAsyncQueriesAndGuestHasNoPersonalState() throws Exception {
        shop(601,6,120.0,30.0);
        jdbc.update("INSERT INTO tb_blog(id,shop_id,user_id,title) VALUES(601,601,601,'登录状态')");
        jdbc.update("INSERT INTO tb_recommend_dish(id,shop_id,user_id,request_id,name,name_key,cover_image,recommend_count) VALUES(601,601,601,?,'烤鱼',?,'/imgs/blogs/a/b/550e8400-e29b-41d4-a716-446655440000.png',1)", UUID.randomUUID().toString(), "烤鱼".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        jdbc.update("INSERT INTO tb_recommend_dish_user(dish_id,user_id) VALUES(601,7)");
        var sets = org.mockito.Mockito.<org.springframework.data.redis.core.SetOperations<String,String>>mock();
        org.mockito.Mockito.when(redis.opsForSet()).thenReturn(sets);
        org.mockito.Mockito.when(sets.isMember("blog:like:601", "7")).thenReturn(true);
        var user = new com.dianping.dto.UserDTO(); user.setId(7L);
        var authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(user,null,java.util.List.of());
        mvc.perform(get("/shop/601/detail").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recommendations.data.records[0].recommended").value(true))
                .andExpect(jsonPath("$.data.blogs.data.records[0].isLike").value(true));
        mvc.perform(get("/shop/601/detail")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recommendations.data.records[0].recommended").value(false))
                .andExpect(jsonPath("$.data.blogs.data.records[0].isLike").value(false));
    }
    @Test void tagEvidenceUsesRecentHistoryBeyondPreviewAndStatisticsUseAllReviews() throws Exception {
        shop(701,7,120.0,30.0);
        LocalDateTime now = LocalDateTime.now();
        // 最近三条没有标签；更早的有效样本仍贡献口碑，九十天外样本只参与全店评分。
        for (int i=0;i<5;i++) jdbc.update("INSERT INTO tb_shop_review(shop_id,user_id,request_id,overall_score,taste_score,environment_score,service_score,content,tags,create_time) VALUES(701,?,?,50,50,50,50,'评价',?,?)",
                701+i,UUID.randomUUID().toString(),i<2 ? "[\"TASTY\"]" : "[]",now.minusDays(5-i));
        jdbc.update("INSERT INTO tb_shop_review(shop_id,user_id,request_id,overall_score,taste_score,environment_score,service_score,content,tags,create_time) VALUES(701,709,?,10,10,10,10,'旧评价','[\"NICE_ENVIRONMENT\"]',?)",UUID.randomUUID().toString(),now.minusDays(91));
        jdbc.update("INSERT INTO tb_blog(id,shop_id,user_id,title,content,create_time) VALUES(701,701,709,'体验','环境好',?),(702,701,710,'体验','环境好',?)",now.minusDays(91),now);
        mvc.perform(get("/shop/701/detail")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviews.data.records.length()").value(3))
                .andExpect(jsonPath("$.data.statistics.data.totalReviews").value(6))
                .andExpect(jsonPath("$.data.statistics.data.averageScore").value(4.3))
                .andExpect(jsonPath("$.data.decisionTags.data.length()").value(1))
                .andExpect(jsonPath("$.data.decisionTags.data[0].code").value("TASTY"))
                .andExpect(jsonPath("$.data.decisionTags.data[0].userCount").value(2));
    }
    @Configuration @EnableWebMvc
    @Import({MapperConfiguration.class,SecurityConfig.class,GlobalExceptionHandler.class,
            TaskChainConfiguration.class,ShopDetailConfiguration.class,
            ShopDetailController.class,ShopSetmealController.class,
            ShopDetailFacadeImpl.class,ShopSetmealFacadeImpl.class,
            ShopDetailApplicationService.class,ShopDetailQueryService.class,ShopSetmealApplicationService.class,
            ShopDetailRepositoryImpl.class,ShopVoucherRepositoryImpl.class,ShopSetmealRepositoryImpl.class,
            ShopReviewRepositoryImpl.class,RecommendDishRepositoryImpl.class,
            QueryShopBaseNode.class,QueryShopVoucherNode.class,QueryShopSetmealNode.class,QueryRecommendationNode.class,
            QueryShopReviewNode.class,QueryShopBlogNode.class,AssembleShopDetailNode.class,EnrichShopDetailNode.class})
    static class Config {
        @Bean DataSource dataSource() { return new DriverManagerDataSource("jdbc:h2:mem:detail_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""); }
        @Bean JdbcTemplate jdbcTemplate(DataSource source) {
            var jdbc=new JdbcTemplate(source);
            jdbc.execute("CREATE TABLE tb_shop(id BIGINT PRIMARY KEY,name VARCHAR(200),type_id BIGINT,images VARCHAR(1000),area VARCHAR(200),address VARCHAR(500),x DOUBLE,y DOUBLE,avg_price BIGINT,open_hours VARCHAR(200),score INT,comments INT)");
            jdbc.execute("CREATE TABLE tb_user(id BIGINT PRIMARY KEY,nick_name VARCHAR(200),icon VARCHAR(1000))");
            jdbc.execute("CREATE TABLE tb_blog(id BIGINT PRIMARY KEY,shop_id BIGINT,user_id BIGINT,title VARCHAR(200),images VARCHAR(1000),content VARCHAR(5000),liked INT DEFAULT 0,comments INT DEFAULT 0,create_time TIMESTAMP,update_time TIMESTAMP)");
            jdbc.execute("CREATE TABLE tb_voucher(id BIGINT PRIMARY KEY,shop_id BIGINT,title VARCHAR(200),sub_title VARCHAR(200),rules VARCHAR(1000),pay_value BIGINT,actual_value BIGINT,type INT,status INT)");
            jdbc.execute("CREATE TABLE tb_seckill_voucher(voucher_id BIGINT PRIMARY KEY,stock INT,begin_time TIMESTAMP,end_time TIMESTAMP)");
            var schema=new ResourceDatabasePopulator(new ClassPathResource("db/migrations/20261016_shop_review.sql"),
                    new ClassPathResource("db/migrations/20261018_shop_setmeal.sql"),new ClassPathResource("db/migrations/20261018_recommend_dish.sql"));
            schema.setSqlScriptEncoding("UTF-8"); schema.execute(source);
            return jdbc;
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(DataSource source,JdbcTemplate initialized) throws Exception {
            var config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);
            var factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(source);factory.setConfiguration(config);factory.afterPropertiesSet();
            return new SqlSessionTemplate(factory.getObject());
        }
        @Bean DataSourceTransactionManager transactionManager(DataSource source) { return new DataSourceTransactionManager(source); }
    }
}
