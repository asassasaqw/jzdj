package com.jzo2o.api.customer;

import com.jzo2o.api.customer.dto.response.AddressBookResDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * contextId 指定FeignClient实例的上下文id，如果不设置默认为类名，value指定微服务的名称，path:指定接口地址
 */
@FeignClient(contextId = "jzo2o-customer",value = "jzo2o-customer",path = "/customer/inner/address-book")
public interface AddressBookApi {

    /**
     * 根据地址簿ID获取地址详情信息
     * @return
     */
    @GetMapping("/{id}")
    AddressBookResDTO detail(@PathVariable("id") Long id);
}