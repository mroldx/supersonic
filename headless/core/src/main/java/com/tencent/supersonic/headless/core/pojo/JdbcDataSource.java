package com.tencent.supersonic.headless.core.pojo;

import com.tencent.supersonic.headless.api.pojo.enums.DataType;
import com.tencent.supersonic.headless.api.pojo.response.DatabaseResp;
import com.tencent.supersonic.headless.core.utils.JdbcDataSourceUtils;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Component
public class JdbcDataSource {

    private static final Object lockLock = new Object();
    private static volatile Map<String, HikariDataSource> dataSourceMap = new ConcurrentHashMap<>();
    private static volatile Map<String, Lock> dataSourceLockMap = new ConcurrentHashMap<>();

    @Value("${source.lock-time:30}")
    @Getter
    protected Long lockTime;

    @Value("${source.max-active:2}")
    @Getter
    protected int maxActive;

    @Value("${source.initial-size:0}")
    @Getter
    protected int initialSize;

    @Value("${source.min-idle:1}")
    @Getter
    protected int minIdle;

    @Value("${source.max-wait:60000}")
    @Getter
    protected long maxWait;

    @Value("${source.time-between-eviction-runs-millis:2000}")
    @Getter
    protected long timeBetweenEvictionRunsMillis;

    @Value("${source.min-evictable-idle-time-millis:600000}")
    @Getter
    protected long minEvictableIdleTimeMillis;

    @Value("${source.max-evictable-idle-time-millis:900000}")
    @Getter
    protected long maxEvictableIdleTimeMillis;

    @Value("${source.time-between-connect-error-millis:60000}")
    @Getter
    protected long timeBetweenConnectErrorMillis;

    @Value("${source.test-while-idle:true}")
    @Getter
    protected boolean testWhileIdle;

    @Value("${source.test-on-borrow:false}")
    @Getter
    protected boolean testOnBorrow;

    @Value("${source.test-on-return:false}")
    @Getter
    protected boolean testOnReturn;

    @Value("${source.break-after-acquire-failure:true}")
    @Getter
    protected boolean breakAfterAcquireFailure;

    @Value("${source.connection-error-retry-attempts:1}")
    @Getter
    protected int connectionErrorRetryAttempts;

    @Value("${source.keep-alive:false}")
    @Getter
    protected boolean keepAlive;

    @Value("${source.validation-query-timeout:5}")
    @Getter
    protected int validationQueryTimeout;

    @Value("${source.validation-query:select 1}")
    @Getter
    protected String validationQuery;

    private Lock getDataSourceLock(String key) {
        if (dataSourceLockMap.containsKey(key)) {
            return dataSourceLockMap.get(key);
        }

        synchronized (lockLock) {
            if (dataSourceLockMap.containsKey(key)) {
                return dataSourceLockMap.get(key);
            }
            Lock lock = new ReentrantLock();
            dataSourceLockMap.put(key, lock);
            return lock;
        }
    }

    public void removeDatasource(DatabaseResp database) {

        String key = getDataSourceKey(database);

        Lock lock = getDataSourceLock(key);

        if (!lock.tryLock()) {
            return;
        }

        try {
            HikariDataSource hikariDataSource = dataSourceMap.remove(key);
            if (hikariDataSource != null) {
                hikariDataSource.close();
            }

            dataSourceLockMap.remove(key);
        } finally {
            lock.unlock();
        }
    }

    public HikariDataSource getDataSource(DatabaseResp database) throws RuntimeException {

        String name = database.getName();
        String jdbcUrl = database.getUrl();
        String username = database.getUsername();
        String password = database.passwordDecrypt();

        String key = getDataSourceKey(database);

        HikariDataSource hikariDataSource = dataSourceMap.get(key);
        if (hikariDataSource != null && !hikariDataSource.isClosed()) {
            return hikariDataSource;
        }

        Lock lock = getDataSourceLock(key);

        try {
            if (!lock.tryLock(lockTime, TimeUnit.SECONDS)) {
                hikariDataSource = dataSourceMap.get(key);
                if (hikariDataSource != null && !hikariDataSource.isClosed()) {
                    return hikariDataSource;
                }
                throw new RuntimeException("Unable to get datasource for jdbcUrl: " + jdbcUrl);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Unable to get datasource for jdbcUrl: " + jdbcUrl);
        }

        hikariDataSource = dataSourceMap.get(key);
        if (hikariDataSource != null && !hikariDataSource.isClosed()) {
            lock.unlock();
            return hikariDataSource;
        }

        hikariDataSource = new HikariDataSource();

        try {
            String className = JdbcDataSourceUtils.getDriverClassName(jdbcUrl);
            try {
                Class.forName(className);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Unable to get driver instance for jdbcUrl: " + jdbcUrl);
            }

            hikariDataSource.setDriverClassName(className);
            hikariDataSource.setPoolName(name);
            hikariDataSource.setJdbcUrl(jdbcUrl);
            hikariDataSource.setUsername(username);

            if (!jdbcUrl.toLowerCase().contains(DataType.PRESTO.getFeature()))  {
                hikariDataSource.setPassword(password);
            }

            // 连接池大小配置
            hikariDataSource.setMaximumPoolSize(maxActive);
            hikariDataSource.setMinimumIdle(minIdle);

            // 超时配置
            hikariDataSource.setConnectionTimeout(maxWait);
            hikariDataSource.setIdleTimeout(minEvictableIdleTimeMillis);
            hikariDataSource.setValidationTimeout(validationQueryTimeout  * 1000L);

            // 泄漏检测
            hikariDataSource.setLeakDetectionThreshold(3600  * 1000 + 5 * 60 * 1000);

            // 验证查询
            String driverName = className.toLowerCase();

            if (driverName.contains("sqlserver")  || driverName.contains("mysql")
                    || driverName.contains("h2")  || driverName.contains("moonbox"))  {
                hikariDataSource.setConnectionTestQuery("select  1");
            } else if (driverName.contains("oracle"))  {
                hikariDataSource.setConnectionTestQuery("select  1 from dual");
            }

            // MySQL 特定配置
            if (driverName.contains("mysql"))  {
                hikariDataSource.addDataSourceProperty("cachePrepStmts",  "true");
                hikariDataSource.addDataSourceProperty("prepStmtCacheSize",  "250");
                hikariDataSource.addDataSourceProperty("prepStmtCacheSqlLimit",  "2048");
                hikariDataSource.addDataSourceProperty("useServerPrepStmts",  "true");
            }

//            try {
//                hikariDataSource.init();
//            } catch (Exception e) {
//                log.error("Exception during pool initialization", e);
//                throw new RuntimeException(e.getMessage());
//            }

            dataSourceMap.put(key, hikariDataSource);

        } finally {
            lock.unlock();
        }

        return hikariDataSource;
    }

    private String getDataSourceKey(DatabaseResp database) {
        return JdbcDataSourceUtils.getKey(database.getName(), database.getUrl(),
                database.getUsername(), database.passwordDecrypt(), "", false);
    }
}
