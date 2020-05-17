package com.opensource.redisaux.bloomfilter.core.bitarray;

import com.opensource.redisaux.common.BloomFilterConstants;
import com.opensource.redisaux.common.RedisAuxException;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author: lele
 * @date: 2019/12/20 上午11:39
 */
@SuppressWarnings("unchecked")
public class RedisBitArray implements BitArray {


    private RedisTemplate redisTemplate;

    private long bitSize;
    /**
     * 读可以共享，写不可以
     */
    private ReadWriteLock readWriteLock;

    private LinkedList<String> keyList;

    private String key;

    private DefaultRedisScript setBitScript;

    private DefaultRedisScript getBitScript;

    private DefaultRedisScript resetBitScript;



    public RedisBitArray(RedisTemplate redisTemplate, String key, DefaultRedisScript setBitScript, DefaultRedisScript getBitScript, DefaultRedisScript resetBitScript,long bitSize) {
        if (bitSize > BloomFilterConstants.MAX_REDIS_BIT_SIZE) {
            throw new RedisAuxException("Invalid redis bit size, must small than 2 to the 32");
        }
        this.bitSize = bitSize;
        this.redisTemplate = redisTemplate;
        this.key = key;
        this.setBitScript = setBitScript;
        this.getBitScript = getBitScript;
        this.keyList = new LinkedList();
        this.keyList.add(key);
        this.resetBitScript = resetBitScript;
        readWriteLock = new ReentrantReadWriteLock();
    }




    @Override
    public boolean set(long[] index) {
        readWriteLock.writeLock().lock();
        setBitScriptExecute(index);
        readWriteLock.writeLock().unlock();
        return Boolean.TRUE;
    }


    /**
     * 通过lua脚本设置
     *
     * @param index
     * @return
     */
    @Override
    public boolean setBatch(List index) {
        readWriteLock.writeLock().lock();
        long[] res = getArrayFromList(index);
        setBitScriptExecute(res);
        readWriteLock.writeLock().unlock();
        return Boolean.TRUE;
    }

    @Override
    public boolean get(long[] index) {
        readWriteLock.readLock().lock();
        List<Long> res = getBitScriptExecute(index, index.length);
        boolean exists = res.get(0).equals(BloomFilterConstants.TRUE);
        readWriteLock.readLock().unlock();
        return exists;
    }

    /**
     * @param index List<long[]>
     * @return
     */
    @Override
    public List<Boolean> getBatch(List index) {
        //index.size*keyList.size
        readWriteLock.readLock().lock();
        long[] array = getArrayFromList(index);
        List<Long> list = getBitScriptExecute(array, ((long[]) index.get(0)).length);
        List<Boolean> res = new ArrayList(index.size());
        for (Long temp : list) {
            res.add(Boolean.valueOf(temp.equals(BloomFilterConstants.TRUE)));
        }
        readWriteLock.readLock().unlock();
        return res;
    }


    @Override
    public long bitSize() {
        return this.bitSize;
    }


    @Override
    public void reset() {
        readWriteLock.writeLock().lock();
        keyList.clear();
        keyList.add(key);
        readWriteLock.writeLock().unlock();
        redisTemplate.execute(resetBitScript, keyList, bitSize);
    }






    /**
     * @param index
     * @return
     */
    private void setBitScriptExecute(long[] index) {
        Integer length = index.length;
        Object[] value = new Long[length];
        for (int i = 0; i < length; i++) {
            value[i] = Long.valueOf(index[i]);
        }
        redisTemplate.execute(setBitScript, keyList, value);
    }

    /**
     * @param index
     * @return
     */
    private List<Long> getBitScriptExecute(long[] index, int size) {
        Object[] value = new Long[index.length + 1];
        value[0] = Long.valueOf(size);
        for (int i = 1; i < value.length; i++) {
            value[i] = Long.valueOf(index[i - 1]);
        }
        List res = (List) redisTemplate.execute(getBitScript, keyList, value);
        return res;
    }

    /**
     * 把list转成long[]
     *
     * @param index
     * @return
     */
    private long[] getArrayFromList(List index) {
        int length = 0;
        for (Object o : index) {
            long[] temp = (long[]) o;
            length += temp.length;
        }
        long[] res = new long[length];
        for (int i = 0; i < index.size(); i++) {
            long[] temp = (long[]) index.get(i);
            System.arraycopy(temp, 0, res, temp.length * i, temp.length);
        }
        return res;
    }

    public List<String> getKeyList() {
        return keyList;
    }

    @Override
    public void clear() {
        keyList.clear();
        keyList = null;
        readWriteLock = null;
    }

    @Override
    public String getKey() {
        return this.key;
    }


}
