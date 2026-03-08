package com.jzo2o.foundations.controller.operation;

import com.jzo2o.api.foundations.dto.response.ServeTypeSimpleResDTO;
import com.jzo2o.common.model.PageResult;
import com.jzo2o.common.model.Result;
import com.jzo2o.foundations.model.domain.Serve;
import com.jzo2o.foundations.model.dto.request.ServePageQueryReqDTO;
import com.jzo2o.foundations.model.dto.request.ServeTypePageQueryReqDTO;
import com.jzo2o.foundations.model.dto.request.ServeTypeUpsertReqDTO;
import com.jzo2o.foundations.model.dto.request.ServeUpsertReqDTO;
import com.jzo2o.foundations.model.dto.response.ServeResDTO;
import com.jzo2o.foundations.model.dto.response.ServeTypeResDTO;
import com.jzo2o.foundations.service.IServeService;
import com.jzo2o.foundations.service.IServeTypeService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.apache.ibatis.annotations.Delete;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * 服务类型相关接口
 *
 * @author itcast
 * @create 2023/7/26 14:16
 **/
@RestController("serveController")
@RequestMapping("/operation/serve")
@Api(tags = "运营端 - 区域服务相关接口")
public class ServeController {
    @Autowired
    private IServeService serveService;

    @GetMapping("/page")
    @ApiOperation("区域服务分页查询")
    public PageResult<ServeResDTO> page(ServePageQueryReqDTO servePageQueryReqDTO) {
        return serveService.pagefindByPage(servePageQueryReqDTO);
    }

    @PostMapping("/batch")
    @ApiOperation("区域服务批量保存")
    public Result add(@RequestBody List<ServeUpsertReqDTO> dtoList) {
        serveService.add(dtoList);
        return Result.ok();
    }

    @PutMapping("/{id}")
    @ApiOperation("区域服务价格更新")
    public Result upData(@PathVariable("id") Long id, BigDecimal price){
        serveService.updatePrice(id,price);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @ApiOperation("区域服务删除")
    public Result delete(@PathVariable("id") Long id){
        serveService.removeByServerId(id);
        return Result.ok();
    }

    @PutMapping("/onSale/{id}")
    @ApiOperation("区域服务上架")
    public Result onSale(@PathVariable("id") Long id){
        serveService.updateOnSale(id);
        return Result.ok();
    }

    @PutMapping("/offSale/{id}")
    @ApiOperation("区域服务下架")
    public Result offSale(@PathVariable("id") Long id){
        serveService.updateOffSale(id);
        return Result.ok();
    }

    @PutMapping("/onHot/{id}")
    @ApiOperation("区域服务设为热门")
    public Result onHot(@PathVariable("id") Long id){
        Serve serve = new Serve();
        serve.setId(id);
        serve.setIsHot(1);
        serveService.updateById( serve);
        return Result.ok();
    }

    @PutMapping("/offHot/{id}")
    @ApiOperation("区域服务取消热门")
    public Result offHot(@PathVariable("id") Long id){
        Serve serve = new Serve();
        serve.setId(id);
        serve.setIsHot(0);
        serveService.updateById( serve);
        return Result.ok();
    }

}
