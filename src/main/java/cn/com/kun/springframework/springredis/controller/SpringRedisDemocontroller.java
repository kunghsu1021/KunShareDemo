package cn.com.kun.springframework.springredis.controller;

import cn.com.kun.common.utils.DateUtils;
import cn.com.kun.common.vo.ResultVo;
import cn.com.kun.component.memorycache.MemoryCacheNoticeMsg;
import cn.com.kun.springframework.springredis.RedisTemplateHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.support.atomic.RedisAtomicLong;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Created by xuyaokun On 2020/10/14 22:56
 * @desc: 
 */
@RequestMapping("/spring-redis")
@RestController
public class SpringRedisDemocontroller {

    public final static Logger LOGGER = LoggerFactory.getLogger(SpringRedisDemocontroller.class);

    private final String HASH_KEY = "kunghsu.hash";

    private final String HASH_KEY_PREFIX = "kunghsu.hash.prefix_";

    @Autowired
    RedisTemplate redisTemplate;

    @Autowired
    private RedisTemplateHelper redisTemplateHelper;

    /**
     * 测试生成唯一的递增流水号
     * @param request
     * @return
     */
    @RequestMapping(value = "/test", method = RequestMethod.GET)
    public String test(HttpServletRequest request){

        for (int i = 0; i < 10; i++) {
            new Thread(()->{
                while (true){
                    System.out.println(generateUniqueId());
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }, "" + i).start();
        }
        
        return "OK";
    }

    /**
     * 生成递增流水号（不要求连续）
     * @return
     */
    private String generateUniqueId(){
        //获取当前秒作为key
        String currentSecond = DateUtils.nowWithNoSymbol();
        RedisAtomicLong counter = new RedisAtomicLong(currentSecond, redisTemplate.getConnectionFactory());
        counter.expire(60, TimeUnit.SECONDS);//设置过期时间
        long number = counter.incrementAndGet();
        if (number > 999999){
            //限制位数（从自己系统的每秒下单数考虑）
            throw new RuntimeException("生成流水号失败！请稍后重试");
        }
        String id = currentSecond + fillString(number);
        return id;
    }

    /**
     * 填充字符串至6位
     */
    private String fillString(long number){
        String source = "" + number;
        while (source.length() < 6){
            source = "0" + source;
        }
        return source;
    }

    /**
     * 测试hash
     * @param request
     * @return
     */
    @RequestMapping(value = "/testHashSet", method = RequestMethod.GET)
    public String testHashSet(HttpServletRequest request){

        //放入一万个key
        for (int i = 0; i < 10000; i++) {
            redisTemplate.opsForHash().put(HASH_KEY, HASH_KEY_PREFIX +i, "" + System.currentTimeMillis());
        }

        return "OK";
    }

    /**
     * 测试hash
     * @param request
     * @return
     */
    @RequestMapping(value = "/testHashGet", method = RequestMethod.GET)
    public String testHashGet(HttpServletRequest request){

        String res = (String) redisTemplate.opsForHash().get(HASH_KEY, HASH_KEY_PREFIX + 10);
        System.out.println("testHashGet获取到结果：" + res);
        return "OK";
    }

    /**
     * 测试hash
     * @param request
     * @return
     */
    @RequestMapping(value = "/testHashGet2", method = RequestMethod.GET)
    public String testHashGet2(HttpServletRequest request){

        Map<String, String> map = redisTemplate.opsForHash().entries(HASH_KEY);
        System.out.println("testHashGet2获取到结果：" + map.size());

        //存取10000个key
        long start = System.currentTimeMillis();
        for (int i = 0; i < 500; i++) {
            String res = map.get(HASH_KEY_PREFIX + i);
            System.out.println(res);
        }
        System.out.println("testHashGet2总耗时：" + (System.currentTimeMillis() - start));

        return "OK";
    }

    /**
     * 测试hash
     * @param request
     * @return
     */
    @RequestMapping(value = "/testHashGet3", method = RequestMethod.GET)
    public String testHashGet3(HttpServletRequest request){

        //存取10000个key
        long start = System.currentTimeMillis();
        for (int i = 0; i < 500; i++) {
            String res = (String) redisTemplate.opsForHash().get(HASH_KEY, HASH_KEY_PREFIX + i);
            System.out.println(res);
        }
        System.out.println("testHashGet3总耗时：" + (System.currentTimeMillis() - start));

        return "OK";
    }


    @RequestMapping(value = "/testString", method = RequestMethod.GET)
    public ResultVo testString(HttpServletRequest request){

//        User user = new User();
//        user.setUsername("kunghsu");
//        redisTemplateHelper.set("spring-redis-demo-testString", user, 6000);
//
//        Object res = redisTemplateHelper.get("spring-redis-demo-testString");

        //下面的代码在低版本jedis包中会有问题，不能存太大的值
        // (这个问题已经在高版本修复)
        long time = 2147483647999999L;
        LOGGER.info("long值：{}", time);
        redisTemplate.opsForValue().set("spring-redis-demo-testString", "123456", time, TimeUnit.MILLISECONDS);
//        redisTemplate.opsForValue().set("spring-redis-demo-testString", "123456", time/1000, TimeUnit.SECONDS);
//        redisTemplate.opsForValue().set("spring-redis-demo-testString", "123456", (time/1000)/60, TimeUnit.MINUTES);

        MemoryCacheNoticeMsg noticeMsg = new MemoryCacheNoticeMsg();
        redisTemplateHelper.set("noticeMsg", "kunghsu");
        String res = (String) redisTemplateHelper.get("noticeMsg");
        LOGGER.info("===========res：{}", res);
        return ResultVo.valueOfSuccess(res);
    }

    @RequestMapping(value = "/testSetAdd", method = RequestMethod.GET)
    public ResultVo testSetAdd(HttpServletRequest request){

        for (int i = 0; i < (10000*50); i++) {
            redisTemplateHelper.sSet("spring-redis-demo-testSetAdd", UUID.randomUUID().toString());
        }
//        Object res = redisTemplateHelper.get("spring-redis-demo-testString");

        return ResultVo.valueOfSuccess("");
    }

    @RequestMapping(value = "/testSetGet", method = RequestMethod.GET)
    public ResultVo testSetGet(HttpServletRequest request){

        long start = System.currentTimeMillis();
        /**
         * 从一个100万大小的set中判断某个元素是否存在，耗时多少？
         */
        boolean res = redisTemplateHelper.sHasKey("spring-redis-demo-testSetAdd", UUID.randomUUID().toString());
        LOGGER.info("耗时：{}ms", System.currentTimeMillis() - start);

        return ResultVo.valueOfSuccess("");
    }

}
