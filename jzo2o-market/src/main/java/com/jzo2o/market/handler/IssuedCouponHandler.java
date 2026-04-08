package com.jzo2o.market.handler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jzo2o.market.model.domain.CouponIssue;
import com.jzo2o.market.model.dto.request.CouponIssueReqDTO;
import com.jzo2o.market.service.ICouponIssueService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Mr.M
 * @version 1.0
 * @description 自动发放优惠券处理器
 * 根据活动id从待发放记录表查找该活动的待发放记录，然后批量进行发放，一次拿出1000条进行发放。
 * @date 2024/9/23 18:43
 */
@Slf4j
public class IssuedCouponHandler implements Runnable {


    //优惠券发放service
    private ICouponIssueService couponIssueService;

    //分布式锁
    private RedissonClient redissonClient;

    //活动id
    private Long activityId;

    //构造方法
    public IssuedCouponHandler(Long activityId,ICouponIssueService couponIssueService,RedissonClient redissonClient) {
        this.activityId = activityId;
        this.couponIssueService = couponIssueService;
        this.redissonClient = redissonClient;
    }

    public void run() {
        //获取锁
        String lockKey = "activity:issued:lock:" + activityId;
        log.info("获取锁：{}", lockKey);
        RLock lock = redissonClient.getLock(lockKey);
        //尝试获取锁
        try {
            boolean tryLock = lock.tryLock(1,-1, TimeUnit.SECONDS);
            if(!tryLock){
                log.info("获取锁失败：{}", lockKey);
                return;
            }
            try {
                //开始发放优惠券
                log.info("开始发放优惠券：{}", activityId);
                //批量发放优惠券
                couponIssueService.autoIssue(activityId);
            } catch (Exception e) {
                log.error("发放优惠券失败：{}", e.getMessage());
            }finally {
                lock.unlock();
            }
    
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }


    }
}