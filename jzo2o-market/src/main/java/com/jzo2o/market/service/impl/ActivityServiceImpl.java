package com.jzo2o.market.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jzo2o.common.expcetions.BadRequestException;
import com.jzo2o.common.expcetions.ForbiddenOperationException;
import com.jzo2o.common.model.PageResult;
import com.jzo2o.common.utils.*;
import com.jzo2o.market.constants.TabTypeConstants;
import com.jzo2o.market.enums.ActivityStatusEnum;
import com.jzo2o.market.enums.CouponStatusEnum;
import com.jzo2o.market.mapper.ActivityMapper;
import com.jzo2o.market.mapper.CouponMapper;
import com.jzo2o.market.model.domain.Activity;
import com.jzo2o.market.model.domain.Coupon;
import com.jzo2o.market.model.domain.CouponWriteOff;
import com.jzo2o.market.model.dto.request.ActivityQueryForPageReqDTO;
import com.jzo2o.market.model.dto.request.ActivitySaveReqDTO;
import com.jzo2o.market.model.dto.response.ActivityInfoResDTO;
import com.jzo2o.market.model.dto.response.CountResDTO;
import com.jzo2o.market.model.dto.response.SeizeCouponInfoResDTO;
import com.jzo2o.market.service.IActivityService;
import com.jzo2o.market.service.ICouponService;
import com.jzo2o.market.service.ICouponWriteOffService;
import com.jzo2o.mysql.utils.PageUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.jzo2o.market.constants.RedisConstants.RedisKey.*;
import static com.jzo2o.market.enums.ActivityStatusEnum.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author itcast
 * @since 2023-09-16
 */
@Service
public class ActivityServiceImpl extends ServiceImpl<ActivityMapper, Activity> implements IActivityService {
    private static final int MILLION = 1000000;

    @Resource
    private RedisTemplate redisTemplate;

    @Resource
    private ICouponService couponService;

    @Resource
    private ICouponWriteOffService couponWriteOffService;
    @Autowired
    private CouponMapper couponMapper;
    @Autowired
    private ActivityServiceImpl activityServiceImpl;


    @Override
    public void saveOrUpdateActivity(ActivitySaveReqDTO reqDTO) {
        //1.参数校验
        reqDTO.check();
        //2.将dto装为实体类对象
        Activity activity = BeanUtil.copyProperties(reqDTO, Activity.class);
        activity.setStatus(ActivityStatusEnum.NO_DISTRIBUTE.getStatus());
        activity.setStockNum(reqDTO.getTotalNum());
        //3.保存
        saveOrUpdate(activity);
    }

    @Override
    public PageResult<ActivityInfoResDTO> findByPage(ActivityQueryForPageReqDTO dto) {
        //1. 设置分页条件
        Page<Activity> page = PageUtils.parsePageQuery(dto, Activity.class);

        //2. 设置业务条件
        LambdaQueryWrapper<Activity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(dto.getId() != null, Activity::getId, dto.getId())
                .like(StringUtils.isNotEmpty(dto.getName()), Activity::getName, dto.getName())
                .eq(dto.getType() != null, Activity::getType, dto.getType())
                .eq(dto.getStatus() != null, Activity::getStatus, dto.getStatus());

        //3. 执行分页查询
        page = this.page(page, wrapper);
        if (CollUtil.isEmpty(page.getRecords())) {
            return new PageResult<ActivityInfoResDTO>(page.getPages(), page.getTotal(), List.of());
        }

        //4. 提前查询本页所有活动id对应的优惠券领取数量, 封装到一个Map<活动id,优惠券领取数量>集合中备用
        // 4-1 提前收集到当前页所有活动的id到一个集合中
        List<Long> activityIdList
                = page.getRecords().stream().map(Activity::getId).collect(Collectors.toList());

        //4-2 调用mapper根据优惠券活动id集合查询优惠券领取数量集合
        List<CountResDTO> countResDTOList = couponMapper.countByActivityIdList(activityIdList);

        //4-3 将上述集合转换为map备用
        Map<Long, Integer> receiveNumMap = countResDTOList.stream().collect(Collectors.toMap(CountResDTO::getActivityId, CountResDTO::getNum));

        //5. 组装返回结果
        List<ActivityInfoResDTO> list = page.getRecords().stream().map(e -> {
            ActivityInfoResDTO activityInfoResDTO = BeanUtil.copyProperties(e, ActivityInfoResDTO.class);

            //优惠券领取数量  活动id
            //select count(*) from coupon where activity_id = 活动id
            //Integer count1 = couponService.lambdaQuery().eq(Coupon::getActivityId, e.getId()).count();
            activityInfoResDTO.setReceiveNum(receiveNumMap.getOrDefault(e.getId(),0));

            //优惠券核销数量
            //select count(*) from coupon_write_off where activity_id = 活动id
            Integer count2 = couponWriteOffService.lambdaQuery().eq(CouponWriteOff::getActivityId, e.getId()).count();
            activityInfoResDTO.setWriteOffNum(count2);

            return activityInfoResDTO;
        }).collect(Collectors.toList());
        return new PageResult<ActivityInfoResDTO>(page.getPages(), page.getTotal(), list);
    }

    @Override
    public ActivityInfoResDTO findById(Long id) {
        //1. 根据活动id查询activity表
        Activity activity = this.getById(id);
        if (ObjectUtil.isNull(activity)){
            throw new ForbiddenOperationException("当前优惠券活动不存在");
        }
        ActivityInfoResDTO activityInfoResDTO
                = BeanUtil.copyProperties(activity, ActivityInfoResDTO.class);

        //2. 根据活动id查询coupon统计当前活动的领取数量
        Integer count1 = couponService.lambdaQuery().eq(Coupon::getActivityId, id).count();
        activityInfoResDTO.setReceiveNum(count1);

        //3. 根据活动id查询coupon_write_off统计当前活动的核销数量
        Integer count2 = couponWriteOffService.lambdaQuery().eq(CouponWriteOff::getActivityId, id).count();
        activityInfoResDTO.setWriteOffNum(count2);

        return activityInfoResDTO;
    }

    @Override
    @Transactional
    public void revoke(Long id) {
        //1. 根据活动id查询活动的状态
        Activity activity = this.getById(id);
        if (ObjectUtil.isNull(activity)) {
            throw new ForbiddenOperationException("当前活动不存在");
        }
        if (activity.getStatus() != NO_DISTRIBUTE.getStatus()
                && activity.getStatus() != DISTRIBUTING.getStatus()) {
            throw new ForbiddenOperationException("当前活动状态不允许撤销");
        }

        //2. 修改活动的状态  待生效或者进行中 ---> 作废
        //update activity set status = 4 where id = 活动id and status in(1,2)
        boolean flag = this.lambdaUpdate()
                .eq(Activity::getId, id)//id = 活动id
                .in(Activity::getStatus, NO_DISTRIBUTE.getStatus(), DISTRIBUTING.getStatus())//status in(1,2)
                .set(Activity::getStatus, VOIDED.getStatus())//set status = 4
                .update();

        //3. 修改优惠券的状态 未使用  --> 已作废
        //update coupon set status = 4 where activity_id = 活动id and status = 1
        if(flag){
            couponService.lambdaUpdate()
                    .eq(Coupon::getActivityId,id)//activity_id = 活动id
                    .eq(Coupon::getStatus, CouponStatusEnum.NO_USE.getStatus())//status = 1
                    .set(Coupon::getStatus, CouponStatusEnum.VOIDED.getStatus())//status = 4
                    .update();
        }

    }

    @Override
    public void updateStatus() {
        
    }

    @Override
    public void preHeat() {
        //1. 查询状态是待开始或者进行中，并且发放开始时间距离现在不足1个月的活动，按照开始时间升序排列
        //select * from activity where status in (1,2)  and distribute_start_time < 当前时间+1个月 order by distribute_start_time asc
        List<Activity> list = this.lambdaQuery()
                .in(Activity::getStatus, NO_DISTRIBUTE.getStatus(), DISTRIBUTING.getStatus())//status in (1,2)
                .lt(Activity::getDistributeStartTime, LocalDateTime.now().plusMonths(1))//distribute_start_time < 当前时间+1个月
                .orderByAsc(Activity::getDistributeStartTime)//order by distribute_start_time asc
                .list();
        if (CollUtil.isEmpty(list)){
            list = new ArrayList<>();//防止缓存穿透
        }


        //2. 将查询到的数据封装到List<SeizeCouponInfoResDTO>，再转换为JSON串
        List<SeizeCouponInfoResDTO> seizeCouponInfoResDTOS = BeanUtils.copyToList(list, SeizeCouponInfoResDTO.class);
        String jsonStr = JsonUtils.toJsonStr(seizeCouponInfoResDTOS);

        //3. 将JSON字符串存入redis
        redisTemplate.opsForValue().set(ACTIVITY_CACHE_LIST,jsonStr);

        //4. 将优惠券活动的库存从MySQL同步到Redis
        //4-1 对于待生效的活动更新库存
        list.stream().filter(e ->
                getStatus(e.getDistributeStartTime(), e.getDistributeEndTime(), e.getStatus()) == NO_DISTRIBUTE.getStatus()
        ).forEach(e ->
                redisTemplate.opsForHash().put(String.format(COUPON_RESOURCE_STOCK, e.getId() % 10), e.getId(), e.getStockNum())
        );

        //4-2 对于已生效的活动如果库存已经同步则不再同步
        list.stream().filter(e ->
                getStatus(e.getDistributeStartTime(), e.getDistributeEndTime(), e.getStatus()) == DISTRIBUTING.getStatus()
        ).forEach(e ->
                //只有库存不存在的情况下, 才要进行保存操作
                redisTemplate.opsForHash().putIfAbsent(String.format(COUPON_RESOURCE_STOCK, e.getId() % 10), e.getId(), e.getStockNum())
        );


    }

    @Override
    public List<SeizeCouponInfoResDTO> queryForListFromCache(Integer tabType) {
        //1. 从Redis中查询优惠券活动的数据
        String jsonStr = (String) redisTemplate.opsForValue().get(ACTIVITY_CACHE_LIST);
        if (StringUtils.isEmpty(jsonStr)) {
            return List.of();
        }

        //2. 将查询到的JSON字符串转换为集合List<SeizeCouponInfoResDTO>
        List<SeizeCouponInfoResDTO> seizeCouponInfoResDTOS
                = JSON.parseArray(jsonStr, SeizeCouponInfoResDTO.class);
        if (CollUtil.isEmpty(seizeCouponInfoResDTOS)) {
            return List.of();
        }

        //3. 根据tabType的值筛选符合条件的活动返回
        return seizeCouponInfoResDTOS.stream().filter(e->{
            int status = getStatus(e.getDistributeStartTime(), e.getDistributeEndTime(), e.getStatus());
            if (tabType == 1){
                return status ==DISTRIBUTING.getStatus();
            }else {
                return status == NO_DISTRIBUTE.getStatus();
            }
        }).map(e->{ e.setRemainNum(e.getStockNum());
            return e;
        }).collect(Collectors.toList());
    }

    @Override
    public ActivityInfoResDTO getActivityInfoByIdFromCache(Long id) {
        //1. 缓存中获取活动信息
        String jsonString = (String) redisTemplate.opsForValue().get(ACTIVITY_CACHE_LIST);
        if (StringUtils.isEmpty(jsonString)) {
            return null;
        }

        //2. 字符串转换为集合
        List<ActivityInfoResDTO> activityInfoResDTOList = JSON.parseArray(jsonString, ActivityInfoResDTO.class);
        if (CollUtil.isEmpty(activityInfoResDTOList)) {
            return null;
        }

        //3. 过滤出指定id的活动
        return activityInfoResDTOList.stream()
                .filter(e -> e.getId().equals(id))
                .findFirst().orElse(null);
    }

    /**
     * 根据活动的目前状态、开始、结束时间 对比当前时间来获取到活动的真实状态
     * 1. 状态在待生效, 但是 活动开始时间 <=当前时间 < 活动结束时间  真实状态应该是 进行中
     * 2. 状态在待生效, 但是 活动结束时间 < 当前时间               真实状态应该是 已结束
     * 3. 状态在进行中, 但是 活动结束时间 < 当前时间               真实状态应该是 已结束
     * 4. 其它情况, 当前状态就是真实状态
     *
     * @param distributeStartTime 活动开始时间
     * @param distributeEndTime   活动结束时间
     * @param status              当前状态
     * @return 活动的真实状态
     */
    private int getStatus(LocalDateTime distributeStartTime, LocalDateTime distributeEndTime, Integer status) {
        if (NO_DISTRIBUTE.getStatus() == status && distributeStartTime.isBefore(DateUtils.now()) && distributeEndTime.isAfter(LocalDateTime.now())) {
            //状态在待生效, 但是 活动开始时间<=当前时间<活动结束时间  真实状态应该是 进行中
            return DISTRIBUTING.getStatus();
        } else if (NO_DISTRIBUTE.getStatus() == status && distributeEndTime.isBefore(LocalDateTime.now())) {
            //状态在待生效, 但是 活动结束时间 < 当前时间   真实状态应该是 已结束
            return LOSE_EFFICACY.getStatus();
        } else if (DISTRIBUTING.getStatus() == status && distributeEndTime.isBefore(LocalDateTime.now())) {
            //状态在进行中, 但是 活动结束时间 < 当前时间   真实状态应该是 已结束
            return LOSE_EFFICACY.getStatus();
        }
        return status;
    }
}
