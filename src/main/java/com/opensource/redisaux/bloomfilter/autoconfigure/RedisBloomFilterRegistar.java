package com.opensource.redisaux.bloomfilter.autoconfigure;

import com.opensource.redisaux.CommonUtil;
import com.opensource.redisaux.EnableRedisAux;
import com.opensource.redisaux.bloomfilter.annonations.BloomFilterPrefix;
import com.opensource.redisaux.bloomfilter.annonations.BloomFilterProperty;
import com.opensource.redisaux.bloomfilter.support.BloomFilterConsts;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ReflectionUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class RedisBloomFilterRegistar implements ImportBeanDefinitionRegistrar {
    public static Map<String, Map<String, BloomFilterProperty>> bloomFilterFieldMap;
    public static boolean transaction;

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        Map<String, Object> attributes = importingClassMetadata
                .getAnnotationAttributes(EnableRedisAux.class.getCanonicalName());
        transaction = (boolean) attributes.get("transaction");
        String[] scanPaths = (String[]) attributes.get(BloomFilterConsts.SCAPATH);
        if (!scanPaths[0].trim().equals("")) {
            bloomFilterFieldMap = new HashMap<>();
            ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(
                    false);
            scanner.addIncludeFilter(new AnnotationTypeFilter(BloomFilterPrefix.class));
            for (String basePath : scanPaths) {
                scanner.findCandidateComponents(basePath).forEach(e -> {
                    Class<?> clazz = null;
                    try {
                        clazz = Class.forName(e.getBeanClassName());
                    } catch (ClassNotFoundException e1) {
                        e1.printStackTrace();
                    }
                    if (clazz.isAnnotationPresent(BloomFilterPrefix.class)) {
                        Map<String, BloomFilterProperty> map = new HashMap();
                        String prefix = clazz.getAnnotation(BloomFilterPrefix.class).prefix();
                        ReflectionUtils.doWithFields(clazz, field -> {
                            field.setAccessible(Boolean.TRUE);
                            if (field.isAnnotationPresent(BloomFilterProperty.class)) {
                                String key = field.getName();
                                String keyName = CommonUtil.getKeyName(prefix, key);
                                map.put(keyName, field.getAnnotation(BloomFilterProperty.class));
                            }
                        });
                        bloomFilterFieldMap.put(prefix, map);
                    }
                });
            }
            bloomFilterFieldMap = Collections.unmodifiableMap(bloomFilterFieldMap);
        } else {
            System.err.println("=============redisbloomfilter未设置扫描路径，不支持lambda调用=============");
        }

        //指定扫描自己写的符合默认扫描注解的组件
        ClassPathBeanDefinitionScanner scanConfigure =
                new ClassPathBeanDefinitionScanner(registry, true);
        scanConfigure.scan(BloomFilterConsts.PATH);
    }

}