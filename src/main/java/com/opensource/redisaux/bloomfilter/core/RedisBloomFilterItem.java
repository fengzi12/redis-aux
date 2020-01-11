package com.opensource.redisaux.bloomfilter.core;

import com.google.common.base.Preconditions;
import com.google.common.hash.Funnel;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.opensource.redisaux.CommonUtil.optimalNumOfBits;
import static com.opensource.redisaux.CommonUtil.optimalNumOfHashFunctions;

/**
 * @author: lele
 * @date: 2019/12/20 上午11:35
 */
public class RedisBloomFilterItem<T> {


    private final Map<String, BitArray<T>> map;

    private final Map<String,List<String>> keyMap;

    private final Map<String, Integer> numHashFunctionsMap;

    private final Funnel<? super T> funnel;

    private final Strategy strategy;

   private RedisBitArrayOperatorBuilder.RedisBitArrayOperator redisBitArrayOperator;



    public static <T> RedisBloomFilterItem<T> create(Funnel<? super T> funnel, Strategy strategy
                                       , RedisBitArrayOperatorBuilder.RedisBitArrayOperator redisBitArrayOperator) {
        strategy = Optional.ofNullable(strategy).orElse(RedisBloomFilterStrategies.MURMUR128_MITZ_64.getStrategy());
        return new RedisBloomFilterItem<>(funnel, strategy, redisBitArrayOperator);
    }


    private RedisBloomFilterItem(
            Funnel<? super T> funnel,
            Strategy strategy,
            RedisBitArrayOperatorBuilder.RedisBitArrayOperator redisBitArrayOperator
    ) {
        this.strategy = strategy;
        this.funnel = funnel;
        this.map = new ConcurrentHashMap<>();
        this.numHashFunctionsMap = new ConcurrentHashMap<>();
        this.redisBitArrayOperator = redisBitArrayOperator;
        this.keyMap=new ConcurrentHashMap<>();
    }

    public boolean mightContain(String key, T member) {
        Integer numHashFunctions = numHashFunctionsMap.get(key);
        BitArray<T> bits = map.get(key);
        if (bits == null) {
            return false;
        }
        return strategy.mightContain(member, funnel, numHashFunctions, bits);
    }

    public List<Boolean> mightContains(String key, List<T> members) {
        Integer numHashFunctions = numHashFunctionsMap.get(key);
        BitArray<T> bits = map.get(key);

        if (bits == null) {
            List<Boolean> list = new ArrayList<>(members.size());
            members.forEach(e -> list.add(Boolean.FALSE));
            return list;
        }
        return strategy.mightContains(funnel, numHashFunctions, bits, members);
    }

    public void reset(String key){
        BitArray<T> tBitArray = map.get(key);
        if(Objects.nonNull(tBitArray)){
            redisBitArrayOperator.reset(keyMap.get(key),tBitArray.bitSize());
        }
    }

    /**
     * 这里判断不为空才删除的原因是，有可能里面的键不在里面
     * @param iterable
     */
    public void removeAll(Collection<String> iterable) {
        boolean delete = false;
        for (String s : iterable) {
            BitArray<T> tBitArray = map.get(s);
            if (tBitArray != null) {
                tBitArray = null;
                map.remove(s);
                Integer integer = numHashFunctionsMap.get(s);
                integer = null;
                numHashFunctionsMap.remove(s);
                keyMap.remove(s);
                delete = true;
            }

        }
        if (delete) {
           redisBitArrayOperator.delete(iterable);
        }
    }

    public void remove(String key) {
        BitArray<T> tBitArray = map.get(key);
        if (tBitArray != null) {
            tBitArray = null;
            map.remove(key);
            Integer integer = numHashFunctionsMap.get(key);
            integer = null;
            numHashFunctionsMap.remove(key);
            keyMap.remove(key);
            redisBitArrayOperator.delete(key);
        }
    }

    public void put(String key, T member, long expectedInsertions, double fpp) {
        Preconditions.checkArgument(
                expectedInsertions >= 0, "Expected insertions (%s) must be >= 0", expectedInsertions);
        Preconditions.checkArgument(fpp > 0.0, "False positive probability (%s) must be > 0.0", fpp);
        Preconditions.checkArgument(fpp < 1.0, "False positive probability (%s) must be < 1.0", fpp);
        //获取keyname
        //获取容量
        long numBits = optimalNumOfBits(expectedInsertions, fpp);
        //获取hash函数数量
        int numHashFunctions = optimalNumOfHashFunctions(expectedInsertions, numBits);
        numHashFunctionsMap.putIfAbsent(key, numHashFunctions);
        RedisBitArray bits = redisBitArrayOperator.createBitArray(key);
        bits.setBitSize(numBits);
        map.putIfAbsent(key, bits);
        keyMap.putIfAbsent(key,Collections.singletonList(key));
        strategy.put(member, funnel, numHashFunctions, bits);
    }

    public void putAll(String key, long expectedInsertions, double fpp, List<T> keys) {
        Preconditions.checkArgument(
                expectedInsertions >= 0, "Expected insertions (%s) must be >= 0", expectedInsertions);
        Preconditions.checkArgument(fpp > 0.0, "False positive probability (%s) must be > 0.0", fpp);
        Preconditions.checkArgument(fpp < 1.0, "False positive probability (%s) must be < 1.0", fpp);
        //获取keyname
        //获取容量
        long numBits = optimalNumOfBits(expectedInsertions, fpp);
        //获取hash函数数量
        int numHashFunctions = optimalNumOfHashFunctions(expectedInsertions, numBits);
        numHashFunctionsMap.putIfAbsent(key, numHashFunctions);
        RedisBitArray bits = redisBitArrayOperator.createBitArray(key);
        bits.setBitSize(numBits);
        map.putIfAbsent(key, bits);
        keyMap.putIfAbsent(key,Collections.singletonList(key));
        strategy.putAll(funnel, numHashFunctions, bits, keys);
    }


}