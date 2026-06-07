package com.jzo2o.orders.manager.strategy.impl;

import com.jzo2o.common.expcetions.ForbiddenOperationException;
import com.jzo2o.orders.base.enums.OrderStatusEnum;
import com.jzo2o.orders.base.mapper.OrdersCanceledMapper;
import com.jzo2o.orders.base.model.domain.OrdersCanceled;
import com.jzo2o.orders.base.model.dto.OrderUpdateStatusDTO;
import com.jzo2o.orders.base.service.IOrdersCommonService;
import com.jzo2o.orders.manager.model.dto.OrderCancelDTO;
import com.jzo2o.orders.manager.strategy.OrderCancelStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

//普通用户取消未支付订单
@Component("1:NO_PAY")//用户类型:订单状态
public class CommonUserNoPayOrderCancelStrategy implements OrderCancelStrategy {

    @Autowired
    private OrdersCanceledMapper ordersCanceledMapper;

    @Autowired
    private IOrdersCommonService ordersCommonService;
    @Override
    @Transactional
    public void cancel(OrderCancelDTO orderCancelDTO) {
        // 1) 更新订单状态为已取消
        // update orders set orders_status = 600 where id = 订单id and orders_status = 0
        OrderUpdateStatusDTO orderUpdateStatusDTO = OrderUpdateStatusDTO.builder()
                .id(orderCancelDTO.getId())//订单id
                .originStatus(OrderStatusEnum.NO_PAY.getStatus())//原始状态
                .targetStatus(OrderStatusEnum.CANCELED.getStatus())//目标状态
                .build();
        Integer i = ordersCommonService.updateStatus(orderUpdateStatusDTO);
        if (i <= 0) {
            throw new ForbiddenOperationException("订单取消失败");
        }

        // 2) 保存取消订单记录
        OrdersCanceled ordersCanceled = new OrdersCanceled();
        ordersCanceled.setId(orderCancelDTO.getId());//订单id
        ordersCanceled.setCancellerId(orderCancelDTO.getCurrentUserId());//取消人
        ordersCanceled.setCancelerName(orderCancelDTO.getCurrentUserName());//取消人名称
        ordersCanceled.setCancellerType(orderCancelDTO.getCurrentUserType());//取消人类型，1：普通用户，4：运营人员
        ordersCanceled.setCancelReason(orderCancelDTO.getCancelReason());//取消原因
        ordersCanceled.setCancelTime(LocalDateTime.now());//取消时间
        ordersCanceledMapper.insert(ordersCanceled);
    }
}
