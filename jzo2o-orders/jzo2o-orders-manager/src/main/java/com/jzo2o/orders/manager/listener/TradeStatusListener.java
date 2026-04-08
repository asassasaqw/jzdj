package com.jzo2o.orders.manager.listener;


import cn.hutool.core.collection.CollUtil;
import com.alibaba.fastjson.JSON;
import com.jzo2o.api.trade.enums.TradingStateEnum;
import com.jzo2o.common.constants.MqConstants;
import com.jzo2o.common.model.msg.TradeStatusMsg;
import com.jzo2o.orders.manager.service.IOrdersCreateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;

@Component
@Slf4j
public class TradeStatusListener {

    @Autowired
    private IOrdersCreateService ordersCreateService;


    /**
     * 更新支付结果
     * 支付成功
     *
     * @param msg 消息
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = MqConstants.Queues.ORDERS_TRADE_UPDATE_STATUS),
            exchange = @Exchange(name = MqConstants.Exchanges.TRADE, type = ExchangeTypes.TOPIC),
            key = MqConstants.RoutingKeys.TRADE_UPDATE_STATUS
    ))
    public void listenTradeUpdatePayStatusMsg(String msg){
        //将接收到的信息转为集合
        List<TradeStatusMsg> tradeStatusMsgs = JSON.parseArray(msg, TradeStatusMsg.class);
        //筛选出符合的数据
        List<TradeStatusMsg> list = tradeStatusMsgs.stream().filter(
                        e -> e.getStatusCode().equals(TradingStateEnum.YJS.getCode()) && "jzo2o.orders".equals(e.getProductAppId()))
                .collect(Collectors.toList());
        if (CollUtil.isEmpty(list)) {
            return;
        }

        //修改订单状态
        list.forEach(m -> ordersCreateService.paySuccess(m));

    }
}
