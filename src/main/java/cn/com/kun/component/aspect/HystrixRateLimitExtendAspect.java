package cn.com.kun.component.aspect;

import cn.com.kun.common.annotation.HystrixRateLimitExtend;
import cn.com.kun.component.spel.SpELHelper;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import cn.com.kun.component.hystrixextend.HystrixRateLimitValueHolder;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

/**
 * 自定义Hystrix切面
 *
 * author:xuyaokun_kzx
 * date:2021/7/2
 * desc:
*/
@Component
@Aspect
//@Order(-1)
//@Order(Ordered.LOWEST_PRECEDENCE)
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
//@Order(Ordered.HIGHEST_PRECEDENCE) //任何切面都不能定义成最高优先级
public class HystrixRateLimitExtendAspect {

    public final static Logger LOGGER = LoggerFactory.getLogger(HystrixRateLimitExtendAspect.class);

    @Autowired
    private SpELHelper spELHelper;

    @Autowired
    private HystrixRateLimitValueHolder hystrixRateLimitValueHolder;

    @Pointcut("@annotation(cn.com.kun.common.annotation.HystrixRateLimitExtend)")
    public void pointCut() {

    }

    @Before(value = "pointCut()")
    public void before(JoinPoint joinPoint) throws NoSuchFieldException, IllegalAccessException {

        // 通过joinPoint获取被注解方法
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        Method method = methodSignature.getMethod();
//        Class<?> targetCls = joinPoint.getTarget().getClass();
//        Method method = targetCls.getDeclaredMethod(methodSignature.getName(), methodSignature.getParameterTypes());

        HystrixRateLimitExtend hystrixRateLimitExtend = method.getAnnotation(HystrixRateLimitExtend.class);

        //业务场景名
        String bizSceneName = hystrixRateLimitExtend.bizSceneName();
        //SpEL表达式
        String spELExp = hystrixRateLimitExtend.key();
        Object keyObj = spELHelper.generateKeyBySpEL(spELExp, joinPoint);
        LOGGER.info("解析SpEL表达式得到的源对象：{}", keyObj);
        String itemName = keyObj.toString();

        //替换限流值
        String rateLimitValue = hystrixRateLimitValueHolder.getRateLimitValue(bizSceneName, itemName);

        if (StringUtils.isNotEmpty(rateLimitValue)){
            //假如存在指定的限流值，则进行替换
            // 获取方法上的HystrixCommand注解对象
            HystrixCommand hystrixCommand = method.getAnnotation(HystrixCommand.class);
            HystrixProperty[] hystrixProperties = hystrixCommand.commandProperties();
            for (HystrixProperty hystrixProperty : hystrixProperties) {
                //假如属性名等于超时时间，修改超时时间
                if ("execution.isolation.semaphore.maxConcurrentRequests".equals(hystrixProperty.name())) {
                /*
                    这里获取到的hystrixProperty是一个代理对象
                    根据代理对象获取到代理处理程序，代理处理程序里有个属性叫memberValues
                    通过反射拿到memberValues
                 */
                    InvocationHandler h = Proxy.getInvocationHandler(hystrixProperty);
                    Field hField = h.getClass().getDeclaredField("memberValues");
                    hField.setAccessible(true);
                    Map memberValues = (Map) hField.get(h);
                    //替换值
                    //这里根据场景！动态修改HystrixCommand的值
                    memberValues.put("value", rateLimitValue);
                }

            }
        }
    }


}