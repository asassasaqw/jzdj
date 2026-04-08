package com.jzo2o.foundations.controller.inner;

import com.jzo2o.api.foundations.ServeApi;
import com.jzo2o.api.foundations.dto.response.ServeAggregationResDTO;
import com.jzo2o.foundations.service.IServeService;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/inner/serve")
@Api(tags = "内部接口 - 服务相关接口")
public class InnerServeController implements ServeApi {

    @Autowired
    private IServeService serveService;

    @GetMapping("/{id}")
    @Override
    public ServeAggregationResDTO findById(Long id) {
        return serveService.findById(id);
    }
}
