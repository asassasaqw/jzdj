package com.jzo2o.market.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.db.DbRuntimeException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jzo2o.api.market.dto.request.CouponUseBackReqDTO;
import com.jzo2o.api.market.dto.request.CouponUseReqDTO;
import com.jzo2o.api.market.dto.response.AvailableCouponsResDTO;
import com.jzo2o.api.market.dto.response.CouponUseResDTO;
import com.jzo2o.common.expcetions.BadRequestException;
import com.jzo2o.common.expcetions.CommonException;
import com.jzo2o.common.expcetions.DBException;
import com.jzo2o.common.expcetions.ForbiddenOperationException;
import com.jzo2o.common.model.PageResult;
import com.jzo2o.common.model.dto.PageQueryDTO;
import com.jzo2o.common.utils.*;
import com.jzo2o.market.enums.ActivityStatusEnum;
import com.jzo2o.market.enums.CouponStatusEnum;
import com.jzo2o.market.mapper.CouponMapper;
import com.jzo2o.market.model.domain.Activity;
import com.jzo2o.market.model.domain.Coupon;
import com.jzo2o.market.model.domain.CouponUseBack;
import com.jzo2o.market.model.domain.CouponWriteOff;
import com.jzo2o.market.model.dto.request.CouponOperationPageQueryReqDTO;
import com.jzo2o.market.model.dto.request.SeizeCouponReqDTO;
import com.jzo2o.market.model.dto.response.ActivityInfoResDTO;
import com.jzo2o.market.model.dto.response.CouponInfoResDTO;
import com.jzo2o.market.service.IActivityService;
import com.jzo2o.market.service.ICouponService;
import com.jzo2o.market.service.ICouponUseBackService;
import com.jzo2o.market.service.ICouponWriteOffService;
import com.jzo2o.market.utils.CouponUtils;
import com.jzo2o.mvc.utils.UserContext;
import com.jzo2o.mysql.utils.PageUtils;
import com.jzo2o.redis.utils.RedisSyncQueueUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static com.jzo2o.common.constants.ErrorInfo.Code.SEIZE_COUPON_FAILD;
import static com.jzo2o.market.constants.RedisConstants.RedisKey.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author itcast
 * @since 2023-09-16
 */
@Service
@Slf4j
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements ICouponService {

    @Resource(name = "seizeCouponScript")
    private DefaultRedisScript<String> seizeCouponScript;

    @Resource
    private RedisTemplate redisTemplate;

    @Resource
    private IActivityService activityService;

    @Resource
    private ICouponUseBackService couponUseBackService;

    @Resource
    private ICouponWriteOffService couponWriteOffService;


    @Override
    public PageResult<CouponInfoResDTO> findByPage(CouponOperationPageQueryReqDTO dto) {
        //0. 校验
        if (dto.getActivityId() == null) {
            throw new ForbiddenOperationException("请指定活动id");
        }

        //1. 设置分页条件
        Page<Coupon> page = PageUtils.parsePageQuery(dto, Coupon.class);

        //2. 设置业务条件
        LambdaQueryWrapper<Coupon> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Coupon::getActivityId, dto.getActivityId());

        //3. 执行分页
        page = this.page(page,wrapper);

        //4. 组装返回结果
        return PageUtils.toPage(page,CouponInfoResDTO.class);
    }

    //抢券
    @Override
    public void seizeCoupon(SeizeCouponReqDTO seizeCouponReqDTO) {
        // 1.校验当前时间是否在活动期间
        ActivityInfoResDTO activity = activityService.getActivityInfoByIdFromCache(seizeCouponReqDTO.getId());
        if (activity == null || activity.getDistributeStartTime().isAfter(LocalDateTime.now())) {
            throw new CommonException(SEIZE_COUPON_FAILD, "活动不存在或者未开始");
        }
        if (activity.getDistributeEndTime().isBefore(LocalDateTime.now())) {
            throw new CommonException(SEIZE_COUPON_FAILD, "活动不存在或者已结束");
        }

        //2. 抢券
        //2-1 准备参数
        int index = (int) (seizeCouponReqDTO.getId() % 10);
        String couponSeizeSyncRedisKey = RedisSyncQueueUtils.getQueueRedisKey(COUPON_SEIZE_SYNC_QUEUE_NAME, index);// 同步队列redisKey
        String resourceStockRedisKey = String.format(COUPON_RESOURCE_STOCK, index);// 资源库存redisKey
        String couponSeizeListRedisKey = String.format(COUPON_SEIZE_LIST, activity.getId(), index);// 抢券列表
        log.debug("seize coupon keys -> couponSeizeListRedisKey->{},resourceStockRedisKey->{},couponSeizeListRedisKey->{},seizeCouponReqDTO.getId()->{},UserContext.currentUserId():{}",
                couponSeizeListRedisKey, resourceStockRedisKey, couponSeizeListRedisKey, seizeCouponReqDTO.getId(), UserContext.currentUserId());

        //2-2 执行抢券脚本
        Object executeResult = redisTemplate.execute(
                seizeCouponScript, //脚本
                Arrays.asList(couponSeizeSyncRedisKey, resourceStockRedisKey, couponSeizeListRedisKey),//键
                seizeCouponReqDTO.getId(), UserContext.currentUserId()//参数
        );

        //3. 返回结果
        if (executeResult == null) {
            throw new CommonException(SEIZE_COUPON_FAILD, "抢券失败");
        }
        long result = NumberUtils.parseLong(executeResult.toString());
        if (result > 0) {
            return; //成功
        }else if (result == -1) {
            throw new CommonException(SEIZE_COUPON_FAILD, "限领一张");
        }else if (result == -2 || result == -4) {
            throw new CommonException(SEIZE_COUPON_FAILD, "已抢光!");
        }else{
            throw new CommonException(SEIZE_COUPON_FAILD, "抢券失败");
        }
    }

    @Override
    public List<CouponInfoResDTO> queryForList(Long lastId, Long userId, Integer status) {
        List<Coupon> list = this.lambdaQuery()
                .eq(Coupon::getStatus, status)
                .eq(Coupon::getUserId, userId)
                .lt(lastId != null, Coupon::getId, lastId)
                .orderByDesc(Coupon::getCreateTime)
                .last("limit 10")
                .list();
        return BeanUtils.copyToList(list, CouponInfoResDTO.class);
    }

    @Override
    public List<AvailableCouponsResDTO> getAvailable(BigDecimal totalAmount) {
        //- 优惠金额：小于订单金额
        //1. 查询优惠券
        List<Coupon> list = this.lambdaQuery()
                .eq(Coupon::getUserId, UserContext.currentUserId())//- 所属用户：当前登录用户
                .eq(Coupon::getStatus, CouponStatusEnum.NO_USE.getStatus())//- 状态：未使用
                .ge(Coupon::getValidityTime, LocalDateTime.now())//- 在有效使用期限内
                .le(Coupon::getAmountCondition, totalAmount)//- 满减金额：小于等于订单总额
                .list();

        //2. 优惠金额：小于订单金额
        List<Coupon> collect = list.stream()
                .map(e -> e.setDiscountAmount(CouponUtils.calDiscountAmount(e, totalAmount))) //获取每个优惠券对应当前订单的优惠金额
                .filter(e ->
                        e.getDiscountAmount().compareTo(new BigDecimal(0)) > 0
                                && e.getDiscountAmount().compareTo(totalAmount) < 0
                )//0 < 优惠金额：小于订单金额
                .sorted(Comparator.comparing(Coupon::getDiscountAmount).reversed())//按照优惠金额从大到小排序
                .collect(Collectors.toList());

        //3. 返回结果
        return BeanUtils.copyToList(collect, AvailableCouponsResDTO.class);
    }

    @Override
    @Transactional
    public CouponUseResDTO use(CouponUseReqDTO couponUseReqDTO) {
        //1. 校验优惠券信息: 只有订单金额大于等于满减金额，并且优惠券在有效状态方可使用
        Coupon coupon = this.lambdaQuery()
                .eq(Coupon::getUserId, UserContext.currentUserId())//- 所属用户：当前登录用户
                .eq(Coupon::getStatus, CouponStatusEnum.NO_USE.getStatus())//- 状态：未使用
                .ge(Coupon::getValidityTime, LocalDateTime.now())//- 在有效使用期限内
                .le(Coupon::getAmountCondition, couponUseReqDTO.getTotalAmount())//- 满减金额：小于等于订单总额
                .eq(Coupon::getId, couponUseReqDTO.getId())//优惠券id
                .one();
        if (ObjectUtil.isNull(coupon)) {
            throw new ForbiddenOperationException("优惠券核销失败");
        }

        //2. 修改优惠券表中该优惠券的使用状态（已使用）、使用时间（当前时间）、订单id（订单微服务传入）
        coupon.setStatus(CouponStatusEnum.USED.getStatus());//使用状态（已使用）
        coupon.setUseTime(LocalDateTime.now());//使用时间（当前时间）
        coupon.setOrdersId(couponUseReqDTO.getOrdersId().toString());//订单id（订单微服务传入）
        this.updateById(coupon);

        //3. 向优惠券核销表添加一条记录
        CouponWriteOff couponWriteOff = new CouponWriteOff();
        couponWriteOff.setCouponId(couponUseReqDTO.getId());
        couponWriteOff.setUserId(UserContext.currentUserId());
        couponWriteOff.setOrdersId(couponUseReqDTO.getOrdersId());
        couponWriteOff.setActivityId(coupon.getActivityId());
        couponWriteOff.setWriteOffTime(LocalDateTime.now());
        couponWriteOff.setWriteOffManPhone(coupon.getUserPhone());
        couponWriteOff.setWriteOffManName(coupon.getUserName());
        couponWriteOffService.save(couponWriteOff);

        //4. 核销成功返回最终优惠的金额
        BigDecimal discountAmount = CouponUtils.calDiscountAmount(coupon, couponUseReqDTO.getTotalAmount());
        CouponUseResDTO couponUseResDTO = new CouponUseResDTO();
        couponUseResDTO.setDiscountAmount(discountAmount);
        return couponUseResDTO;
    }

    @Override
    @Transactional
    public void useBack(CouponUseBackReqDTO couponUseBackReqDTO) {
        //1.检查优惠卷是否有核销记录，没有则不需要退回
        CouponWriteOff couponWriteOff = couponWriteOffService.lambdaQuery()
                .eq(CouponWriteOff::getOrdersId, couponUseBackReqDTO.getOrdersId())
                .eq(CouponWriteOff::getUserId, couponUseBackReqDTO.getUserId())
                .one();

        if ((ObjectUtils.isNull(couponWriteOff))){
            throw new ForbiddenOperationException("优惠券退回失败,原因：没有对应的核销记录");
        }

        //2.在优惠卷退回表中添加一条记录
        CouponUseBack couponUseBack = new CouponUseBack();
        couponUseBack.setCouponId(couponWriteOff.getCouponId());
        couponUseBack.setUserId(couponWriteOff.getUserId());
        couponUseBack.setUseBackTime(LocalDateTime.now());
        couponUseBack.setWriteOffTime(couponWriteOff.getWriteOffTime());
        couponUseBackService.save(couponUseBack);

        //3.修改优惠券表状态
        Coupon coupon = lambdaQuery().eq(Coupon::getId, couponWriteOff.getCouponId()).one();
        if (ObjectUtils.isNull(coupon)){
            throw new ForbiddenOperationException("优惠券退回失败,原因：没有对应的优惠券");
        }

        //3.1如果优惠卷的使用期限在当前时间之前，则修改为作废
        if (coupon.getValidityTime().isBefore(LocalDateTime.now())) {
            coupon.setStatus(CouponStatusEnum.INVALID.getStatus());
        }else if (coupon.getValidityTime().isAfter(LocalDateTime.now())){
            coupon.setStatus(CouponStatusEnum.NO_USE.getStatus());
        }
        //3.2如果优惠卷所对应的活动已作废，则修改为作废
        Activity activity = activityService.lambdaQuery().eq(Activity::getId, coupon.getActivityId()).one();
        if (ObjectUtils.isNull(activity)){
            throw new ForbiddenOperationException("优惠券退回失败,原因：没有对应的活动");
        }
        if (ActivityStatusEnum.VOIDED.equals(activity.getStatus())){
            coupon.setStatus(CouponStatusEnum.VOIDED.getStatus());
        }
//        coupon.setOrdersId( null);
//        coupon.setUseTime(null);
//        updateById(coupon);
//        //4.删除优惠券核销记录
//        couponWriteOffService.removeById(couponWriteOff);
        boolean b = this.lambdaUpdate()
                .set(Coupon::getStatus, coupon.getStatus())
                .set(Coupon::getOrdersId, null)
                .set(Coupon::getUseTime, null)
                .eq(Coupon::getId, coupon.getId())
                .update();
        if (!b) {
            throw new ForbiddenOperationException("优惠券退回失败,原因: 更新优惠券失败");
        }

        //4. 删除优惠券核销表中的相关记录
        boolean b1 = couponWriteOffService.removeById(couponWriteOff.getId());
        if (!b1) {
            throw new ForbiddenOperationException("优惠券退回失败,原因: 删除优惠券核销记录失败");
        }

    }
}
