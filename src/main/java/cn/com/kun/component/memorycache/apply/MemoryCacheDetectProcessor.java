package cn.com.kun.component.memorycache.apply;

import cn.com.kun.component.memorycache.MemoryCacheProperties;
import cn.com.kun.springframework.springredis.RedisTemplateHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static cn.com.kun.component.memorycache.MemoryCacheConstants.NOTICE_TIMEMILLIS_HASH_KEYNAME;

/**
 * 内存缓存检测处理器
 * 作用：检测是否有缓存器等待刷新，通过时间戳判断
 *
 * author:xuyaokun_kzx
 * date:2021/6/29
 * desc:
*/
@Component
public class MemoryCacheDetectProcessor {

    public final static Logger LOGGER = LoggerFactory.getLogger(MemoryCacheDetectProcessor.class);

    @Autowired
    @Qualifier("caffeineCacheManager")
    private CacheManager cacheManager;

    @Autowired
    private RedisTemplateHelper redisTemplateHelper;

    @Value("${kunsharedemo.sleepTime:60000}")
    private long sleepTime;

    private int heartBeatCount = 0;

    private Map<String, String> timeMillisMap = new HashMap<>();

    @Autowired
    private MemoryCacheProperties memoryCacheProperties;

    @PostConstruct
    public void init(){
        new Thread(()->{
            doCheck();
        }, "").start();
    }

    private void doCheck() {

        while (memoryCacheProperties.isEnabled()){
            try {
                check();
                Thread.sleep(sleepTime);
                logHeartBeat();
            } catch (Exception e) {
                LOGGER.error("doCheck方法出现异常", e);
            }
        }

    }

    private void logHeartBeat() {

        heartBeatCount++;
        if (heartBeatCount == 5){
            LOGGER.info("MemoryCacheDetectProcessor working...");
            heartBeatCount = 0;
        }
    }

    private void check() {
        /**
         * 获取整个hash结构
         */
        Map<Object,Object> redisMap = redisTemplateHelper.hmget(NOTICE_TIMEMILLIS_HASH_KEYNAME);
        Iterator<Map.Entry<Object, Object>> iterator = redisMap.entrySet().iterator();
        while (iterator.hasNext()){
            Map.Entry<Object, Object> entry = iterator.next();
            String configName = (String) entry.getKey();
            String lastUpdateTime = (String) entry.getValue();
            String oldUpdateTime = timeMillisMap.get(configName);
            if (oldUpdateTime == null){
                timeMillisMap.put(configName, lastUpdateTime);
            }else {
                if (!oldUpdateTime.equals(lastUpdateTime)){
                    //redis中的时间戳和timeMillisMap中的时间不等，说明发生变更
                    //清缓存
                    cacheManager.getCache(configName).clear();
                    timeMillisMap.put(configName, lastUpdateTime);
                    LOGGER.debug("本次清空缓存管理器{}", configName);
                } else {
                    //未发生变更
                    LOGGER.debug("缓存管理器{}未发生变更", configName);
                }
            }
        }
    }

    /**
     * 更新某个缓存管理器的时间戳
     * @param configName
     * @param lastUpdateTime
     */
    public void updateTimemillis(String configName, String lastUpdateTime){
        timeMillisMap.put(configName, lastUpdateTime);
    }
}