package com.jzo2o.foundations.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jzo2o.common.expcetions.ForbiddenOperationException;
import com.jzo2o.common.model.PageResult;
import com.jzo2o.common.utils.ObjectUtils;
import com.jzo2o.foundations.mapper.ServeItemMapper;
import com.jzo2o.foundations.mapper.ServeMapper;
import com.jzo2o.foundations.model.domain.Region;
import com.jzo2o.foundations.model.domain.Serve;
import com.jzo2o.foundations.model.domain.ServeItem;
import com.jzo2o.foundations.model.dto.request.ServePageQueryReqDTO;
import com.jzo2o.foundations.model.dto.request.ServeUpsertReqDTO;
import com.jzo2o.foundations.model.dto.response.ServeAggregationSimpleResDTO;
import com.jzo2o.foundations.model.dto.response.ServeCategoryResDTO;
import com.jzo2o.foundations.model.dto.response.ServeResDTO;
import com.jzo2o.foundations.service.IServeService;
import com.jzo2o.mysql.utils.PageHelperUtils;
import com.jzo2o.mysql.utils.PageUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.jzo2o.foundations.mapper.RegionMapper;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static com.jzo2o.foundations.enums.FoundationStatusEnum.ENABLE;

@Service
public class ServeServiceImpl extends ServiceImpl<ServeMapper, Serve> implements IServeService {

    @Resource
    private ServeItemMapper serveItemMapper;
    @Resource
    private RegionMapper regionMapper;
    @Autowired
    private ServeMapper serveMapper;


    @Override
    public PageResult<ServeResDTO> pagefindByPage(ServePageQueryReqDTO servePageQueryReqDTO) {
        return PageHelperUtils.selectPage(servePageQueryReqDTO,
                () -> baseMapper.queryListByRegionId(servePageQueryReqDTO.getRegionId()));
    }

    @Override
    @Transactional
    public void add(List<ServeUpsertReqDTO> dtoList) {

        for (ServeUpsertReqDTO dto : dtoList) {
            //1.查看是否是启动状态
            ServeItem serveItem = serveItemMapper.selectById(dto.getServeItemId());
            if (ObjectUtils.isNull(serveItem )|| serveItem.getActiveStatus() !=2){
                throw new ForbiddenOperationException("添加失败,服务项目状态有误");
            }

            //2.查看是否已经存在
            Integer count = this.lambdaQuery().eq(Serve::getServeItemId, dto.getServeItemId())
                    .eq(Serve::getRegionId, dto.getRegionId())
                    .count();
            if (count > 0) {
                throw new ForbiddenOperationException("添加失败,该服务已存在");
            }

            //3.添加
            Serve serve = BeanUtil.copyProperties(dto, Serve.class);
            Region region = regionMapper.selectById(dto.getRegionId());
            if (ObjectUtil.isNotNull(region)){
                serve.setCityCode(region.getCityCode());
            }
            baseMapper.insert(serve);
        }

    }

    @Override
    public void updatePrice(Long id, BigDecimal price) {
        this.lambdaUpdate().eq(Serve::getId, id).set(Serve::getPrice, price).update();
    }

    @Override
    public void removeByServerId(Long id) {
        //检查是否是草稿状态
        Serve serve = this.getById(id);
        if (serve.getSaleStatus() != 0  ) {
            throw new ForbiddenOperationException("删除失败,服务状态有误");
        }
        baseMapper.deleteById(id);
    }

    @Override
    public void updateOnSale(Long id) {
        //1.检查区域服务是否是非上架状态
        Integer saleStatus = this.baseMapper.selectById(id).getSaleStatus();
        if (saleStatus == 2) {
            throw new ForbiddenOperationException("上架失败,服务状态有误");
        }
        //2.检查服务项目是否是启动状态
        ServeItem serveItem = serveItemMapper.selectById(this.baseMapper.selectById(id).getServeItemId());
        if (serveItem.getActiveStatus() != 2) {
            throw new ForbiddenOperationException("上架失败,服务项目状态有误");
        }

        //3.修改
        this.lambdaUpdate().eq(Serve::getId, id).set(Serve::getSaleStatus, 2).update();
    }

    @Override
    public void updateOffSale(Long id) {
        //1.检查区域服务是否是上架状态
        Integer saleStatus = this.baseMapper.selectById(id).getSaleStatus();
        if (saleStatus != 2) {
            throw new ForbiddenOperationException("下架失败,服务状态有误");
        }
        //2.修改
        this.lambdaUpdate().eq(Serve::getId, id).set(Serve::getSaleStatus, 1).update();
    }

    @Override
    public List<ServeCategoryResDTO> firstPageServeList(Long regionId) {
        //1.先验证区域是否是启用状态
        Region region = regionMapper.selectById(regionId);
        if (ObjectUtils.isNull(region)|| region.getActiveStatus() != 2) {
            throw new ForbiddenOperationException("服务查询失败,区域状态有误");
        }

        //2. 查询指定区域下上架的服务分类及项目信息
        List<ServeCategoryResDTO> list = baseMapper.findListByRegionId(regionId);
        if (CollUtil.isEmpty(list)) {
            return Collections.emptyList();
        }

        //3. 截取
        list = CollUtil.sub(list, 0, Math.min(list.size(), 2));//服务类型截取
        list.forEach(e -> {
            //服务项目截取
            if (CollUtil.isNotEmpty(e.getServeResDTOList())) {
                e.setServeResDTOList(CollUtil.sub(e.getServeResDTOList(), 0, Math.min(e.getServeResDTOList().size(), 4)));
            }
        });
        return list;

    }

    @Override
    public List<ServeAggregationSimpleResDTO> hotServeList(Long regionId) {
        //1 对区域进行校验
        Region region = regionMapper.selectById(regionId);
        if (ObjectUtil.isNull(region) || region.getActiveStatus() != 2) {
            return Collections.emptyList();
        }

        //2 查询指定区域下上架且热门的服务项目信息
        return baseMapper.findServeListByRegionId(regionId);
    }

    @Override
    public ServeAggregationSimpleResDTO serveDetail(Long id) {
        //这里不使用联表查询，使用分部查询
        Serve serve = baseMapper.selectById(id);
        if (ObjectUtil.isNull(serve)) {
           throw new ForbiddenOperationException("服务详情查询失败,服务不存在");
        }
        Long serveItemId = serve.getServeItemId();

        ServeItem serveItem = serveItemMapper.selectById(serveItemId);
        if (ObjectUtil.isNull(serveItem)) {
            throw new ForbiddenOperationException("服务详情查询失败,服务项目不存在");
        }

        //3. 将两部分内容组装成返回结果
        ServeAggregationSimpleResDTO dto = BeanUtil.copyProperties(serve, ServeAggregationSimpleResDTO.class);
        dto.setServeItemName(serveItem.getName());
        dto.setServeItemImg(serveItem.getImg());
        dto.setDetailImg(serveItem.getDetailImg());
        dto.setUnit(serveItem.getUnit());

        return dto;
    }
}
