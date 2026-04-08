package com.jzo2o.foundations.controller.handler;


import com.jzo2o.canal.listeners.AbstractCanalRabbitMqMsgListener;
import com.jzo2o.es.core.ElasticSearchTemplate;
import com.jzo2o.foundations.constants.IndexConstants;
import com.jzo2o.foundations.model.domain.ServeSync;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

@Component
//本类负责解析MQ中同步的serve_sync这张表的数据,然后写入到ES的serve_aggregation索引库
//后面相似的类有很多,例如order_sync、user_sync, 每个类中所做事情的步骤是一样的
//1. 解析MQ中的消息内容,封装到实体类对象(所有类中的这部分代码是一样的)
//2. 将实体类对象的数据同步到ES索引库中(每个类中这部分代码是不一样的)
public class ServeCanalDataSyncHandler extends AbstractCanalRabbitMqMsgListener<ServeSync> {

    @Resource
    private ElasticSearchTemplate elasticSearchTemplate;

    //此方法会接收到到来自mq的消息,然后按照解析消息、同步索引库的步骤执行
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "canal-mq-jzo2o-foundations",
                    arguments = {
                            @Argument(name = "x-single-active-consumer", value = "true", type = "java.lang.Boolean")
                    }),
            exchange = @Exchange(name = "exchange.canal-jzo2o", type = ExchangeTypes.TOPIC),
            key = "canal-mq-jzo2o-foundations")
    )
    public void onMessage(Message message) throws Exception {
        //由于解析逻辑,所有子类的逻辑是一样的,索引自己放到父类中,此处直接调用父类的解析方法
        parseMsg(message);
    }
    //由于不同的子类实现逻辑不一样,因此每个子类自己实现此方法
    @Override
    public void batchSave(List<ServeSync> data) {
        //保存数据到ES指定的索引库
        elasticSearchTemplate.opsForDoc().batchInsert(IndexConstants.SERVE, data);
    }

    //由于不同的子类实现逻辑不一样,因此每个子类自己实现此方法
    @Override
    public void batchDelete(List<Long> ids) {
        //删除ES指定的索引库中的数据
        elasticSearchTemplate.opsForDoc().batchDelete(IndexConstants.SERVE, ids);
    }
}
