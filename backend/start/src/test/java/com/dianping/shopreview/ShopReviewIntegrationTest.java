package com.dianping.shopreview;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.dianping.config.MapperConfiguration;
import com.dianping.shop.review.model.request.PublishShopReviewRequest;
import com.dianping.shop.review.model.request.ShopReviewPageRequest;
import com.dianping.shop.review.service.ShopReviewApplicationService;
import com.dianping.shopdetail.repository.ShopReviewRepositoryImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import static org.assertj.core.api.Assertions.*;

/** 使用隔离 H2 验证真实映射和事务，不连接本地 MySQL 或 Redis。 */
@SpringJUnitConfig(ShopReviewIntegrationTest.Config.class)
class ShopReviewIntegrationTest {
    @Autowired ShopReviewApplicationService service;
    @Autowired JdbcTemplate jdbc;

    @Test void publishesIndependentScoresAndReplacesHistoricalSummary() {
        jdbc.update("INSERT INTO tb_shop(id,score,comments) VALUES(101,49,1000)");
        Long id = service.publish(101L, 7L, request(UUID.randomUUID().toString(), "  很好  ", "4.5"));
        var page = service.page(new ShopReviewPageRequest(101L, 1L));
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.averageScore()).isEqualByComparingTo("4.5");
        assertThat(page.records().getFirst().id()).isEqualTo(id);
        assertThat(page.records().getFirst().tasteScore()).isEqualByComparingTo("3");
        assertThat(page.records().getFirst().content()).isEqualTo("很好");
        assertThat(page.records().getFirst().userName()).isEqualTo("小林");
        assertThat(page.records().getFirst().userIcon()).isEqualTo("/avatar.png");
        assertThat(jdbc.queryForMap("SELECT score,comments FROM tb_shop WHERE id=101"))
                .containsEntry("SCORE", 45).containsEntry("COMMENTS", 1);
    }

    @Test void readsCurrentAuthorAndKeepsReviewsWhenAuthorIsMissing() {
        jdbc.update("INSERT INTO tb_shop(id,score,comments) VALUES(102,0,0)");
        jdbc.update("INSERT INTO tb_user(id,nick_name,icon) VALUES(8,'旧昵称','/old.png')");
        service.publish(102L, 8L, request(UUID.randomUUID().toString(), "当前资料", "4"));
        jdbc.update("UPDATE tb_user SET nick_name='新昵称',icon='/new.png' WHERE id=8");
        service.publish(102L, 999L, request(UUID.randomUUID().toString(), "作者资料缺失", "4.5"));

        var page = service.page(new ShopReviewPageRequest(102L, 1L));
        assertThat(page.total()).isEqualTo(2);
        var authorReview = page.records().stream().filter(item -> item.userId().equals(8L)).findFirst().orElseThrow();
        assertThat(authorReview.userName()).isEqualTo("新昵称");
        assertThat(authorReview.userIcon()).isEqualTo("/new.png");
        var missingReview = page.records().stream().filter(item -> item.userId().equals(999L)).findFirst().orElseThrow();
        assertThat(missingReview.userName()).isEqualTo("用户资料暂缺");
        assertThat(missingReview.userIcon()).isNull();
    }

    @Test void calculatesRoundedSummaryAndPreservesItOnAnOutOfRangePage() {
        jdbc.update("INSERT INTO tb_shop(id,score,comments) VALUES(104,0,0)");
        var empty = service.page(new ShopReviewPageRequest(104L, 1L));
        assertThat(empty.averageScore()).isNull();
        assertThat(empty.total()).isZero();
        service.publish(104L, 7L, request(UUID.randomUUID().toString(), "第一条", "4"));
        service.publish(104L, 7L, request(UUID.randomUUID().toString(), "第二条", "4.5"));

        var page = service.page(new ShopReviewPageRequest(104L, 2L));
        assertThat(page.records()).isEmpty();
        assertThat(page.total()).isEqualTo(2);
        assertThat(page.averageScore()).isEqualByComparingTo("4.3");
        assertThat(jdbc.queryForMap("SELECT score,comments FROM tb_shop WHERE id=104"))
                .containsEntry("SCORE", 43).containsEntry("COMMENTS", 2);
    }

    static PublishShopReviewRequest request(String id, String content, String score) {
        return new PublishShopReviewRequest(id, new BigDecimal(score), new BigDecimal("3"),
                new BigDecimal("4"), new BigDecimal("5"), content, List.of("TASTY"));
    }

    @Configuration
    @Import({MapperConfiguration.class, ShopReviewRepositoryImpl.class, ShopReviewApplicationService.class})
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:review_"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        }
        @Bean JdbcTemplate jdbcTemplate(DataSource ds) {
            var jdbc = new JdbcTemplate(ds);
            jdbc.execute("CREATE TABLE tb_shop(id BIGINT PRIMARY KEY,score INT,comments INT,CONSTRAINT reject_summary CHECK(id<>103 OR comments=0))");
            jdbc.execute("CREATE TABLE tb_user(id BIGINT PRIMARY KEY,nick_name VARCHAR(255),icon VARCHAR(255))");
            jdbc.execute("CREATE TABLE tb_shop_review(id BIGINT AUTO_INCREMENT PRIMARY KEY,shop_id BIGINT NOT NULL,user_id BIGINT NOT NULL,request_id VARCHAR(36) NOT NULL,overall_score INT,taste_score INT,environment_score INT,service_score INT,content VARCHAR(4000),tags VARCHAR(2000),create_time TIMESTAMP,CONSTRAINT uk_review_submission UNIQUE(user_id,request_id))");
            jdbc.update("INSERT INTO tb_user VALUES(7,'小林','/avatar.png')");
            return jdbc;
        }
        @Bean SqlSessionTemplate sqlSessionTemplate(DataSource ds, JdbcTemplate initialized) throws Exception {
            var configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            var factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(ds); factory.setConfiguration(configuration); factory.afterPropertiesSet();
            return new SqlSessionTemplate(factory.getObject());
        }
        @Bean DataSourceTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
    }
}
