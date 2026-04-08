package com.jzo2o.market.model.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 优惠券待发放表
 */
@Data
@TableName("coupon_issue")
public class CouponIssue {

    /**
     * 主键
     */
    @TableId(type = IdType.INPUT)
    private Long id;

    /**
     * 优惠券活动id
     */
    private Long activityId;

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 发放状态 0:待发放，1：已发放
     */
    private Integer status;
}