package com.jzo2o.orders.manager.strategy;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.jzo2o.api.market.CouponApi;
import com.jzo2o.api.market.dto.request.CouponUseBackReqDTO;
import com.jzo2o.common.expcetions.ForbiddenOperationException;
import com.jzo2o.orders.base.enums.OrderStatusEnum;
import com.jzo2o.orders.base.mapper.OrdersMapper;
import com.jzo2o.orders.base.model.domain.Orders;
import com.jzo2o.orders.manager.model.dto.OrderCancelDTO;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class OrderCancelStrategyManager {

    @Autowired
    private OrdersMapper ordersMapper;

    @Autowired
    private CouponApi couponApi;

    //key格式：userType+":"+orderStatusEnum，例：1：NO_PAY
    private Map<String, OrderCancelStrategy> strategyMap = new HashMap<>();

    @PostConstruct //此注解标注的方法会在当前对象创建后自动调用
    public void init() {
        strategyMap = SpringUtil.getBeansOfType(OrderCancelStrategy.class);
        log.debug("订单取消策略类初始化到map完成！");
    }

    @GlobalTransactional
    public void cancel(OrderCancelDTO orderCancelDTO) {
        //1. 根据订单id查询订单信息,如果订单不存在, 直接返回错误
        Orders orders = ordersMapper.selectById(orderCancelDTO.getId());
        if (ObjectUtil.isNull(orders)) {
            throw new ForbiddenOperationException("订单不存在");
        }
        BeanUtil.copyProperties(orders, orderCancelDTO);


        //添加退回优惠券的逻辑
        if (orders.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0){
            CouponUseBackReqDTO couponUseBackReqDTO = new CouponUseBackReqDTO();
            couponUseBackReqDTO.setOrdersId(orders.getId());//订单id
            couponUseBackReqDTO.setUserId(orders.getUserId());//用户id
            couponApi.useBack(couponUseBackReqDTO);
        }

        //2. 根据用户类型和订单状态获取获取策略对象
        String key = orderCancelDTO.getCurrentUserType() + ":" + OrderStatusEnum.codeOf(orders.getOrdersStatus()).toString();
        OrderCancelStrategy strategy =  strategyMap.get(key);
        if (ObjectUtil.isEmpty(strategy)) {
            throw new ForbiddenOperationException("不被许可的操作");
        }

        //3. 执行策略对象的方法
        strategy.cancel(orderCancelDTO);
    }



}
