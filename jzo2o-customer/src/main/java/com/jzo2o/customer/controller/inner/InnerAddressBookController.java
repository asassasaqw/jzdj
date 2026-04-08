package com.jzo2o.customer.controller.inner;


import com.jzo2o.api.customer.dto.response.AddressBookResDTO;
import com.jzo2o.customer.service.impl.AddressBookServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/inner/address-book")
@RestController
@Slf4j
public class InnerAddressBookController {
    @Autowired
    private AddressBookServiceImpl addressBookService;
    /**
     * 根据地址簿ID获取地址详情信息
     * @return
     */
    @GetMapping("/{id}")
    AddressBookResDTO detail(@PathVariable("id") Long id){
        return addressBookService.detail(id);
    }

}

