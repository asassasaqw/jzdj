package com.jzo2o.orders.manager.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jzo2o.api.orders.dto.request.OrderCancelReqDTO;
import com.jzo2o.api.orders.dto.response.OrderResDTO;
import com.jzo2o.api.orders.dto.response.OrderSimpleResDTO;
import com.jzo2o.common.enums.EnableStatusEnum;
import com.jzo2o.common.expcetions.ForbiddenOperationException;
import com.jzo2o.common.utils.BeanUtils;
import com.jzo2o.common.utils.ObjectUtils;
import com.jzo2o.orders.base.config.OrderStateMachine;
import com.jzo2o.orders.base.enums.OrderRefundStatusEnum;
import com.jzo2o.orders.base.enums.OrderStatusEnum;
import com.jzo2o.orders.base.mapper.OrdersCanceledMapper;
import com.jzo2o.orders.base.mapper.OrdersMapper;
import com.jzo2o.orders.base.mapper.OrdersRefundMapper;
import com.jzo2o.orders.base.model.domain.Orders;
import com.jzo2o.orders.base.model.domain.OrdersCanceled;
import com.jzo2o.orders.base.model.domain.OrdersRefund;
import com.jzo2o.orders.base.model.dto.OrderSnapshotDTO;
import com.jzo2o.orders.base.model.dto.OrderUpdateStatusDTO;
import com.jzo2o.orders.base.service.IOrdersCommonService;
import com.jzo2o.orders.manager.model.dto.OrderCancelDTO;
import com.jzo2o.orders.manager.service.IOrdersManagerService;
import com.jzo2o.orders.manager.strategy.OrderCancelStrategyManager;
import com.jzo2o.redis.helper.CacheHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.jzo2o.orders.base.constants.FieldConstants.SORT_BY;
import static com.jzo2o.orders.base.constants.RedisConstants.RedisKey.ORDERS;

/**
 * <p>
 * 订单表 服务实现类
 * </p>
 *
 * @author itcast
 * @since 2023-07-10
 */
@Slf4j
@Service
public class OrdersManagerServiceImpl extends ServiceImpl<OrdersMapper, Orders> implements IOrdersManagerService {

    private IOrdersManagerService owner;

    @Autowired
    private IOrdersCommonService ordersCommonService;

    @Autowired
    private OrdersCanceledMapper ordersCanceledMapper;

    @Autowired
    private OrdersRefundMapper ordersRefundMapper;

    @Autowired
    private OrderCancelStrategyManager orderCancelStrategyManager;

    @Override
    public List<Orders> batchQuery(List<Long> ids) {
        LambdaQueryWrapper<Orders> queryWrapper = Wrappers.<Orders>lambdaQuery().in(Orders::getId, ids).ge(Orders::getUserId, 0);
        return baseMapper.selectList(queryWrapper);
    }

    @Override
    public Orders queryById(Long id) {
        return baseMapper.selectById(id);
    }

    @Autowired
    private CacheHelper cacheHelper;
    /**
     * 滚动分页查询
     *
     * @param currentUserId 当前用户id
     * @param ordersStatus  订单状态，0：待支付，100：派单中，200：待服务，300：服务中，400：待评价，500：订单完成，600：已取消，700：已关闭
     * @param sortBy        排序字段
     * @return 订单列表
     */
    @Override
    public List<OrderSimpleResDTO> consumerQueryList(Long currentUserId, Integer ordersStatus, Long sortBy) {
        //-- 1. 先根据查询条件拿到订单的id集合(走非聚集索引,但是不会回表)
        List<Orders> list = this.lambdaQuery()
                .select(Orders::getId)//select id from orders
                .eq(Orders::getUserId, currentUserId)//where user_id = 登录用户id
                .eq(ordersStatus != null, Orders::getOrdersStatus, ordersStatus)//and orders_status = 订单状态（非必传）
                .eq(Orders::getDisplay, EnableStatusEnum.ENABLE.getStatus())//and display = 1
                .lt(sortBy != null, Orders::getSortBy, sortBy)//and sort_by < 上次查询最后一条记录的sort_by值
                .orderByDesc(Orders::getSortBy)//order by sort_by desc
                .last("limit 10")//limit 10
                .list();
        if (CollUtil.isEmpty(list)){
            return List.of();
        }

        //-- 2. 根据id集合再去查询订单信息(走聚集索引,不会回表)
        //select * from orders where id in (上面拿到的id集合)
        List<Long> orderIdList = list.stream().map(Orders::getId).collect(Collectors.toList());
        //缓存key   ORDERS:PAGE_QUERY:PAGE_用户id
        String redisKey = String.format(ORDERS, currentUserId);
        //方法(当查询的数据在缓存中没有的情况下, 调用你提供的这个方法来获取数据)
        CacheHelper.BatchDataQueryExecutor<Long, OrderSimpleResDTO> batchDataQueryExecutor=
                (ids, clazz) -> {
                    List<Orders> orders = baseMapper.selectBatchIds(ids);
                //查询不到
                if (CollUtil.isEmpty(orders)) {
                    return Map.of();
                }
                //查询到了List<Orders> ordersList -- Map<订单id,订单对象>
                    return orders.stream().collect(Collectors.toMap(
                            e -> e.getId(),
                            e -> BeanUtil.toBean(e, OrderSimpleResDTO.class)
                    ));
                };

        List<OrderSimpleResDTO> orderSimpleResDTOS = cacheHelper.batchGet(redisKey, orderIdList, batchDataQueryExecutor, OrderSimpleResDTO.class, 600L);

        //3. 结果转换封装
        return orderSimpleResDTOS;

    }

    @Autowired
    private OrderStateMachine orderStateMachine;
    /**
     * 根据订单id查询
     *
     * @param id 订单id
     * @return 订单详情
     */
    @Override
    public OrderResDTO getDetail(Long id) {
//      Orders orders = queryById(id);
//      OrderResDTO orderResDTO = BeanUtil.toBean(orders, OrderResDTO.class);
        //改为从订单快照类中查询
        String json = orderStateMachine.getCurrentSnapshotCache(id.toString());
        //将数据转换为快照对象
        OrderSnapshotDTO orderSnapshotDTO = JSON.parseObject(json, OrderSnapshotDTO.class);
        //将快照对象转换为返回值对象
        OrderResDTO orderResDTO = BeanUtil.toBean(orderSnapshotDTO, OrderResDTO.class);
        return orderResDTO;
    }

    private Orders canalIfPayOvertime(Orders orders) {
        //订单支付超时
        if(orders.getOrdersStatus().equals(OrderStatusEnum.NO_PAY.getStatus())&&orders.getCreateTime().isBefore(LocalDateTime.now().plusMinutes(15))){
            OrderCancelDTO orderCancelDTO=new OrderCancelDTO();
            orderCancelDTO.setId(orders.getId());
            orderCancelDTO.setCancelReason("订单支付超时系统自动取消");
            owner.cancel(orderCancelDTO);
            //查询最新的订单信息
            orders = queryById(orders.getId());
        }
        return orders;
    }



    /**
     * 订单评价
     *
     * @param ordersId 订单id
     */
    @Override
    @Transactional
    public void evaluationOrder(Long ordersId) {
//        //查询订单详情
//        Orders orders = queryById(ordersId);
//
//        //构建订单快照
//        OrderSnapshotDTO orderSnapshotDTO = OrderSnapshotDTO.builder()
//                .evaluationTime(LocalDateTime.now())
//                .build();
//
//        //订单状态变更
//        orderStateMachine.changeStatus(orders.getUserId(), orders.getId().toString(), OrderStatusChangeEventEnum.EVALUATE, orderSnapshotDTO);
    }

    @Override
    public void cancel(OrderCancelDTO orderCancelDTO) {
        //调用取消订单策略管理器处理取消订单
        orderCancelStrategyManager.cancel(orderCancelDTO);
    }

}
