package com.jzo2o.market.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jzo2o.market.model.domain.Coupon;
import com.jzo2o.market.model.domain.CouponIssue;
import com.jzo2o.market.model.dto.request.CouponIssueReqDTO;

import java.util.List;

public interface ICouponIssueService extends IService<CouponIssue> {

    /**
     * 根据优惠券id批量新增优惠券发放信息
     *
     * @param couponIssueReqDTO 优惠券发放信息
     * @return 优惠券发放信息
     */
    List<CouponIssue> saveByIds(CouponIssueReqDTO couponIssueReqDTO);

    /**
     * 优惠券发放
     *
     * @param couponIssueReqDTO 优惠券发放信息
     * @return 优惠券发放信息
     */
    List<CouponIssue> issue(CouponIssueReqDTO couponIssueReqDTO);

    /**
     * 自动发放优惠券
     */
    void autoIssue(Long activityId);
}
