package com.jzo2o.market.service;

import com.jzo2o.common.model.PageResult;
import com.jzo2o.market.model.domain.Activity;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jzo2o.market.model.dto.request.ActivityQueryForPageReqDTO;
import com.jzo2o.market.model.dto.request.ActivitySaveReqDTO;
import com.jzo2o.market.model.dto.response.ActivityInfoResDTO;
import com.jzo2o.market.model.dto.response.SeizeCouponInfoResDTO;

import javax.validation.constraints.Null;
import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author itcast
 * @since 2023-09-16
 */
public interface IActivityService extends IService<Activity> {

    /**
     * 新增或修改一个优惠券活动
     *
     * @param dto 优惠券活动
     */
    void saveOrUpdateActivity(ActivitySaveReqDTO dto);

    /**
     * 运营端分页查询活动
     *
     * @param dto 查询条件
     * @return 优惠券活动分页结果
     */
    PageResult<ActivityInfoResDTO> findByPage(ActivityQueryForPageReqDTO dto);

    /**
     * 查询活动详情
     *
     * @param id 优惠券活动id
     * @return 优惠券活动详情
     */
    ActivityInfoResDTO findById(Long id);

    /**
     * 活动撤销
     *
     * @param id 活动id
     */
    void revoke(Long id);

    void updateStatus();

    /**
     * 活动预热(将满足条件的活动,同步到Redis中等待抢券)
     */
    void preHeat();

    /**
     * 用户端抢券列表分页查询活动信息
     *
     * @param tabType 挑选条件 1 疯抢中  2 即将开始
     * @return 活动列表
     */
    List<SeizeCouponInfoResDTO> queryForListFromCache(Integer tabType);

    /**
     * 从缓存中获取活动信息
     *
     * @param id 活动id
     * @return 活动信息
     */
    ActivityInfoResDTO getActivityInfoByIdFromCache(Long id);
}
