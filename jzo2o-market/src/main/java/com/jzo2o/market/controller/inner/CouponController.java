package com.jzo2o.market.controller.inner;

import com.jzo2o.api.market.dto.request.CouponUseBackReqDTO;
import com.jzo2o.api.market.dto.request.CouponUseReqDTO;
import com.jzo2o.api.market.dto.response.AvailableCouponsResDTO;
import com.jzo2o.api.market.dto.response.CouponUseResDTO;
import com.jzo2o.market.service.ICouponService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController("innerCouponController")
@RequestMapping("/inner/coupon")
@Api(tags = "内部接口-优惠券相关接口")
public class CouponController{

    @Autowired
    private ICouponService couponService;

    @ApiOperation("获取可用优惠券列表")
    @GetMapping("/getAvailable")
    public List<AvailableCouponsResDTO> getAvailable(@RequestParam("totalAmount") BigDecimal totalAmount) {
        return couponService.getAvailable(totalAmount);
    }

    @ApiOperation("使用优惠券，并返回优惠金额")
    @PostMapping("/use")
    public CouponUseResDTO use(@RequestBody CouponUseReqDTO couponUseReqDTO) {
        return couponService.use(couponUseReqDTO);
    }

    @PostMapping("/useBack")
    @ApiOperation("退回优惠券")
    public void useBack(@RequestBody CouponUseBackReqDTO couponUseBackReqDTO) {
        couponService.useBack(couponUseBackReqDTO);
    }
}