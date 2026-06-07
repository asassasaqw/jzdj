package com.jzo2o.foundations.controller.consumer;

import com.jzo2o.foundations.model.dto.response.ServeAggregationSimpleResDTO;
import com.jzo2o.foundations.model.dto.response.ServeAggregationTypeSimpleResDTO;
import com.jzo2o.foundations.model.dto.response.ServeCategoryResDTO;
import com.jzo2o.foundations.model.dto.response.ServeSimpleResDTO;
import com.jzo2o.foundations.service.IServeService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

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

//    @GetMapping("/search")
//    @ApiOperation("服务搜索")
//    public List<ServeAggregationSimpleResDTO> search(Integer cityCode,Long regionId) {
//        return null;
//    }
//


    @GetMapping("/serveTypeList")
    @ApiOperation("查询当前区域下上架服务对应的分类")
    public List<ServeAggregationTypeSimpleResDTO> serveTypeList(Long regionId) {
        return serveService.serveTypeList(regionId);
    }

    @GetMapping("/search")
    @ApiOperation("服务搜索")
    public List<ServeSimpleResDTO> search(String cityCode, String  keyword, Long serveTypeId) {
        return serveService.search(cityCode, keyword, serveTypeId);
    }


//    @GetMapping("/search")
//    @ApiOperation("首页服务搜索")
//    @ApiImplicitParams({
//            @ApiImplicitParam(name = "cityCode", value = "城市编码", required = true, dataTypeClass = String.class),
//            @ApiImplicitParam(name = "serveTypeId", value = "服务类型id", dataTypeClass = Long.class),
//            @ApiImplicitParam(name = "keyword", value = "关键词", dataTypeClass = String.class)
//    })
//    public List<ServeSimpleResDTO> findServeList(@RequestParam("cityCode") String cityCode,
//                                                 @RequestParam(value = "serveTypeId", required = false) Long serveTypeId,
//                                                 @RequestParam(value = "keyword", required = false) String keyword) {
//
//        return null;
//    }

}