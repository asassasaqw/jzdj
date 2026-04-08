package com.jzo2o.market.controller.operation;

import com.jzo2o.common.model.PageResult;
import com.jzo2o.market.model.dto.request.ActivityQueryForPageReqDTO;
import com.jzo2o.market.model.dto.request.ActivitySaveReqDTO;
import com.jzo2o.market.model.dto.response.ActivityInfoResDTO;
import com.jzo2o.market.service.IActivityService;
import io.swagger.annotations.ApiOperation;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController("operationActivityController")
@RequestMapping("/operation/activity")
public class ActivityController {

    @Autowired
    private IActivityService activityService;

    @ApiOperation("新增或者修改一个优惠卷活动")
    @PostMapping("/save")
    public void save(@RequestBody ActivitySaveReqDTO reqDTO) {
        activityService.saveOrUpdateActivity(reqDTO);
    }

    @ApiOperation("运营端分页查询活动")
    @GetMapping("/page")
    public PageResult<ActivityInfoResDTO> findByPage(ActivityQueryForPageReqDTO dto) {
        return activityService.findByPage(dto);
    }

    @ApiOperation("查询优惠卷活动详情")
    @GetMapping("/{id}")
    public ActivityInfoResDTO getDetail(@PathVariable("id") Long id) {
        return activityService.findById(id);
    }

    @ApiOperation("活动撤销")
    @PostMapping("/revoke/{id}")
    public void revoke(@PathVariable("id") Long id) {
        activityService.revoke(id);
    }

}
