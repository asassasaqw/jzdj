package com.jzo2o.foundations.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jzo2o.common.model.PageResult;
import com.jzo2o.foundations.model.domain.Serve;
import com.jzo2o.foundations.model.dto.request.ServePageQueryReqDTO;
import com.jzo2o.foundations.model.dto.request.ServeUpsertReqDTO;
import com.jzo2o.foundations.model.dto.response.ServeAggregationSimpleResDTO;
import com.jzo2o.foundations.model.dto.response.ServeCategoryResDTO;
import com.jzo2o.foundations.model.dto.response.ServeResDTO;

import java.math.BigDecimal;
import java.util.List;

public interface IServeService extends IService<Serve> {
    /**
     * 区域服务分页查询
     *
     * @param servePageQueryReqDTO 区域服务分页查询参数
     * @return 区域服务分页查询结果
     */
    PageResult<ServeResDTO> pagefindByPage(ServePageQueryReqDTO servePageQueryReqDTO);

    /**
     * 批量新增区域服务
     *
     * @param dtoList 区域服务列表
     */
    void add(List<ServeUpsertReqDTO> dtoList);

    /**
     * 修改区域服务价格
     *
     * @param id 区域服务id
     * @param price 区域服务价格
     */
    void updatePrice(Long id, BigDecimal price);

    /**
     * 删除区域服务
     *
     * @param id 区域服务id
     */
    void removeByServerId(Long id);

    void updateOnSale(Long id);

    void updateOffSale(Long id);

    /**
     * 查询指定区域下上架的服务分类及项目信息
     *
     * @param regionId 区域id
     * @return 服务分类及项目信息
     */
    List<ServeCategoryResDTO> firstPageServeList(Long regionId);

    List<ServeAggregationSimpleResDTO> hotServeList(Long regionId);

    ServeAggregationSimpleResDTO serveDetail(Long id);
}
