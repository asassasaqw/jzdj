package com.jzo2o.orders.manager.strategy.impl;


import cn.hutool.core.bean.BeanUtil;
import com.jzo2o.orders.base.config.OrderStateMachine;
import com.jzo2o.orders.base.enums.OrderStatusChangeEventEnum;
import com.jzo2o.orders.base.mapper.OrdersCanceledMapper;
import com.jzo2o.orders.base.mapper.OrdersRefundMapper;
import com.jzo2o.orders.base.model.domain.OrdersCanceled;
import com.jzo2o.orders.base.model.domain.OrdersRefund;
import com.jzo2o.orders.base.model.dto.OrderSnapshotDTO;
import com.jzo2o.orders.manager.model.dto.OrderCancelDTO;
import com.jzo2o.orders.manager.strategy.OrderCancelStrategy;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 普通用户取消未服务订单
 */
@Component("1:NO_SERVE")//用户类型:订单状态
public class CommonUserNoServeOrderCancelStrategy implements OrderCancelStrategy {

    @Autowired
    private OrdersCanceledMapper ordersCanceledMapper;
    @Autowired
    private OrdersRefundMapper ordersRefundMapper;
    @Autowired
    private OrderStateMachine orderStateMachine;
    @Override
    public void cancel(OrderCancelDTO orderCancelDTO) {
        //改为状态机取消订单修改状态
        OrderSnapshotDTO orderSnapshotDTO = OrderSnapshotDTO.builder()
                .cancellerId(orderCancelDTO.getCurrentUserId())
                .cancelerName(orderCancelDTO.getCurrentUserName())
                .cancellerType(orderCancelDTO.getCurrentUserType())
                .cancelReason(orderCancelDTO.getCancelReason())
                .cancelTime(LocalDateTime.now())
                .build();
        orderStateMachine.changeStatus(orderCancelDTO.getUserId(),String.valueOf(orderCancelDTO.getId()), OrderStatusChangeEventEnum.CLOSE_NO_SERVE_ORDER, orderSnapshotDTO);

        // 2) 保存取消订单记录
        OrdersCanceled ordersCanceled = new OrdersCanceled();
        ordersCanceled.setId(orderCancelDTO.getId());//订单id
        ordersCanceled.setCancellerId(orderCancelDTO.getCurrentUserId());//取消人
        ordersCanceled.setCancelerName(orderCancelDTO.getCurrentUserName());//取消人名称
        ordersCanceled.setCancellerType(orderCancelDTO.getCurrentUserType());//取消人类型，1：普通用户，4：运营人员
        ordersCanceled.setCancelReason(orderCancelDTO.getCancelReason());//取消原因
        ordersCanceled.setCancelTime(LocalDateTime.now());//取消时间
        ordersCanceledMapper.insert(ordersCanceled);

        //3) 保存待退款的记录
        OrdersRefund ordersRefund =  BeanUtil.copyProperties(orderCancelDTO,OrdersRefund.class);
        ordersRefundMapper.insert(ordersRefund);
    }
}
