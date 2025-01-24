package com.wjp.wcloudatlasbackend.manager;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.wjp.wcloudatlasbackend.model.dto.picture.PictureQueryRequest;

import com.wjp.wcloudatlasbackend.model.entity.domain.Picture;
import com.wjp.wcloudatlasbackend.model.vo.picture.PictureVO;
import com.wjp.wcloudatlasbackend.service.PictureService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.util.concurrent.TimeUnit;

@Component  // 标识该类为Spring组件，自动注入到Spring容器中
@RequiredArgsConstructor  // 自动生成构造方法，注入所有final字段
public class PictureCacheManager {

    // 日志记录器，用于记录日志
    private static final Logger log = LoggerFactory.getLogger(PictureCacheManager.class);

    // 缓存的 key 模板，包含页面图片的唯一标识符
    private static final String PAGE_PICTURE_CACHE_KEY = "smart-pichub:page:picture:%s";

    // 分布式锁的 key 模板，防止缓存击穿
    private static final String PAGE_PICTURE_CACHE_LOCK_KEY = "smart-pichub:page:picture:lock:%s";

    // 最小缓存过期时间（单位：分钟）
    private static final long MIN_EXPIRE = 5;

    // 最大缓存过期时间（单位：分钟）
    private static final long MAX_EXPIRE = 10;

    // 缓存过期时间的单位，这里是分钟
    private static final TimeUnit UNIT = TimeUnit.MINUTES;

    // 本地缓存，使用 Caffeine 实现，缓存页面数据
    private final Cache<String, String> localCache = Caffeine.newBuilder()
            .initialCapacity(1000)  // 初始容量为1000
            .maximumSize(10000)  // 最大缓存数为10000
            // 自定义缓存过期策略，过期时间随机，介于最小和最大过期时间之间
            .expireAfter(new Expiry<String, String>() {
                @Override
                public long expireAfterCreate(String key, String value, long currentTime) {
                    return UNIT.toNanos(RandomUtil.randomLong(MIN_EXPIRE, MAX_EXPIRE));  // 随机生成过期时间
//                    return UNIT.toNanos(10);  // 设置固定的过期时间为10秒
                }

                @Override
                public long expireAfterUpdate(String key, String value, long currentTime, long currentDuration) {
                    return currentDuration;  // 更新时返回原过期时间，不做更改
                }

                @Override
                public long expireAfterRead(String key, String value, long currentTime, long currentDuration) {
                    return currentDuration;  // 读取时返回原过期时间，不做更改
                }
            })
            .build();

    // Redis模板，用于操作Redis缓存
    private final StringRedisTemplate stringRedisTemplate;

    // 图片服务，用于查询数据库获取图片数据
    private final PictureService pictureService;

    // ObjectMapper，用于在缓存中存储和读取Java对象
    private final ObjectMapper objectMapper;

    // 获取页面图片的缓存方法，首先从缓存中查找数据，如果没有则从数据库查询并设置缓存
    public Page<PictureVO> getPagePictureCache(PictureQueryRequest condition, HttpServletRequest  request) {
        int current = condition.getCurrent();
        int pageSize = condition.getPageSize();

        // 生成缓存的唯一key，使用请求条件的MD5值作为缓存标识符
        String hashKey = DigestUtil.md5Hex(JSONUtil.toJsonStr(condition));
        String key = String.format(PAGE_PICTURE_CACHE_KEY, hashKey);

        // 尝试从缓存中获取数据
        Page<PictureVO> pictureVOPage = tryGetPagePictureCache(key, stringRedisTemplate.opsForValue());
        if (pictureVOPage != null) {
            return pictureVOPage;  // 如果缓存中有数据，则直接返回
        }

        // 设置分布式锁，避免缓存击穿（多个请求同时查询数据库）
        ValueOperations<String, String> ops = stringRedisTemplate.opsForValue();
        String curThreadIdentification = Thread.currentThread().getName();  // 获取当前线程的名称作为线程标识
        String lockKey = String.format(PAGE_PICTURE_CACHE_LOCK_KEY, hashKey);  // 根据缓存的key生成分布式锁的key

        int retryCount = 0;
        try {
            while (true) {
                // 尝试获取分布式锁，使用setIfAbsent命令保证锁是唯一的
                Boolean isLockSuccess = ops.setIfAbsent(lockKey, curThreadIdentification, 5, TimeUnit.SECONDS);
                if (isLockSuccess != null && isLockSuccess) {
                    break;  // 如果获取锁成功，退出重试循环
                }
                // 如果获取锁失败，则稍作等待再重试，避免高频次争抢锁
                Thread.sleep(100);
                // 再次尝试从缓存获取数据
                pictureVOPage = tryGetPagePictureCache(key, stringRedisTemplate.opsForValue());
                if (pictureVOPage != null) {
                    return pictureVOPage;  // 如果缓存中已经有数据，则返回
                }
                // 如果重试次数超过5次，则抛出异常
                if (++retryCount > 5) {
                    log.error("Failed to acquire lock after {} attempts for key: {}", retryCount, lockKey);
                    throw new RuntimeException("Failed to acquire lock after multiple attempts.");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();  // 线程中断时，恢复中断状态
            log.error("Thread was interrupted while trying to acquire lock for key: {}", lockKey, e);
            throw new RuntimeException("Thread interrupted while acquiring lock.", e);
        } catch (Exception e) {
            log.error("Error occurred while acquiring lock for key: {}", lockKey, e);
            throw new RuntimeException("Error occurred while acquiring lock.", e);
        }


        // 根据查询条件 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, pageSize), pictureService.getQueryWrapper(condition));
        // 封装为VO

        pictureVOPage = pictureService.getPictureVOPage(picturePage, request);
        // 获取分布式锁成功，查询数据库并构建缓存
        String cache = null;
        try {
            // 将结果转换为JSON字符串，以便存储到缓存
            cache = objectMapper.writeValueAsString(pictureVOPage);
        } catch (Exception e) {
            log.error("Failed to serialize pictureVOPage for cache", e);
            throw new RuntimeException("Failed to serialize pictureVOPage.", e);
        }

        // 将数据存入本地缓存（Caffeine）
        localCache.put(key, cache);

        // 将数据存入Redis缓存，并设置随机的过期时间
        long randomExpireTime = RandomUtil.randomLong(MIN_EXPIRE, MAX_EXPIRE);  // 随机生成过期时间
        stringRedisTemplate.opsForValue().set(key, cache, randomExpireTime, UNIT);

        // 释放分布式锁，确保当前线程释放它持有的锁
        String lockIdentification = ops.get(lockKey);  // 获取锁的持有者
        if (curThreadIdentification.equals(lockIdentification)) {
            stringRedisTemplate.delete(lockKey);  // 如果当前线程是锁的持有者，则删除锁
        }

        return pictureVOPage;  // 返回查询结果
    }

    // 尝试从本地缓存（Caffeine）和Redis缓存中获取数据
    private Page<PictureVO> tryGetPagePictureCache(String key, ValueOperations<String, String> ops) {
        // 从Caffeine本地缓存中获取数据
        String localCacheValue = localCache.getIfPresent(key);
        if (localCacheValue != null) {
            try {
                // 将 JSON 字符串 反序列化为 PictureVO 集合
                return objectMapper.readValue(localCacheValue, Page.class);  // 反序列化并返回
            } catch (Exception e) {
                log.error("Failed to deserialize local cache for key: {}", key, e);  // 如果反序列化失败，记录日志
            }
        }

        // 从Redis缓存中获取数据
        String redisCacheValue = ops.get(key);
        if (redisCacheValue != null) {
            try {
                return objectMapper.readValue(redisCacheValue, Page.class);  // 反序列化并返回
            } catch (Exception e) {
                log.error("Failed to deserialize Redis cache for key: {}", key, e);  // 如果反序列化失败，记录日志
            }
        }

        return null;  // 如果缓存中没有数据，则返回null
    }



}
