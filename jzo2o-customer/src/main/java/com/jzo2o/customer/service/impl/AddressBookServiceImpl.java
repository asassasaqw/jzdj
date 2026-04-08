package com.jzo2o.customer.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jzo2o.api.customer.dto.response.AddressBookResDTO;
import com.jzo2o.api.publics.MapApi;
import com.jzo2o.api.publics.dto.response.LocationResDTO;
import com.jzo2o.common.model.PageResult;
import com.jzo2o.common.utils.BeanUtils;
import com.jzo2o.common.utils.CollUtils;
import com.jzo2o.common.utils.NumberUtils;
import com.jzo2o.common.utils.StringUtils;
import com.jzo2o.customer.mapper.AddressBookMapper;
import com.jzo2o.customer.model.domain.AddressBook;
import com.jzo2o.customer.model.dto.request.AddressBookPageQueryReqDTO;
import com.jzo2o.customer.model.dto.request.AddressBookUpsertReqDTO;
import com.jzo2o.customer.service.IAddressBookService;
import com.jzo2o.mvc.utils.UserContext;
import com.jzo2o.mysql.utils.PageUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 地址薄 服务实现类
 * </p>
 *
 * @author itcast
 * @since 2023-07-06
 */
@Service
public class AddressBookServiceImpl extends ServiceImpl<AddressBookMapper, AddressBook> implements IAddressBookService {

    @Resource
    private MapApi mapApi;
    @Override
    public List<AddressBookResDTO> getByUserIdAndCity(Long userId, String city) {

        List<AddressBook> addressBooks = lambdaQuery()
                .eq(AddressBook::getUserId, userId)
                .eq(AddressBook::getCity, city)
                .list();
        if(CollUtils.isEmpty(addressBooks)) {
            return new ArrayList<>();
        }
        return BeanUtils.copyToList(addressBooks, AddressBookResDTO.class);
    }

    @Override
    public AddressBookResDTO findDefaultAddress() {
        //用户id  默认地址
        //select * from address_book where user_id = 登录用户 and is_default = 1
        AddressBook addressBook = this.lambdaQuery()
                .eq(AddressBook::getUserId, UserContext.currentUserId())//登录用户id
                .eq(AddressBook::getIsDefault, 1)//默认地址
                .one();
        if (ObjectUtil.isNull(addressBook)){
            return null;
        }

        //转换
        return BeanUtil.copyProperties(addressBook,AddressBookResDTO.class);
    }

    @Override
    public void add(AddressBookUpsertReqDTO addressBookUpsertReqDTO) {
        Long userId = UserContext.currentUserId();

        //先检查添加的地址是否是否是默认地址，如果是默认地址，则将之前默认地址改为非默认
        if (addressBookUpsertReqDTO.getIsDefault() == 1) {
            this.lambdaUpdate().set(AddressBook::getIsDefault, 0)
                    .eq(AddressBook::getUserId, userId).update();
        }

        //准备赋值数据
        AddressBook addressBook = BeanUtils.copyProperties(addressBookUpsertReqDTO, AddressBook.class);
        addressBook.setUserId(userId);

        //如果前端给的经纬度是空，则调用地图接口获取经纬度
        if (StringUtils.isEmpty(addressBookUpsertReqDTO.getLocation())) {
            //组装详细地址
            String completeAddress = addressBookUpsertReqDTO.getProvince() +
                    addressBookUpsertReqDTO.getCity() +
                    addressBookUpsertReqDTO.getCounty() +
                    addressBookUpsertReqDTO.getAddress();
            LocationResDTO locationByAddress = mapApi.getLocationByAddress(completeAddress);
            //经纬度(字符串格式：经度,纬度),经度在前，纬度在后
            String location = locationByAddress.getLocation();
            addressBookUpsertReqDTO.setLocation(location);

            if (StringUtils.isNotEmpty(addressBookUpsertReqDTO.getLocation())) {
                // 经度
                addressBook.setLon(NumberUtils.parseDouble(addressBookUpsertReqDTO.getLocation().split(",")[0]));
                // 纬度
                addressBook.setLat(NumberUtils.parseDouble(addressBookUpsertReqDTO.getLocation().split(",")[1]));
            }
        }
        //4. 执行保存操作
        getBaseMapper().insert(addressBook);
    }

    @Override
    public PageResult<AddressBookResDTO> page(AddressBookPageQueryReqDTO addressBookPageQueryReqDTO) {
        //1. 设置分页参数
        Page<AddressBook> page = PageUtils.parsePageQuery(addressBookPageQueryReqDTO, AddressBook.class);

        //设置查询条件
        LambdaQueryWrapper<AddressBook> wrapper =
                new LambdaQueryWrapper<AddressBook>()
                        .eq(AddressBook::getUserId, UserContext.currentUserId());
        //分页查询
        Page<AddressBook> serveTypePage = baseMapper.selectPage(page, wrapper);


        return PageUtils.toPage(serveTypePage, AddressBookResDTO.class);
    }

    @Override
    public AddressBookResDTO detail(Long id) {
        AddressBook addressBook = baseMapper.selectById(id);
        AddressBookResDTO addressBookResDTO = BeanUtils.copyProperties(addressBook, AddressBookResDTO.class);
        return addressBookResDTO;
    }

    @Override
    public void updateAddressBook(Long id, AddressBookUpsertReqDTO addressBookUpsertReqDTO) {
        //1. 检查默认地址,如果为默认地址，则将之前默认地址改为非默认
        if (addressBookUpsertReqDTO.getIsDefault() == 1) {
            lambdaUpdate().set(AddressBook::getIsDefault, 0)
                    .eq(AddressBook::getUserId, UserContext.currentUserId())
                    .update();
        }

        //2. 拷贝赋值
        AddressBook addressBook = BeanUtil.toBean(addressBookUpsertReqDTO, AddressBook.class);
        addressBook.setId(id);

        //3. 如果请求体中没有经纬度，则调用第三方api根据详细地址获取经纬度
        if (ObjectUtil.isEmpty(addressBookUpsertReqDTO.getLocation())) {
            //组装详细地址
            String completeAddress = addressBookUpsertReqDTO.getProvince() +
                    addressBookUpsertReqDTO.getCity() +
                    addressBookUpsertReqDTO.getCounty() +
                    addressBookUpsertReqDTO.getAddress();
            //远程请求高德获取经纬度
            LocationResDTO locationDto = mapApi.getLocationByAddress(completeAddress);
            //经纬度(字符串格式：经度,纬度),经度在前，纬度在后
            String location = locationDto.getLocation();
            addressBookUpsertReqDTO.setLocation(location);
        }

        if (StringUtils.isNotEmpty(addressBookUpsertReqDTO.getLocation())) {
            // 经度
            addressBook.setLon(NumberUtils.parseDouble(addressBookUpsertReqDTO.getLocation().split(",")[0]));
            // 纬度
            addressBook.setLat(NumberUtils.parseDouble(addressBookUpsertReqDTO.getLocation().split(",")[1]));
        }


        //4. 执行修改
        baseMapper.updateById(addressBook);
    }

    @Override
    public void updateDefaultStatus(Long id, Integer flag) {
        //如果设默认地址，先把其他地址取消默认
        if (1 == flag) {
            this.lambdaUpdate().set(AddressBook::getIsDefault, 0)
                    .eq(AddressBook::getUserId, UserContext.currentUserId())
                    .update();
        }

        //2. 修改
        AddressBook addressBook = new AddressBook();
        addressBook.setId(id);
        addressBook.setIsDefault(flag);
        getBaseMapper().updateById(addressBook);
}
}
