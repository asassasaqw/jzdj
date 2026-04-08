package com.jzo2o.orders.manager.handler;


import cn.hutool.core.collection.CollUtil;
import com.jzo2o.api.trade.RefundRecordApi;
import com.jzo2o.api.trade.dto.response.ExecutionResultResDTO;
import com.jzo2o.common.constants.UserType;
import com.jzo2o.orders.base.enums.OrderPayStatusEnum;
import com.jzo2o.orders.base.enums.OrderRefundStatusEnum;
import com.jzo2o.orders.base.enums.OrderStatusEnum;
import com.jzo2o.orders.base.model.domain.Orders;
import com.jzo2o.orders.base.model.domain.OrdersRefund;
import com.jzo2o.orders.manager.model.dto.OrderCancelDTO;
import com.jzo2o.orders.manager.service.IOrdersManagerService;
import com.jzo2o.orders.manager.service.IOrdersRefundService;
import com.jzo2o.orders.manager.strategy.OrderCancelStrategyManager;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单处理器类
 */
@Slf4j
@Component
public class OrdersHandler {
    @Autowired
    private IOrdersRefundService ordersRefundService;

    @Autowired
    private RefundRecordApi refundRecordApi;

    @Autowired
    private IOrdersManagerService ordersManagerService;

    @Autowired
    private OrderCancelStrategyManager orderCancelStrategyManager;

    @Autowired
    private OrdersHandler owner;

    @XxlJob(value = "handleRefundOrders")
    public void handleRefundOrders(){
        //1. 读取退款表中的数据
        //限定查记录的数量
        List<OrdersRefund> ordersRefundList = ordersRefundService.queryRefundOrderListByCount(100);
        if (CollUtil.isEmpty(ordersRefundList)){
            return;
        }

        //2.遍历退款表数据
        for (OrdersRefund ordersRefund : ordersRefundList) {
            //2.1. 然后调用支付服务的退款接口
            ExecutionResultResDTO executionResultResDTO
                    = refundRecordApi.refundTrading(ordersRefund.getTradingOrderNo(), ordersRefund.getRealPayAmount());

            if (executionResultResDTO != null){
                //4. 根据退款接口的返回值做处理
                if (executionResultResDTO.getRefundStatus() == OrderRefundStatusEnum.REFUNDING.getStatus()) {
                    continue;//如果返回值是退款中, 不做后续处理
                }
            }
            //退款后续操作
            owner.afterRefund(ordersRefund,executionResultResDTO);
        }

    }

    @Transactional
    public void afterRefund(OrdersRefund ordersRefund,ExecutionResultResDTO executionResultResDTO) {
        //1) 更新订单表中退款相关字段(refund_status 退款状态 refund_no 支付服务退款单号 refund_id 第三方支付的退款单号)
        Orders orders = new Orders();
        orders.setId(ordersRefund.getId());
        orders.setRefundNo(executionResultResDTO.getRefundNo());
        orders.setRefundId(executionResultResDTO.getRefundId());
        orders.setRefundStatus(executionResultResDTO.getRefundStatus());
        boolean b = ordersManagerService.updateById(orders);

        //2) 删除退款表中的数据
        if (b){
            ordersRefundService.removeById(ordersRefund.getId());
        }
    }


    @XxlJob(value = "cancelOverTimePayOrder")
    public void cancelOverTimePayOrder(){
        //1. 查询超时未支付的订单
        //select * from orders where orders_status = 0 and pay_status = 2 and create_time < 当前时间 - 15分钟
        List<Orders> list = ordersManagerService.lambdaQuery()
                .eq(Orders::getOrdersStatus, OrderStatusEnum.NO_PAY.getStatus())//orders_status = 0
                .eq(Orders::getPayStatus, OrderPayStatusEnum.NO_PAY.getStatus())//pay_status = 2
                .lt(Orders::getCreateTime, LocalDateTime.now().minusMinutes(15))//create_time < 当前时间 - 15分钟
                .last("limit 100")//限制每次最多查100条
                .list();
        if (CollUtil.isEmpty(list)){
            return;
        }


        //2. 遍历集合, 获取到每一笔订单
        for (Orders orders : list) {
            //然后去取消
            OrderCancelDTO orderCancelDTO = new OrderCancelDTO();
            orderCancelDTO.setId(orders.getId());//订单id
            orderCancelDTO.setCurrentUserId(0L);//当前用户id
            orderCancelDTO.setCurrentUserName("系统定时任务");//当前用户名称
            orderCancelDTO.setCurrentUserType(UserType.SYSTEM);//当前用户类型
            orderCancelDTO.setCancelReason("超时未支付");//取消原因
            orderCancelStrategyManager.cancel(orderCancelDTO);
        }



    }

}
