package com.jzo2o.orders.manager.strategy;

import com.jzo2o.orders.manager.model.dto.OrderCancelDTO;

/**
 * 订单取消策略接口
 */
public interface OrderCancelStrategy {
    /**
     * 订单取消
     *
     * @param orderCancelDTO 取消订单的参数
     */
    void cancel(OrderCancelDTO orderCancelDTO);
}