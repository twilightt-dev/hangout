package com.dianping.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.dianping.entity.Shop;
import com.dianping.mapper.ShopMapper;
import com.dianping.service.ShopService;
import com.dianping.shared.cache.ShopCacheAccessException;
import com.dianping.shared.cache.ShopCacheProtection;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 通过真实 Mapper、H2 和 Spring 事务代理验证新增、删除的回滚及事务结束后解锁。 */
class ShopCacheTransactionTest {
    private JdbcTemplate jdbc;
    private ShopService service;
    private ShopCacheProtection protection;
    private ShopCacheProtection.Lease lease;

    /** 构造隔离数据库，Redis 协调仅作为可注入故障的外部端口。 */
    @BeforeEach void setup() throws Exception {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:shop_tx_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("""
                CREATE TABLE tb_shop(id BIGINT AUTO_INCREMENT PRIMARY KEY,name VARCHAR(100),type_id BIGINT,
                  images VARCHAR(100),area VARCHAR(100),address VARCHAR(100),x DOUBLE,y DOUBLE,avg_price BIGINT,
                  sold INT,comments INT,score INT,open_hours VARCHAR(100),create_time TIMESTAMP,update_time TIMESTAMP)
                """);
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(ShopMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(ds);
        factory.setConfiguration(configuration);
        factory.afterPropertiesSet();
        var mapper = new SqlSessionTemplate(factory.getObject()).getMapper(ShopMapper.class);
        protection = mock(ShopCacheProtection.class);
        lease = mock(ShopCacheProtection.Lease.class);
        when(protection.tryLock(anyLong(), any(Duration.class))).thenReturn(lease);
        var redis = mock(StringRedisTemplate.class);
        when(redis.opsForGeo()).thenReturn(mock(GeoOperations.class));
        var target = new ShopServiceImpl();
        ReflectionTestUtils.setField(target, "baseMapper", mapper);
        ReflectionTestUtils.setField(target, "protection", protection);
        ReflectionTestUtils.setField(target, "stringRedisTemplate", redis);
        var proxy = new ProxyFactory(target);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds),
                new AnnotationTransactionAttributeSource()));
        service = (ShopService) proxy.getProxy();
    }

    /** 布隆过滤器追加失败必须回滚真实插入。 */
    @Test void failedBloomAppendRollsBackInsertedShop() {
        doThrow(unavailable()).when(protection).add(anyLong());
        assertThatThrownBy(() -> service.save(new Shop().setName("未提交")))
                .isInstanceOf(ShopCacheAccessException.class);
        assertThat(count()).isZero();
        verify(lease).close();
    }

    /** 清理旧空值失败不能返回新增成功；已追加的过滤器 ID 可以保留。 */
    @Test void failedEvictionRollsBackInsertedShop() {
        doThrow(unavailable()).when(protection).evict(anyLong());
        assertThatThrownBy(() -> service.save(new Shop().setName("未提交")))
                .isInstanceOf(ShopCacheAccessException.class);
        assertThat(count()).isZero();
        verify(protection).add(anyLong());
        verify(lease).close();
    }

    /** 删除后清理缓存失败，数据库记录必须仍在。 */
    @Test void failedDeletionEvictionRollsBackDeletedShop() {
        jdbc.update("INSERT INTO tb_shop(id,name) VALUES(7,'保留')");
        doThrow(unavailable()).when(protection).evict(7L);
        assertThatThrownBy(() -> service.removeById(7L)).isInstanceOf(ShopCacheAccessException.class);
        assertThat(count()).isOne();
        verify(protection, never()).add(anyLong());
        verify(lease).close();
    }

    /** 提交前失去锁时拒绝数据库提交，验证不是业务方法返回就提前解锁。 */
    @Test void lostOwnershipBeforeCommitRollsBackAndThenReleases() {
        doNothing().doThrow(unavailable()).when(lease).verifyOwnership();
        assertThatThrownBy(() -> service.save(new Shop().setName("不能提交")))
                .isInstanceOf(ShopCacheAccessException.class);
        assertThat(count()).isZero();
        verify(lease).close();
    }

    /** 解锁回调执行时真实数据库事务已经完成，成功店铺立即可读取。 */
    @Test void successfulCreationReleasesOnlyAfterCommit() {
        var visibleAtRelease = new java.util.concurrent.atomic.AtomicInteger(-1);
        doAnswer(call -> {
            verify(lease, never()).close();
            return null;
        }).when(protection).evict(anyLong());
        doAnswer(call -> {
            try (var connection = jdbc.getDataSource().getConnection();
                 var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT COUNT(*) FROM tb_shop")) {
                rows.next();
                visibleAtRelease.set(rows.getInt(1));
            }
            return null;
        }).when(lease).close();
        var shop = new Shop().setName("已提交");
        assertThat(service.save(shop)).isTrue();
        assertThat(service.getById(shop.getId()).getName()).isEqualTo("已提交");
        assertThat(visibleAtRelease).hasValue(1);
        verify(lease).close();
    }

    /** 只读取隔离夹具表的记录数。 */
    private int count() { return jdbc.queryForObject("SELECT COUNT(*) FROM tb_shop", Integer.class); }
    /** 生成事务应向外传播的 Redis 协调故障。 */
    private static ShopCacheAccessException unavailable() { return new ShopCacheAccessException("测试 Redis 故障", null); }
}
