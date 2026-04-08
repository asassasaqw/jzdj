package com.jzo2o.market.controller.inner;


import com.jzo2o.market.model.domain.CouponIssue;
import com.jzo2o.market.model.dto.request.CouponIssueReqDTO;
import com.jzo2o.market.service.ICouponIssueService;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Component
@RequestMapping("/inner/issuedCoupon")
@Slf4j
public class IssuedCouponController {

    @Autowired
    private ICouponIssueService couponIssueService;

    @PostMapping("/issue")
    public List<CouponIssue> issue(@RequestBody CouponIssueReqDTO couponIssueReqDTO) {
        return couponIssueService.issue(couponIssueReqDTO);
    }


    @ApiOperation("提交待发放优惠券数据")
    @PostMapping("/save")
    public List<CouponIssue> save(@RequestBody CouponIssueReqDTO couponIssueReqDTO) {
        return couponIssueService.saveByIds(couponIssueReqDTO);
    }
}
