package com.opensource.redisaux.bloomfilter.autoconfigure;

import com.opensource.redisaux.bloomfilter.core.*;
import com.opensource.redisaux.bloomfilter.support.BloomFilterConsts;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Configuration
@ConditionalOnClass(RedisBloomFilter.class)
@AutoConfigureAfter(RedisAutoConfiguration.class)
public class RedisBloomFilterAutoConfiguration {

    @Resource(name= BloomFilterConsts.INNERTEMPLATE)
    private RedisTemplate redisTemplate;



    @Bean
    @ConditionalOnMissingBean(RedisBloomFilter.class)
    public RedisBloomFilter bloomFilterService() {

        Properties properties = System.getProperties();
        String property = properties.getProperty("sun.arch.data.model");
        Strategy strategy = RedisBloomFilterStrategies.getStrategy(property);
        if (strategy == null) {
            strategy = RedisBloomFilterStrategies.MURMUR128_MITZ_32.getStrategy();
        }
        Map<Class, RedisBloomFilterItem> map = new HashMap<>(FunnelEnum.values().length);
        for (FunnelEnum funnelEnum : FunnelEnum.values()) {
            map.put(funnelEnum.getCode(), RedisBloomFilterItem.create(funnelEnum.getFunnel(), strategy,redisBitArrayFactory()));
        }
        return new RedisBloomFilter(map);
    }

    @Bean(name = "setBitScript")
    public DefaultRedisScript setBitScript() {
        DefaultRedisScript script = new DefaultRedisScript();
        script.setScriptText("for i=1,tonumber(KEYS[2]) do redis.call('setbit',KEYS[1],ARGV[i*2-1],ARGV[i*2]) end");
        return script;
    }

    @Bean(name = "getBitScript")
    public DefaultRedisScript getBitScript() {
        DefaultRedisScript script = new DefaultRedisScript();
        script.setScriptText("local array={} for i=1,tonumber(KEYS[2])  do array[i]=redis.call('getbit',KEYS[1],ARGV[i]) end return array");
        script.setResultType(List.class);
        return script;
    }

    @Bean
    public RedisBitArrayFactoryBuilder.RedisBitArrayFactory redisBitArrayFactory(){
        RedisBitArrayFactoryBuilder builder=new RedisBitArrayFactoryBuilder();
        builder.setGetBitScript(getBitScript()).setSetBitScript(setBitScript()).setRedisTemplate(redisTemplate);
        return builder.build();
    }




}