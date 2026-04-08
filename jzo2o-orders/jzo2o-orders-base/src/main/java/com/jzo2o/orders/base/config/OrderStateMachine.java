package com.jzo2o.orders.base.config;

import com.jzo2o.orders.base.enums.OrderStatusEnum;
import com.jzo2o.orders.base.model.dto.OrderSnapshotDTO;
import com.jzo2o.orders.base.service.IHistoryOrdersSyncCommonService;
import com.jzo2o.statemachine.AbstractStateMachine;
import com.jzo2o.statemachine.persist.StateMachinePersister;
import com.jzo2o.statemachine.snapshot.BizSnapshotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 订单状态机
 */
@Component
public class OrderStateMachine extends AbstractStateMachine<OrderSnapshotDTO> {


    @Resource
    private IHistoryOrdersSyncCommonService historyOrdersSyncService;

    public OrderStateMachine(StateMachinePersister stateMachinePersister, BizSnapshotService bizSnapshotService, RedisTemplate redisTemplate) {
        super(stateMachinePersister, bizSnapshotService, redisTemplate);
    }

    /**
     * 设置状态机名称
     *
     * @return 状态机名称
     */
    @Override
    protected String getName() {
        return "order";
    }

    /**
     * 设置状态机初始状态
     *
     * @return 状态机初始状态
     */
    @Override
    protected OrderStatusEnum getInitState() {
        return OrderStatusEnum.NO_PAY;
    }


    /**
     * 后置处理器 订单创建之后要做的操作,暂时啥也不做
     *
     * @param orderSnapshotDTO 订单快照
     */
    @Override
    protected void postProcessor(OrderSnapshotDTO orderSnapshotDTO) {
        /***************************完成、关闭、取消订单写历史订单同步表*******************************/
        //取出订单的新状态
        Integer ordersStatus = orderSnapshotDTO.getOrdersStatus();
        if(OrderStatusEnum.FINISHED.getStatus().equals(ordersStatus) ||
                OrderStatusEnum.CLOSED.getStatus().equals(ordersStatus) ||
                OrderStatusEnum.CANCELED.getStatus().equals(ordersStatus) ){
            historyOrdersSyncService.writeHistorySync(orderSnapshotDTO.getId());
        }
    }
}