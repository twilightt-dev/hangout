package com.dianping.setmeal;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.dianping.config.MapperConfiguration;
import com.dianping.config.SecurityConfig;
import com.dianping.exception.GlobalExceptionHandler;
import com.dianping.filter.JwtAuthenticationFilter;
import jakarta.servlet.FilterChain;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 通过真实 HTTP、门面、应用服务与 MyBatis 查询隔离 H2，不连接本地业务数据库。 */
@WebAppConfiguration
@SpringJUnitConfig(ShopSetmealIntegrationTest.Config.class)
class ShopSetmealIntegrationTest {
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean DataSource dataSource;
    @MockitoBean JwtAuthenticationFilter authenticationFilter;
    static final AtomicLong IDS = new AtomicLong(1000);
    MockMvc mvc;

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
    void guestReadsPublishedSetmealsWithTheirFixedItems() throws Exception {
        jdbc.update("INSERT INTO tb_shop VALUES(101)");
        jdbc.update("INSERT INTO tb_shop_setmeal(id,shop_id,name,cover_image,price,people,rules,status,sort) VALUES(1,101,'双人餐','/meal.png',9900,2,'无需预约',1,0)");
        jdbc.update("INSERT INTO tb_shop_setmeal_item(id,setmeal_id,name,quantity,unit,sort) VALUES(1,1,'烤鱼',1,'份',0)");

        mvc.perform(get("/shop/101/setmeals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].name").value("双人餐"))
                .andExpect(jsonPath("$.data.records[0].price").value(9900))
                .andExpect(jsonPath("$.data.records[0].items[0].name").value("烤鱼"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "not-a-number"})
    void rejectsInvalidShopId(String shopId) throws Exception {
        mvc.perform(get("/shop/" + shopId + "/setmeals"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void distinguishesMissingShopFromAnExistingShopWithoutPublishedSetmeals() throws Exception {
        jdbc.update("INSERT INTO tb_shop VALUES(102)");
        mvc.perform(get("/shop/102/setmeals"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"code\":1,\"data\":{\"records\":[],\"total\":0}}"));
        mvc.perform(get("/shop/999/setmeals"))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "cover", "rules", "price", "originalPrice", "people", "emptyItems", "itemName", "quantity", "unit"})
    void refusesIncompleteOrInvalidPublishedSetmeal(String field) throws Exception {
        long id = IDS.incrementAndGet();
        jdbc.update("INSERT INTO tb_shop VALUES(?)", id);
        jdbc.update("INSERT INTO tb_shop_setmeal(id,shop_id,name,cover_image,price,people,rules,status,sort) VALUES(?,?,'有效套餐','/meal.png',9900,2,'无需预约',1,0)", id, id);
        if (!field.equals("emptyItems"))
            jdbc.update("INSERT INTO tb_shop_setmeal_item(id,setmeal_id,name,quantity,unit,sort) VALUES(?,?,'菜品',1,'份',0)", id, id);
        String mutation = switch (field) {
            case "name" -> "UPDATE tb_shop_setmeal SET name=' ' WHERE id=?";
            case "cover" -> "UPDATE tb_shop_setmeal SET cover_image='' WHERE id=?";
            case "rules" -> "UPDATE tb_shop_setmeal SET rules=' ' WHERE id=?";
            case "price" -> "UPDATE tb_shop_setmeal SET price=0 WHERE id=?";
            case "originalPrice" -> "UPDATE tb_shop_setmeal SET original_price=100 WHERE id=?";
            case "people" -> "UPDATE tb_shop_setmeal SET people=0 WHERE id=?";
            case "itemName" -> "UPDATE tb_shop_setmeal_item SET name=' ' WHERE id=?";
            case "quantity" -> "UPDATE tb_shop_setmeal_item SET quantity=0 WHERE id=?";
            case "unit" -> "UPDATE tb_shop_setmeal_item SET unit=' ' WHERE id=?";
            default -> null;
        };
        if (mutation != null) jdbc.update(mutation, id);
        mvc.perform(get("/shop/" + id + "/setmeals"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void databaseUnavailableReturns503InsteadOfAnEmptyList() throws Exception {
        doThrow(new SQLException("测试数据库不可用")).when(dataSource).getConnection();
        mvc.perform(get("/shop/101/setmeals"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(0));
    }
    @Test
    void pagesPublishedMealsWithItemsAndStableTieOrder() throws Exception {
        jdbc.update("INSERT INTO tb_shop VALUES(301)");
        for (long id=301;id<=303;id++) {
            jdbc.update("INSERT INTO tb_shop_setmeal(id,shop_id,name,cover_image,price,people,rules,status,sort) VALUES(?,301,'套餐','/meal.png',9900,2,'规则',1,0)",id);
            jdbc.update("INSERT INTO tb_shop_setmeal_item(id,setmeal_id,name,quantity,unit,sort) VALUES(?,?,'主菜',1,'份',0)",id,id);
        }
        mvc.perform(get("/shop/301/setmeals").param("size","2").param("current","2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.pages").value(2))
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(303))
                .andExpect(jsonPath("$.data.records[0].items[0].name").value("主菜"));
    }

    @Test
    void repositoryFiltersByShopAndStatusAndKeepsStableSetmealAndItemOrder() {
        var repository = context.getBean(com.dianping.shopdetail.repository.ShopSetmealRepository.class);
        jdbc.update("INSERT INTO tb_shop VALUES(201),(202)");
        jdbc.update("INSERT INTO tb_shop_setmeal(id,shop_id,name,cover_image,price,people,rules,status,sort) VALUES(201,201,'次序二','/meal.png',9900,2,'规则',1,2),(202,201,'同序靠前','/meal.png',9900,2,'规则',1,1),(203,201,'同序靠后','/meal.png',9900,2,'规则',1,1),(204,201,'下架','/meal.png',9900,2,'规则',0,0),(205,202,'其他店','/meal.png',9900,2,'规则',1,0)");
        jdbc.update("INSERT INTO tb_shop_setmeal_item(id,setmeal_id,name,quantity,unit,sort) VALUES(201,201,'菜品',1,'份',0),(202,202,'主菜',1.5,'份',1),(203,202,'饮料',2,'杯',0),(204,203,'配菜',1,'份',0),(205,204,'下架菜品',1,'份',0),(206,205,'其他店菜品',1,'份',0)");

        var meals = repository.findPublishedByShopId(201L);
        assertThat(meals).extracting(com.dianping.shopdetail.model.ShopSetmeal::id).containsExactly(202L, 203L, 201L);
        assertThat(meals.getFirst().items()).extracting(com.dianping.shopdetail.model.ShopSetmealItem::name)
                .containsExactly("饮料", "主菜");
        assertThat(meals.getFirst().items().getLast().quantity()).isEqualByComparingTo("1.5");
    }

    @Configuration
    @EnableWebMvc
    @Import({MapperConfiguration.class, GlobalExceptionHandler.class, SecurityConfig.class})
    @ComponentScan(basePackages = "com.dianping", useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
                    pattern = "com\\.dianping\\.(controller\\.ShopSetmealController|(shopdetail\\..*ShopSetmeal(FacadeImpl|RepositoryImpl)|shop\\.setmeal\\.service\\.ShopSetmealApplicationService))"))
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:setmeal_" + UUID.randomUUID()
                    + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        }
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) {
            var jdbc = new JdbcTemplate(dataSource);
            jdbc.execute("CREATE TABLE tb_shop(id BIGINT PRIMARY KEY)");
            var schema = new ResourceDatabasePopulator(new ClassPathResource("db/migrations/20261018_shop_setmeal.sql"));
            schema.setSqlScriptEncoding("UTF-8");
            schema.execute(dataSource);
            return jdbc;
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(DataSource dataSource, JdbcTemplate initialized) throws Exception {
            var configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            var factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setConfiguration(configuration);
            factory.afterPropertiesSet();
            return new SqlSessionTemplate(factory.getObject());
        }
        @Bean DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }
}
