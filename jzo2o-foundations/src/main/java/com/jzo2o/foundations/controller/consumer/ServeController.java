package com.jzo2o.foundations.controller.consumer;

import com.jzo2o.foundations.model.dto.response.ServeAggregationSimpleResDTO;
import com.jzo2o.foundations.model.dto.response.ServeCategoryResDTO;
import com.jzo2o.foundations.service.IServeService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController("consumerServeController")
@RequestMapping("/customer/serve")
@Api(tags = "用户端 - 区域相关接口")
public class ServeController {
    @Resource
    private IServeService serveService;

    @GetMapping("/firstPageServeList")
    @ApiOperation("首页服务分类及项目")
    public List<ServeCategoryResDTO> firstPageServeList(Long regionId) {
        return serveService.firstPageServeList(regionId);
    }

    @GetMapping("/hotServeList")
    @ApiOperation("精选推荐")
    public List<ServeAggregationSimpleResDTO> hotServeList(Long regionId) {
        return serveService.hotServeList(regionId);
    }

    @GetMapping("/{id}")
    @ApiOperation("服务详情")
    public ServeAggregationSimpleResDTO serveDetail(@PathVariable("id") Long id) {
        return serveService.serveDetail(id);
    }

}