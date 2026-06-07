package com.jzo2o.market.handler;

import com.jzo2o.common.utils.DateUtils;
import com.jzo2o.market.enums.CouponStatusEnum;
import com.jzo2o.market.model.domain.Coupon;
import com.jzo2o.market.service.IActivityService;
import com.jzo2o.market.service.ICouponService;
import com.jzo2o.redis.annotations.Lock;
import com.jzo2o.redis.constants.RedisSyncQueueConstants;
import com.jzo2o.redis.sync.SyncManager;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

import java.util.concurrent.ThreadPoolExecutor;

import static com.jzo2o.market.constants.RedisConstants.Formatter.*;
import static com.jzo2o.market.constants.RedisConstants.RedisKey.COUPON_SEIZE_SYNC_QUEUE_NAME;

@Component
@Slf4j
public class XxlJobHandler {

    @Resource
    private SyncManager syncManager;

    @Resource
    private IActivityService activityService;

    @Resource
    private ICouponService couponService;

    /**
     * 活动状态到期变更任务
     */
    @XxlJob("updateActivityStatus")
    public void updateActivityStatus() {
        log.info("定时修改活动状态...");
        try {
            activityService.updateStatus();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 已领取优惠券自动过期任务
     */
    @XxlJob("processExpireCoupon")
    public void processExpireCoupon() {
        couponService.lambdaUpdate()
                .eq(Coupon::getStatus, CouponStatusEnum.NO_USE.getStatus())//未使用
                .le(Coupon::getValidityTime, DateUtils.now())//有效期小于当前时间
                .set(Coupon::getStatus, CouponStatusEnum.INVALID.getStatus())//已失效
                .update();
    }


    /**
     * 活动预热(将满足条件的活动,同步到Redis中等待抢券)
     */
    @XxlJob("activityPreheat")
    public void activityPreHeat() {
        log.info("优惠券活动定时预热...");
        try {
            activityService.preHeat();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Resource(name = "syncThreadPool")
    private ThreadPoolExecutor threadPoolExecutor;

    /**
     * 抢券同步队列
     */
    @XxlJob("seizeCouponSyncJob")
    public void seizeCouponSyncJob() {
        /**
         * 开始同步，可以使用自定义线程池，如果不设置使用默认线程池
         *
         * @param queueName 同步队列名称
         * @param storageType 数据存储类型，1：redis hash数据结构，2：redis list数据结构，3：redis zSet结构
         * @param mode 1 单条执行 2批量执行
         * @param dataSyncExecutor 数据同步线程池
         */
        syncManager.start(
                COUPON_SEIZE_SYNC_QUEUE_NAME,//抢券同步队列
                RedisSyncQueueConstants.STORAGE_TYPE_HASH,//redis同步队列存储结构hash
                RedisSyncQueueConstants.MODE_SINGLE,//单条执行模式
                threadPoolExecutor//线程池
        );

    }
}
