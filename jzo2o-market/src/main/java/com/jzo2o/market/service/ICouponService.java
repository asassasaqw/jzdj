package com.jzo2o.market.service;

import com.jzo2o.api.market.dto.request.CouponUseBackReqDTO;
import com.jzo2o.api.market.dto.request.CouponUseReqDTO;
import com.jzo2o.api.market.dto.response.AvailableCouponsResDTO;
import com.jzo2o.api.market.dto.response.CouponUseResDTO;
import com.jzo2o.common.model.PageResult;
import com.jzo2o.market.model.domain.Coupon;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jzo2o.market.model.dto.request.CouponOperationPageQueryReqDTO;
import com.jzo2o.market.model.dto.request.SeizeCouponReqDTO;
import com.jzo2o.market.model.dto.response.CouponInfoResDTO;

import java.math.BigDecimal;
import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author itcast
 * @since 2023-09-16
 */
public interface ICouponService extends IService<Coupon> {


    /**
     * 根据活动id查询优惠券领取记录
     *
     * @param dto 活动id
     * @return 优惠券领取记录
     */
    PageResult<CouponInfoResDTO> findByPage(CouponOperationPageQueryReqDTO dto);

    /**
     * 抢券
     *
     * @param seizeCouponReqDTO 抢券参数
     */
    void seizeCoupon(SeizeCouponReqDTO seizeCouponReqDTO);


    /**
     * 我的优惠券列表
     *
     * @param lastId 最后一个优惠券id
     * @param userId 用户id
     * @param status 状态
     * @return 优惠券列表
     */
    List<CouponInfoResDTO> queryForList(Long lastId, Long userId, Integer status);

    /**
     * 获取可用优惠券列表
     *
     * @param totalAmount 订单总金额
     * @return 可用的优惠券列表
     */
    List<AvailableCouponsResDTO> getAvailable(BigDecimal totalAmount);

    /**
     * 核销优惠券
     *
     * @param couponUseReqDTO 优惠券对象
     * @return 实际使用金额
     */
    CouponUseResDTO use(CouponUseReqDTO couponUseReqDTO);

    /**
     * 退回优惠券
     *
     * @param couponUseBackReqDTO 优惠券
     */
    void useBack(CouponUseBackReqDTO couponUseBackReqDTO);
}
