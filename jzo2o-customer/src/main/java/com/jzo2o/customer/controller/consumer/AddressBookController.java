package com.jzo2o.customer.controller.consumer;

import com.jzo2o.api.customer.dto.response.AddressBookResDTO;
import com.jzo2o.common.model.PageResult;

import com.jzo2o.customer.model.dto.request.AddressBookPageQueryReqDTO;
import com.jzo2o.customer.model.dto.request.AddressBookUpsertReqDTO;
import com.jzo2o.customer.service.IAddressBookService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController("consumerAddressBookController")
@RequestMapping("/consumer/address-book")
@Api(tags = "用户端 - 用户地址相关接口")
public class AddressBookController {

    @Autowired
    private IAddressBookService addressBookService;

    @GetMapping("/defaultAddress")
    @ApiOperation("查询用户默认地址值")
    public AddressBookResDTO findDefaultAddress(){
        return addressBookService.findDefaultAddress();
    }

    @PostMapping
    @ApiOperation("保存用户地址")
    public void addressBook(@RequestBody AddressBookUpsertReqDTO addressBookUpsertReqDTO){
        addressBookService.add(addressBookUpsertReqDTO);
    }

    @GetMapping("/page")
    @ApiOperation("查询用户地址列表")
    public  PageResult<AddressBookResDTO> page(AddressBookPageQueryReqDTO addressBookPageQueryReqDTO){
        return addressBookService.page(addressBookPageQueryReqDTO);
    }

    @GetMapping("/{id}")
    @ApiOperation("地址薄详情")
    public AddressBookResDTO detail(@PathVariable Long id){
        return addressBookService.detail(id);
    }

    @PutMapping("/{id}")
    @ApiOperation("修改地址薄")
    public void update(@PathVariable Long id, @RequestBody AddressBookUpsertReqDTO addressBookUpsertReqDTO){
        addressBookService.updateAddressBook(id,addressBookUpsertReqDTO);
    }

    @DeleteMapping("/batch")
    @ApiOperation("批量删除地址薄")
    public void delete(@RequestBody List<Long> ids){
        addressBookService.removeByIds(ids);
    }

    @PutMapping("/default")
    @ApiOperation("设置默认地址")
    public void setDefault(Long id,Integer flag){
        addressBookService.updateDefaultStatus(id, flag);
    }

}