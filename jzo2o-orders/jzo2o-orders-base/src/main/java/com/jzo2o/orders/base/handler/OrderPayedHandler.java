package com.jzo2o.orders.base.handler;

import cn.hutool.db.DbRuntimeException;
import com.jzo2o.orders.base.enums.OrderPayStatusEnum;
import com.jzo2o.orders.base.enums.OrderStatusEnum;
import com.jzo2o.orders.base.model.dto.OrderSnapshotDTO;
import com.jzo2o.orders.base.model.dto.OrderUpdateStatusDTO;
import com.jzo2o.orders.base.service.IOrdersCommonService;
import com.jzo2o.statemachine.core.StatusChangeEvent;
import com.jzo2o.statemachine.core.StatusChangeHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

//订单支付事件逻辑处理器
@Slf4j
@Component("order_payed")//接口实现类的bean名称规则为:状态机名称_事件名称
public class OrderPayedHandler implements StatusChangeHandler<OrderSnapshotDTO> {

    @Autowired
    private IOrdersCommonService ordersService;

    /**
     * 订单支付处理逻辑
     *
     * @param bizId       业务id
     * @param bizSnapshot 快照
     */
    @Override
    public void handler(String bizId, StatusChangeEvent statusChangeEventEnum, OrderSnapshotDTO bizSnapshot) {
        log.info("支付成功事件处理逻辑开始，订单号：{}", bizId);
        // 修改订单状态和支付状态
        OrderUpdateStatusDTO orderUpdateStatusDTO = OrderUpdateStatusDTO.builder()
                .id(Long.valueOf(bizId))
                .originStatus(OrderStatusEnum.NO_PAY.getStatus())
                .targetStatus(OrderStatusEnum.DISPATCHING.getStatus())
                .payStatus(OrderPayStatusEnum.PAY_SUCCESS.getStatus())
                .payTime(bizSnapshot.getPayTime())
                .tradingOrderNo(bizSnapshot.getTradingOrderNo())
                .transactionId(bizSnapshot.getThirdOrderId())
                .tradingChannel(bizSnapshot.getTradingChannel())
                .build();
        int result = ordersService.updateStatus(orderUpdateStatusDTO);
        if (result <= 0) {
            throw new DbRuntimeException("支付事件处理失败");
        }
    }
}