/*
 * Copyright 2014 FraudMetrix.cn All right reserved. This software is the
 * confidential and proprietary information of FraudMetrix.cn ("Confidential
 * Information"). You shall not disclose such Confidential Information and shall
 * use it only in accordance with the terms of the license agreement you entered
 * into with FraudMetrix.cn.
 */
package io.redis.jedis;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.commons.pool2.impl.AbandonedConfig;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.util.Assert;

import redis.clients.jedis.JedisPoolConfig;

/**
 * 基于Spring工厂Bean({@link FactoryBean})实现的自定义分片Jedis连接池({@link CustomShardedJedisPool})工厂。
 * <p>
 * 属性配置文件各字段含义说明：
 * 
 * <pre>
 * redis.server.list：Redis服务器列表信息，配置格式如下：
 *     redisServers："redisServer[, redisServer ...]"
 *     redisServer："host:port:name[:weight]"
 * redis.timeout.millis：链接套接字的连接超时时间和读取超时时间
 * redis.max.total.num：在给定的时间可以由连接池分配的对象的数量上限
 * redis.max.idle.num：连接池中空闲实例的数量上限
 * redis.min.idle.num：连接池维护的空闲对象的最小数量
 * redis.min.evictable.idle.time.minutes：池对象的最小可驱逐的空闲时间（当池对象的空闲时间超过该属性值时，就被纳入到驱逐检测对象的范围里）
 * redis.max.evictable.idle.time.minutes：池对象的最大可驱逐的空闲时间（当池对象的空闲时间超过该属性值时，会被立刻驱逐并销毁）
 * redis.remove.abandoned.timeout.minutes：移除被废弃的池对象的超时时间
 * </pre>
 * 
 * 【使用示例】<br>
 * Properties：
 * 
 * <pre>
 * redis.server.list=192.168.6.189:6379:Shard-01,192.168.6.189:6380:Shard-02,192.168.6.189:6381:Shard-03
 * redis.timeout.millis=100
 * redis.max.total.num=32768
 * redis.max.idle.num=32768
 * redis.min.idle.num=20
 * redis.min.evictable.idle.time.minutes=30
 * redis.max.evictable.idle.time.minutes=1440
 * redis.remove.abandoned.timeout.minutes=5
 * </pre>
 * 
 * XML：
 * 
 * <pre>
 * <bean id="shardedJedisPool" class="cn.fraudmetrix.forseti.biz.service.redis.impl.CustomShardedJedisPoolFactoryBean">
 *         <property name="redisServers" value="${redis.server.list}" />
 *         <property name="timeoutMillis" value="${redis.timeout.millis}" />
 *         <property name="maxTotalNum" value="${redis.max.total.num}" />
 *         <property name="maxIdleNum" value="${redis.max.idle.num}" />
 *         <property name="minIdleNum" value="${redis.min.idle.num}" />
 *         <property name="minEvictableIdleTimeMinutes" value="${redis.min.evictable.idle.time.minutes}" />
 *         <property name="maxEvictableIdleTimeMinutes" value="${redis.max.evictable.idle.time.minutes}" />
 *         <property name="removeAbandonedTimeoutMinutes" value="${redis.remove.abandoned.timeout.minutes}" />
 * </bean>
 * </pre>
 * 
 * @author huagang.li 2014年12月13日 上午9:25:52
 */
public class CustomShardedJedisPoolFactoryBean implements FactoryBean<CustomShardedJedisPool> {

	private static final String DEFAULT_REDIS_CONFIG = "redis.default.properties";

    /** 对象池的配置信息 */
    private final GenericObjectPoolConfig poolConfig      = new JedisPoolConfig();

    /** "池对象废弃策略"配置信息 */
    private final AbandonedConfig         abandonedConfig = new AbandonedConfig();

    /** Redis集群节点列表信息 */
    private String                        redisServers;
    // private String shardServers;

    /** 链接套接字的连接超时时间、读取超时时间 */
    private int                           timeoutMillis;

    @Override
    public CustomShardedJedisPool getObject() throws Exception {
        this.setImmutablePoolConfig();
        CustomShardedJedisPool shardedJedisPool = new CustomShardedJedisPool(
                                                                             poolConfig,
                                                                             RedisConfigUtils.parserRedisServerList(redisServers,
                                                                                                                    timeoutMillis));
        shardedJedisPool.setAbandonedConfig(abandonedConfig);
        return shardedJedisPool;
    }

    /**
	 * 设置一些不可改变的配置属性。<br>
	 * <font color="red">注意：</font>该方法里设置的配置信息不能随意更改！
	 * 
	 * @throws IOException
	 */
	private void setImmutablePoolConfig() throws IOException {

		Properties defaultRedisConfigs = new Properties();
		defaultRedisConfigs.load(this.getClass().getClassLoader()
				.getResourceAsStream(DEFAULT_REDIS_CONFIG));

        // 设置"在连接池耗尽时，借用池对象的方法(ObjectPool#borrowObject())调用"是非阻塞的
		// poolConfig.setBlockWhenExhausted(false);
		boolean blockWhenExhausted = parseBooleanValue(defaultRedisConfigs,
				"redis.block.when.exhausted");
		poolConfig.setBlockWhenExhausted(blockWhenExhausted);

        // 关闭"在借用或返回池对象时，检测其有效性"（因为它会对集群中的所有节点发送PING命令，对性能影响较大）
		// poolConfig.setTestOnBorrow(false);
		// poolConfig.setTestOnReturn(false);
		boolean testOnBorrow = parseBooleanValue(defaultRedisConfigs,
				"redis.test.on.borrow");
		poolConfig.setTestOnBorrow(testOnBorrow);
		boolean testOnReturn = parseBooleanValue(defaultRedisConfigs,
				"redis.test.on.return");
		poolConfig.setTestOnReturn(testOnReturn);

        /*
         * "EvictionTimer守护线程"的相关配置，用它来维护"空闲对象"列表和保证集群节点的有效性
         */
		// poolConfig.setTestWhileIdle(true);
		boolean testWhileIdle = parseBooleanValue(defaultRedisConfigs,
				"redis.test.while.idle");
		poolConfig.setTestWhileIdle(testWhileIdle);
		// 每隔1秒钟执行一次，保证异常节点能被及时摘除（具体隔多久调度一次，尽量根据业务需求来定）
		// poolConfig.setTimeBetweenEvictionRunsMillis(TimeUnit.SECONDS.toMillis(1L));
		long timeBetweenEvictionRunsSeconds = parseLongValue(
				defaultRedisConfigs, "redis.time.between.eviction.runs.seconds");
		poolConfig.setTimeBetweenEvictionRunsMillis(TimeUnit.SECONDS
				.toMillis(timeBetweenEvictionRunsSeconds));
		// 每次检测10个空闲池对象
		// poolConfig.setNumTestsPerEvictionRun(10);
		int numTestsPerEvictionRun = parseIntValue(defaultRedisConfigs,
				"redis.num.tests.per.eviction.run");
		poolConfig.setNumTestsPerEvictionRun(numTestsPerEvictionRun);

        // 在借用池对象时，关闭"池对象废弃策略"执行，尽量让"EvictionTimer守护线程"来维护
		// abandonedConfig.setRemoveAbandonedOnBorrow(false);
		// abandonedConfig.setLogAbandoned(true);
		boolean removeAbandonedOnBorrow = parseBooleanValue(
				defaultRedisConfigs, "redis.remove.abandoned.on.borrow");
		abandonedConfig.setRemoveAbandonedOnBorrow(removeAbandonedOnBorrow);
		boolean logAbandoned = parseBooleanValue(defaultRedisConfigs,
				"redis.log.abandoned");
		abandonedConfig.setLogAbandoned(logAbandoned);
    }

	/**
	 * 解析字符串为布尔值。
	 */
	private static boolean parseBooleanValue(Properties props, String key) {
		return Boolean.parseBoolean(props.getProperty(key));
	}

	/**
	 * 解析字符串为整型值。
	 */
	private static int parseIntValue(Properties props, String key) {
		return Integer.parseInt(props.getProperty(key));
	}

	/**
	 * 解析字符串为长整型值。
	 */
	private static long parseLongValue(Properties props, String key) {
		return Long.parseLong(props.getProperty(key));
	}

    @Override
    public Class<?> getObjectType() {
        return CustomShardedJedisPool.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    /**
     * 设置Redis集群的节点列表信息。
     * <p>
     *
     * <pre>
     * Redis节点列表的配置格式：
     *     redisServers："redisServer[, redisServer ...]"
     *     redisServer："host:port:name[:weight]"
     * 
     * 示例：
     *     "192.168.6.189:6379:Shard-01, 192.168.6.189:6380:Shard-02, 192.168.6.189:6381:Shard-03"
     *     "192.168.6.189:6379:Shard-01:1, 192.168.6.189:6380:Shard-02:1, 192.168.6.189:6381:Shard-03:1"
     * </pre>
     *
     * @param redisServers Redis集群节点列表信息
     */
	public final void setRedisServers(String redisServers) {
        Assert.notNull(redisServers, "'redisServers' property must not be null");

        this.redisServers = redisServers;
    }

    /**
     * 设置链接套接字的连接超时时间和读取超时时间(毫秒数)。
     * 
     * @param timeoutMillis 套接字的连接超时时间和读取超时时间(ms)
     */
	public final void setTimeoutMillis(int timeoutMillis) {
        Assert.isTrue(timeoutMillis > 0, "'timeoutMillis' property must be greater than 0 : " + timeoutMillis);

        this.timeoutMillis = timeoutMillis;
    }

    /**
     * 设置在给定的时间可以由连接池分配的对象的数量上限。
     * <p>
     * 默认值是 8个。
     * 
     * @param maxTotalNum 由连接池同时管理的对象实例的总数上限
     * @see GenericObjectPoolConfig#setMaxTotal(int)
     * @see org.apache.commons.pool2.impl.GenericObjectPool#setMaxTotal(int)
     */
	public final void setMaxTotalNum(int maxTotalNum) {
        Assert.isTrue(maxTotalNum > 0, "'maxTotalNum' property must be greater than 0 : " + maxTotalNum);

        poolConfig.setMaxTotal(maxTotalNum);
    }

    /**
     * 设置连接池中空闲实例的数量上限。
     * <p>
     * 如果在负载较重的系统中{@code maxIdle}被设置得太低，这可能会看到对象被销毁时，立即又有新对象被创建。<br>
     * 这样就会引起"活跃线程返回对象的速度比请求对象还快"，造成空闲对象的数量超过最大空闲对象数量({@code maxIdle})。<br>
     * 负载较重系统的{@code maxIdle}的最佳值会有所不同，但默认值是一个很好的起点。
     * <p>
     * 默认值是 8个，该属性值必须大于{@link #setMinIdleNum(int)}设置的"空闲对象的最小数量"。
     * 
     * @param maxIdleNum 连接池中空闲实例的数量上限
     * @see GenericObjectPoolConfig#setMaxIdle(int)
     * @see org.apache.commons.pool2.impl.GenericObjectPool#setMaxIdle(int)
     */
	public final void setMaxIdleNum(int maxIdleNum) {
        Assert.isTrue(maxIdleNum > 0, "'maxIdleNum' property must be greater than 0 : " + maxIdleNum);

        poolConfig.setMaxIdle(maxIdleNum);
    }

    /**
     * 设置连接池维护的空闲对象的最小数量。
     * <p>
     * 默认值是 0个，即不维护空闲对象。
     * 
     * @param minIdleNum
     * @see GenericObjectPoolConfig#setMinIdle(int)
     * @see org.apache.commons.pool2.impl.GenericObjectPool#setMinIdle(int)
     */
	public final void setMinIdleNum(int minIdleNum) {
        Assert.isTrue(minIdleNum > 0, "'minIdleNum' property must be greater than 0 : " + minIdleNum);

        poolConfig.setMinIdle(minIdleNum);
    }

	/**
	 * 对象池管理池对象的行为表示类。
	 */
	public static enum PoolBehaviour {
		/** 堆栈 - 后进先出(last in, first out) */
		LIFO,
		/** 队列 - 先进先出(first in, first out) */
		FIFO, ;
	}

	/**
	 * 设置对象池管理池对象的行为。
	 * <p>
	 * 默认管理池对象的行为是使用"后进先出({@link PoolBehaviour#LIFO})"方式，还可以使用"先进先出(
	 * {@link PoolBehaviour#FIFO})"方式。
	 * 
	 * @param poolBehaviour
	 */
	public final void setPoolBehaviour(PoolBehaviour poolBehaviour) {
		switch (poolBehaviour) {
		case LIFO:
			poolConfig.setLifo(true);
			break;
		case FIFO:
			poolConfig.setLifo(false);

		default:
			break;
		}
	}

    /**
     * 设置池对象的最小可驱逐的空闲时间(分钟数)。
     * <p>
     * 默认值是 30分钟。
     * <p>
     * 当池对象的空闲时间超过该属性值时，就被纳入到驱逐检测对象的范围里。
     * 
     * @param minEvictableIdleTimeMinutes
     * @see GenericObjectPoolConfig#setSoftMinEvictableIdleTimeMillis(long)
     * @see org.apache.commons.pool2.impl.GenericObjectPool#setSoftMinEvictableIdleTimeMillis(long)
     */
	public final void setMinEvictableIdleTimeMinutes(
			long minEvictableIdleTimeMinutes) {
        Assert.isTrue(minEvictableIdleTimeMinutes > 0,
                      "'minEvictableIdleTimeMinutes' property must be greater than 0 : " + minEvictableIdleTimeMinutes);

        poolConfig.setSoftMinEvictableIdleTimeMillis(TimeUnit.MINUTES.toMillis(minEvictableIdleTimeMinutes));
    }

    /**
     * 设置池对象的最大可驱逐的空闲时间(分钟数)。
     * <p>
     * 默认值是 30分钟，该属性值必须大于{@link #setMinEvictableIdleTimeMinutes(long)}设置的"最小可驱逐的空闲时间"。<br>
     * 最好设置为 1天 (1440分钟 = {@code TimeUnit.DAYS.toMinutes(1L)})，因为这样可以保证对象池中的空闲对象的最小数量。
     * <p>
     * 当池对象的空闲时间超过该属性值时，会被立刻驱逐并销毁。
     * 
     * @param maxEvictableIdleTimeMinutes
     * @see GenericObjectPoolConfig#setMinEvictableIdleTimeMillis(long)
     * @see org.apache.commons.pool2.impl.GenericObjectPool#setMinEvictableIdleTimeMillis(long)
     */
	public final void setMaxEvictableIdleTimeMinutes(
			long maxEvictableIdleTimeMinutes) {
        Assert.isTrue(maxEvictableIdleTimeMinutes > 0,
                      "'maxEvictableIdleTimeMinutes' property must be greater than 0 : " + maxEvictableIdleTimeMinutes);

        poolConfig.setMinEvictableIdleTimeMillis(TimeUnit.MINUTES.toMillis(maxEvictableIdleTimeMinutes));
    }

    /**
     * 设置移除被废弃的池对象的超时时间(分钟数)。
     * <p>
     * 默认值是 5分钟。
     * 
     * @param removeAbandonedTimeoutMinutes
     */
	public final void setRemoveAbandonedTimeoutMinutes(
			int removeAbandonedTimeoutMinutes) {
        Assert.isTrue(removeAbandonedTimeoutMinutes > 0,
                      "'removeAbandonedTimeoutMinutes' property must be greater than 0 : "
                              + removeAbandonedTimeoutMinutes);

        abandonedConfig.setRemoveAbandonedOnMaintenance(true);
        abandonedConfig.setRemoveAbandonedTimeout((int) TimeUnit.MINUTES.toSeconds(removeAbandonedTimeoutMinutes));
    }

}
