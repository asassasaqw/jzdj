package com.jzo2o.orders.manager.service.impl;

import   cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jzo2o.api.customer.AddressBookApi;
import com.jzo2o.api.customer.dto.response.AddressBookResDTO;
import com.jzo2o.api.foundations.ServeApi;
import com.jzo2o.api.foundations.dto.response.ServeAggregationResDTO;
import com.jzo2o.api.market.CouponApi;
import com.jzo2o.api.market.dto.request.CouponUseReqDTO;
import com.jzo2o.api.market.dto.response.AvailableCouponsResDTO;
import com.jzo2o.api.market.dto.response.CouponUseResDTO;
import com.jzo2o.api.trade.NativePayApi;
import com.jzo2o.api.trade.TradingApi;
import com.jzo2o.api.trade.dto.request.NativePayReqDTO;
import com.jzo2o.api.trade.dto.response.NativePayResDTO;
import com.jzo2o.api.trade.dto.response.TradingResDTO;
import com.jzo2o.api.trade.enums.PayChannelEnum;
import com.jzo2o.api.trade.enums.TradingStateEnum;
import com.jzo2o.common.expcetions.CommonException;
import com.jzo2o.common.expcetions.ForbiddenOperationException;
import com.jzo2o.common.model.msg.TradeStatusMsg;
import com.jzo2o.common.utils.DateUtils;
import com.jzo2o.common.utils.ObjectUtils;
import com.jzo2o.common.utils.StringUtils;
import com.jzo2o.mvc.utils.UserContext;
import com.jzo2o.orders.base.config.OrderStateMachine;
import com.jzo2o.orders.base.constants.RedisConstants;
import com.jzo2o.orders.base.enums.OrderPayStatusEnum;
import com.jzo2o.orders.base.enums.OrderStatusChangeEventEnum;
import com.jzo2o.orders.base.enums.OrderStatusEnum;
import com.jzo2o.orders.base.mapper.OrdersMapper;
import com.jzo2o.orders.base.model.domain.Orders;
import com.jzo2o.orders.base.model.dto.OrderSnapshotDTO;
import com.jzo2o.orders.base.service.IOrdersDiversionCommonService;
import com.jzo2o.orders.base.service.impl.OrdersDiversionCommonServiceImpl;
import com.jzo2o.orders.manager.model.dto.request.OrdersPayReqDTO;
import com.jzo2o.orders.manager.model.dto.request.PlaceOrderReqDTO;
import com.jzo2o.orders.manager.model.dto.response.OrdersPayResDTO;
import com.jzo2o.orders.manager.model.dto.response.PlaceOrderResDTO;
import com.jzo2o.orders.manager.porperties.TradeProperties;
import com.jzo2o.orders.manager.service.IOrdersCreateService;
import com.jzo2o.redis.annotations.Lock;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static com.jzo2o.common.constants.ErrorInfo.Code.TRADE_FAILED;

/**
 * <p>
 * 下单服务类
 * </p>
 *
 * @author itcast
 * @since 2023-07-10
 */
@Slf4j
@Service
public class OrdersCreateServiceImpl extends ServiceImpl<OrdersMapper, Orders> implements IOrdersCreateService {

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private ServeApi serveApi;

    @Autowired
    private AddressBookApi addressBookApi;
    @Autowired
    private OrdersCreateServiceImpl orderCreateService;

    @Autowired
    private NativePayApi nativePayApi;

    @Autowired
    private TradeProperties tradeProperties;

    @Autowired
    private CouponApi couponApi;

    @Value("${jzo2o.openPay}")
    private Boolean openPay;

    @Autowired
    private IOrdersDiversionCommonService ordersDiversionService;


    @Override
    public PlaceOrderResDTO placeOrder(PlaceOrderReqDTO placeOrderReqDTO) {
        Long userId = UserContext.currentUserId();
        return placeOrder(userId,placeOrderReqDTO);
    }

    @Override
    public OrdersPayResDTO pay(Long id, OrdersPayReqDTO ordersPayReqDTO) {
        //调用支付微服务 获取 二维码图片
        //1. 根据订单id查询订单信息,如果订单不存在, 直接返回错误
        Orders orders = this.getById(id);
        if (ObjectUtil.isNull(orders)) {
            throw new ForbiddenOperationException("订单不存在");
        }

        //2. 查询订单支付状态, 如果是已经支付 , 直接返回错误
        //transaction_id : 只有支付成功,才会有这个号
        if (orders.getPayStatus() == 4 && StringUtils.isNotEmpty(orders.getTransactionId())) {
            throw new ForbiddenOperationException("订单已经支付了");
        }

        //3. 调用支付微服务, 获取二维码
        NativePayReqDTO nativePayReqDTO = new NativePayReqDTO();
        nativePayReqDTO.setProductAppId("jzo2o.orders");//业务系统标识
        nativePayReqDTO.setProductOrderNo(id);//业务系统订单号
        nativePayReqDTO.setTradingChannel(ordersPayReqDTO.getTradingChannel());//支付渠道
        nativePayReqDTO.setTradingAmount(orders.getRealPayAmount());//支付金额
        nativePayReqDTO.setMemo(orders.getServeItemName());//备注

        //根据交易渠道设置商户号
        if (ObjectUtil.equal(ordersPayReqDTO.getTradingChannel(), PayChannelEnum.WECHAT_PAY)) {
            nativePayReqDTO.setEnterpriseId(tradeProperties.getWechatEnterpriseId());//微信商户号
        }
        if (ObjectUtil.equal(ordersPayReqDTO.getTradingChannel(), PayChannelEnum.ALI_PAY)) {
            nativePayReqDTO.setEnterpriseId(tradeProperties.getAliEnterpriseId());//阿里商户号
        }

        //原有的交易渠道不为空 而且跟刚刚传入交易渠道不一样
        if (StringUtils.isNotEmpty(orders.getTradingChannel()) &&
                !StringUtils.equals(orders.getTradingChannel(), ordersPayReqDTO.getTradingChannel().toString())
        ) {
            nativePayReqDTO.setChangeChannel(true);//是否改变交易渠道
        }else {
            nativePayReqDTO.setChangeChannel(false);//是否改变交易渠道
        }
        NativePayResDTO payResDTO = nativePayApi.createDownLineTrading(nativePayReqDTO);

        //4. 更新订单表数据(支付服务交易单号 支付渠道)
        orders.setTradingOrderNo(payResDTO.getTradingOrderNo());//支付服务交易单号
        orders.setTradingChannel(payResDTO.getTradingChannel());//支付渠道
        this.updateById(orders);

        //5. 封装返回结果
        OrdersPayResDTO ordersPayResDTO = BeanUtil.copyProperties(payResDTO, OrdersPayResDTO.class);
        ordersPayResDTO.setPayStatus(2);//支付状态: 未支付

        return ordersPayResDTO;
    }

    @Override
    public List<AvailableCouponsResDTO> getAvailableCoupons(Long serveId, Integer purNum) {
        // 1.获取服务
        ServeAggregationResDTO serveResDTO = serveApi.findById(serveId);
        if (serveResDTO == null || serveResDTO.getSaleStatus() != 2) {
            throw new ForbiddenOperationException("服务不可用");
        }

        // 2.计算订单总金额
        BigDecimal totalAmount = serveResDTO.getPrice().multiply(new BigDecimal(purNum));

        // 3.获取可用优惠券,并返回优惠券列表
        List<AvailableCouponsResDTO> available = couponApi.getAvailable(totalAmount);
        return available;
    }

    @Override
    @GlobalTransactional
    public void saveOrdersWithCoupon(Orders orders, Long couponId) {
        //1. 调用优惠券微服务核销优惠券
        CouponUseReqDTO couponUseReqDTO = new CouponUseReqDTO();
        couponUseReqDTO.setId(couponId);//优惠券id
        couponUseReqDTO.setOrdersId(orders.getId());//订单id
        couponUseReqDTO.setTotalAmount(orders.getTotalAmount());//总金额
        CouponUseResDTO couponUseResDTO = couponApi.use(couponUseReqDTO);

        //2. 修改订单的优惠金额和实付金额
        BigDecimal discountAmount = couponUseResDTO.getDiscountAmount();
        orders.setDiscountAmount(discountAmount);//优惠金额
        orders.setRealPayAmount(orders.getTotalAmount().subtract(discountAmount));//实付金额

        //3. 创建订单
        this.save(orders);

        //重新获取一下订单,并且封装成快照类
        orders = this.getById(orders.getId());
        OrderSnapshotDTO orderSnapshotDTO = BeanUtil.copyProperties(orders, OrderSnapshotDTO.class);

        //启动状态机
        orderStateMachine.start(orders.getId(), orders.getId().toString(),orderSnapshotDTO);
    }

    @Autowired
    private TradingApi tradingApi;
    @Override
    public OrdersPayResDTO getPayResultFromTradServer(Long id) {
        //1.查询订单表
        Orders orders = this.getById(id);
        if (ObjectUtil.isNull(orders)) {
            throw new ForbiddenOperationException("订单不存在");
        }

        //2. 如果订单的支付状态是待支付 并且 支付服务交易单号 不为空  调用支付服务查询订单支付状态
        if (orders.getPayStatus() == 2 && orders.getTradingOrderNo() != null){
            //调用支付服务查询订单支付状态
            TradingResDTO tradingResDTO = tradingApi.findTradResultByTradingOrderNo(orders.getTradingOrderNo());
            //根据支付服务返回的状态修改订单表中字段(订单状态、支付状态、第三方支付交易号)
            //交易状态: 2-付款中 3-付款失败 4-已结算 5-取消订单
            TradingStateEnum tradingState = tradingResDTO.getTradingState();

            if (ObjectUtil.equal(tradingState, TradingStateEnum.YJS)) {
//                // 修改订单状态和支付状态
//                OrderSnapshotDTO orderSnapshotDTO = OrderSnapshotDTO.builder()
//                        .payTime(LocalDateTime.now())
//                        .tradingOrderNo(tradingResDTO.getTradingOrderNo())
//                        .tradingChannel(tradingResDTO.getTradingChannel())
//                        .thirdOrderId(tradingResDTO.getTransactionId())
//                        .build();
                //设置订单的支付状态成功
                TradeStatusMsg msg = TradeStatusMsg.builder()
                        .productOrderNo(orders.getId())
                        .tradingChannel(tradingResDTO.getTradingChannel())
                        .statusCode(TradingStateEnum.YJS.getCode())
                        .tradingOrderNo(tradingResDTO.getTradingOrderNo())
                        .transactionId(tradingResDTO.getTransactionId())
                        .build();
//                orderStateMachine.changeStatus(orders.getId(), orders.getId().toString(), OrderStatusChangeEventEnum.PAYED, orderSnapshotDTO);
                orderCreateService.paySuccess(msg);
            } else {
                //todo 其它情况暂不处理,应该每种都对应一个处理器
            }
        }

        //3. 返回结果
        //查询订单的信息
        Orders newOrders = this.getById(id);
        OrdersPayResDTO ordersPayResDTO = new OrdersPayResDTO();
        ordersPayResDTO.setProductOrderNo(newOrders.getId());//业务系统订单号
        ordersPayResDTO.setTradingOrderNo(newOrders.getTradingOrderNo());//交易系统订单号
        ordersPayResDTO.setTradingChannel(newOrders.getTradingChannel());//支付渠道
        ordersPayResDTO.setPayStatus(newOrders.getPayStatus());//支付状态
        return ordersPayResDTO;
    }

    @Autowired
    private OrdersDiversionCommonServiceImpl orderDiversionService;

    /**
     * 支付成功， 其他信息暂且不填
     *
     * @param tradeStatusMsg 交易状态消息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void paySuccess(TradeStatusMsg tradeStatusMsg) {
//        //查询订单
//        Orders orders = baseMapper.selectById(tradeStatusMsg.getProductOrderNo());
//        if (ObjectUtil.isNull(orders)) {
//            throw new CommonException(TRADE_FAILED, "订单不存在");
//        }
//        //校验支付状态如果不是待支付状态则不作处理
//        if (ObjectUtil.notEqual(OrderPayStatusEnum.NO_PAY.getStatus(), orders.getPayStatus())) {
//            log.info("更新订单支付成功，当前订单:{}支付状态不是待支付状态", orders.getId());
//            return;
//        }
//        //校验订单状态如果不是待支付状态则不作处理
//        if (ObjectUtils.notEqual(OrderStatusEnum.NO_PAY.getStatus(),orders.getOrdersStatus())) {
//            log.info("更新订单支付成功，当前订单:{}状态不是待支付状态", orders.getId());
//            return;
//        }
//
//        //第三方支付单号校验
//        if (ObjectUtil.isEmpty(tradeStatusMsg.getTransactionId())) {
//            log.error("支付成功事件处理失败，缺少第三方支付单号，订单号：{}", orders.getId());
//            return;
//        }
//        //更新订单的支付状态及第三方交易单号等信息
//        boolean update = lambdaUpdate()
//                .eq(Orders::getId, orders.getId())//订单号
//                .eq(Orders::getPayStatus, OrderPayStatusEnum.NO_PAY.getStatus())//原支付状态
//                .eq(Orders::getOrdersStatus, OrderStatusEnum.NO_PAY.getStatus())//原订单状态
//                .set(Orders::getPayTime, LocalDateTime.now())//支付时间
//                .set(Orders::getTradingOrderNo, tradeStatusMsg.getTradingOrderNo())//交易单号
//                .set(Orders::getTradingChannel, tradeStatusMsg.getTradingChannel())//支付渠道
//                .set(Orders::getTransactionId, tradeStatusMsg.getTransactionId())//第三方支付交易号
//                .set(Orders::getPayStatus, OrderPayStatusEnum.PAY_SUCCESS.getStatus())//支付状态
//                .set(Orders::getOrdersStatus, OrderStatusEnum.DISPATCHING.getStatus())//订单状态更新为派单中
//                .update();
//        if(!update){
//            log.info("更新订单:{}支付成功失败", orders.getId());
//            throw new CommonException("更新订单"+orders.getId()+"支付成功失败");
//        }
        //更改为采用状态机实现
        //查询订单
        Orders orders = baseMapper.selectById(tradeStatusMsg.getProductOrderNo());

        //2：待支付，4：支付成功
        if (ObjectUtil.notEqual(OrderPayStatusEnum.NO_PAY.getStatus(), orders.getPayStatus())) {
            log.info("当前订单：{}，不是待支付状态", orders.getId());
            return;
        }

        //第三方支付单号校验
        if (ObjectUtil.isEmpty(tradeStatusMsg.getTransactionId())) {
            throw new CommonException("支付成功通知缺少第三方支付单号");
        }
        // 修改订单状态和支付状态
        OrderSnapshotDTO orderSnapshotDTO = OrderSnapshotDTO.builder()
                .payTime(LocalDateTime.now())
                .tradingOrderNo(tradeStatusMsg.getTradingOrderNo())
                .tradingChannel(tradeStatusMsg.getTradingChannel())
                .thirdOrderId(tradeStatusMsg.getTransactionId())
                .build();
        orderStateMachine.changeStatus( String.valueOf(orders.getId()), OrderStatusChangeEventEnum.PAYED, orderSnapshotDTO);

        // 订单分流
        ordersDiversionService.diversion(orders);
    }

    @Lock(formatter = "lock:orders:placeOrder:{userId}:{serveId}", time = 30,unlock = false)
    public PlaceOrderResDTO placeOrder(Long userId,PlaceOrderReqDTO placeOrderReqDTO) {
        //1. 调用运营微服务, 根据服务id查询
        // 1. 调用运营微服务（加try-catch捕获Feign异常）
        ServeAggregationResDTO serveDto = null;
        try {
            log.info("开始调用 Feign 服务，serveId: {}", placeOrderReqDTO.getServeId());
            serveDto = serveApi.findById(placeOrderReqDTO.getServeId());
            log.info("Feign 调用结果：{}", serveDto != null ? serveDto.getId() : "null");
        } catch (Exception e) {
            log.error("Feign 调用失败", e);
            // Feign 调用失败（404/500/超时等），直接抛业务异常
            throw new ForbiddenOperationException("远程调用失败");
        }

        if (ObjectUtil.isNull(serveDto) || serveDto.getSaleStatus() != 2) {
            throw new ForbiddenOperationException("服务不存在或者状态有误");
        }

        //2. 调用customer微服务, 根据地址id查询信息
        AddressBookResDTO addressDto = addressBookApi.detail(placeOrderReqDTO.getAddressBookId());
        if (ObjectUtil.isNull(addressDto)) {
            throw new ForbiddenOperationException("服务地址有误");
        }

        //3. 准备Orders实体类对象
        Orders orders = new Orders();
        orders.setId(generateOrderId());//订单id
        orders.setUserId(UserContext.currentUserId());//下单人id
        orders.setServeId(placeOrderReqDTO.getServeId());//服务id

        //运营数据微服务
        orders.setServeTypeId(serveDto.getServeTypeId());//服务类型id
        orders.setServeTypeName(serveDto.getServeTypeName());//服务类型名称
        orders.setServeItemId(serveDto.getServeItemId());//服务项id
        orders.setServeItemName(serveDto.getServeItemName());//服务项名称
        orders.setServeItemImg(serveDto.getServeItemImg());//服务项图片
        orders.setUnit(serveDto.getUnit());//服务单位
        orders.setPrice(serveDto.getPrice());//服务单价
        orders.setCityCode(serveDto.getCityCode());//城市编码

        orders.setOrdersStatus(0);//订单状态: 待支付
        orders.setPayStatus(2);//支付状态: 待支付

        orders.setPurNum(placeOrderReqDTO.getPurNum());//购买数量
        orders.setTotalAmount(serveDto.getPrice().multiply(new BigDecimal(placeOrderReqDTO.getPurNum())));//总金额: 价格 * 购买数量
        orders.setDiscountAmount(new BigDecimal(0));//优惠金额
        orders.setRealPayAmount(orders.getTotalAmount().subtract(orders.getDiscountAmount()));//实付金额 订单总金额 - 优惠金额

        //地址
        orders.setServeAddress(addressDto.getAddress());//服务详细地址
        orders.setContactsPhone(addressDto.getPhone());//联系人手机号
        orders.setContactsName(addressDto.getName());//联系人名字
        orders.setLon(addressDto.getLon());//经度
        orders.setLat(addressDto.getLat());//纬度

        orders.setServeStartTime(placeOrderReqDTO.getServeStartTime());//服务开始时间
        orders.setDisplay(1);//用户端是否展示 1 展示
        orders.setSortBy(DateUtils.toEpochMilli(placeOrderReqDTO.getServeStartTime()) + orders.getId() % 100000);//排序字段


        //4. 保存到数据表
        if (ObjectUtil.isNull(placeOrderReqDTO.getCouponId())){
            orderCreateService.saveOrders(orders);
        }else if(ObjectUtil.isNotNull(placeOrderReqDTO.getCouponId())){
            orderCreateService.saveOrdersWithCoupon(orders, placeOrderReqDTO.getCouponId());
        }

        //这里为了方便，直接设置下单就支付成功
        if (Boolean.FALSE.equals(openPay)){
            TradeStatusMsg wechatPay = TradeStatusMsg.builder()
                    .productOrderNo(orders.getId())
                    .tradingChannel("WECHAT_PAY")
                    .statusCode(TradingStateEnum.YJS.getCode())
                    .tradingOrderNo(IdUtil.getSnowflakeNextId())
                    .transactionId(IdUtil.getSnowflakeNextIdStr())
                    .build();
            //调用支付成功处理
            this.paySuccess(wechatPay);
        }
        // 订单分流
//        orderDiversionService.diversion(orders);


        //5.返回
        return new PlaceOrderResDTO(orders.getId());
    }

    @Autowired
    private OrderStateMachine orderStateMachine;
    //新编写一个方法,将需要事务控制事务的方法,提到这个位置来
    @Transactional
    public void saveOrders(Orders orders){

        this.save(orders);

        //重新获取一下订单,并且封装成快照类
        orders = this.getById(orders.getId());
        OrderSnapshotDTO orderSnapshotDTO = BeanUtil.copyProperties(orders, OrderSnapshotDTO.class);

        //启动状态机
        orderStateMachine.start(orders.getId(), orders.getId().toString(),orderSnapshotDTO);

    }

    /**
     * 生成订单id
     *
     * @return 订单id 19位：2位年+2位月+2位日+13位序号(自增)
     */
    private Long generateOrderId() {
        //1. 2位年+2位月+2位日
        Long yyMMdd = DateUtils.getFormatDate(LocalDateTime.now(), "yyMMdd");

        //2. 自增数字  1 2
        Long num = redisTemplate.opsForValue().increment(RedisConstants.Lock.ORDERS_SHARD_KEY_ID_GENERATOR, 1);//1 代表的是每次增长量为1

        //3. 组装返回
        return yyMMdd * 10000000000000L + num;
    }
}
