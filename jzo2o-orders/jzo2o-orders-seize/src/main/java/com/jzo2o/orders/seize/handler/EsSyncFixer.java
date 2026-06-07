package com.jzo2o.orders.seize.handler;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.jzo2o.common.expcetions.ForbiddenOperationException;
import com.jzo2o.orders.base.model.domain.OrdersSeize;
import com.jzo2o.orders.seize.service.IOrdersSeizeService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.jzo2o.orders.base.constants.EsIndexConstants.ORDERS_SEIZE;

/**
 * ES 数据一致性修复工具
 * 功能：
 * 1. 删除 ES 多余数据
 * 2. 补充 ES 缺少的数据
 * 3. 保证 MySQL ↔ ES 完全一致
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EsSyncFixer {

    private final RestHighLevelClient client;

    @Autowired
    private IOrdersSeizeService ordersSeizeService;




    /**
     * 对数据库和ES中数据进行一致性修复
     */
    @XxlJob(value = "EsSyncFixerForSeize")
    public void fix() {

        log.info("====== ES 一致性修复开始 ======");
        //获取数据库中所有serve_sync的ID
        List<Long> dbIds = getAllIdsFromDb();
        //获取ES中所有serve_sync的ID
        List<Long> esIds = getAllIdsFromEs();
        //转为 Set，提高查询效率
        Set<Long> dbIdSet = new HashSet<>(dbIds);
        Set<Long> esIdSet = new HashSet<>(esIds);

        //找出ES中多余的数据
        List<Long> collect = esIdSet.stream().filter(id -> !dbIdSet.contains(id)).collect(Collectors.toList());
        if (CollUtil.isNotEmpty(collect)){
            // 1. 构建批量删除请求
            BulkRequest bulkRequest = new BulkRequest();
            for (Long id : collect) {
                // 删除请求：指定 索引名 + 文档ID
                DeleteRequest deleteRequest = new DeleteRequest(ORDERS_SEIZE, String.valueOf(id));
                bulkRequest.add(deleteRequest);
            }
            // 2. 执行批量删除
            try {
                client.bulk(bulkRequest, RequestOptions.DEFAULT);
            } catch (IOException e) {
                throw new ForbiddenOperationException("删除ES数据异常");
            }
        }

        //找出ES中缺少的数据
        List<Long> collect1 = dbIdSet.stream().filter(id -> !esIdSet.contains(id)).collect(Collectors.toList());

        log.info("需要删除：{}条", collect.size());
        log.info("需要新增：{}条", collect1.size());

        if (CollUtil.isNotEmpty(collect1)){
            BulkRequest bulkRequest = new BulkRequest();

            for (Long id : collect1) {
                // 索引请求：指定索引名 + 文档ID + 文档内容
                IndexRequest indexRequest = new IndexRequest(ORDERS_SEIZE, String.valueOf(id));
                // 获取数据库中serve_sync数据
                OrdersSeize serveSync = ordersSeizeService.getById(id);
                if (serveSync == null){
                    continue;
                }
                // 封装成ES文档
                indexRequest.source(JSONUtil.toJsonStr(serveSync), XContentType.JSON);
                // 添加到批量请求中
                bulkRequest.add(indexRequest);
            }

            try {
                client.bulk(bulkRequest, RequestOptions.DEFAULT);
            } catch (IOException e) {
                throw new ForbiddenOperationException("添加ES数据异常");
            }
        }

        log.info("====== ES和数据库数据更新 ======");
        List<Long> upData = dbIdSet.stream().filter(id -> esIdSet.contains(id)).collect(Collectors.toList());

        if (CollUtil.isNotEmpty(upData)) {
            log.info("需要更新（覆盖）ES数据：{} 条", upData.size());
            BulkRequest bulkRequest = new BulkRequest();
            for (Long id : upData) {
                OrdersSeize serveSync = ordersSeizeService.getById(id);
                if (serveSync == null) continue;
                IndexRequest indexRequest = new IndexRequest(ORDERS_SEIZE, String.valueOf(id));
                indexRequest.source(JSONUtil.toJsonStr(serveSync), XContentType.JSON);
                bulkRequest.add(indexRequest);
            }

            try {
                client.bulk(bulkRequest, RequestOptions.DEFAULT);
                log.info("ES数据覆盖更新完成：{} 条", upData.size());
            } catch (IOException e) {
                throw new ForbiddenOperationException("更新ES数据异常");
            }
        }


    }







    //获取数据库中所有serve_sync的ID
    public List<Long> getAllIdsFromDb(){
        return ordersSeizeService.lambdaQuery()
                .select(OrdersSeize::getId) // 只查ID字段
                .list()                   // 返回 List<ServeSync>
                .stream()
                .map(OrdersSeize::getId)    // 抽取 ID → 变成 List<Long>
                .collect(Collectors.toList());
    }

    //获取ES中所有serve_sync的ID
    public List<Long> getAllIdsFromEs(){
        //1.封装请求参数
        SearchRequest serveAggregation = new SearchRequest("orders_seize");
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

        //2.查询所有
        searchSourceBuilder.query(QueryBuilders.matchAllQuery());
        // 只查ID，不查数据（超快）
        searchSourceBuilder.fetchSource(false);
        // 最多查10000条（足够绝大多数场景）
        searchSourceBuilder.size(10000);

        serveAggregation.source(searchSourceBuilder);

        SearchResponse search=null;
        try {
            search = client.search(serveAggregation, RequestOptions.DEFAULT);
        } catch (IOException e) {
            e.printStackTrace();
            throw new ForbiddenOperationException("查询ES数据异常");
        }
        //3.解析结果
        SearchHit[] hits = search.getHits().getHits();
        List<Long> idList = new ArrayList<>();
        for (SearchHit hit : hits) {
            idList.add(Long.valueOf(hit.getId()));
        }

        return idList;

    }
}