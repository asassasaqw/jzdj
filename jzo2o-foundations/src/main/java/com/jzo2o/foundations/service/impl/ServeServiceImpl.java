package com.jzo2o.foundations.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jzo2o.api.foundations.dto.response.ServeAggregationResDTO;
import com.jzo2o.common.expcetions.ForbiddenOperationException;
import com.jzo2o.common.model.PageResult;
import com.jzo2o.common.utils.ObjectUtils;
import com.jzo2o.foundations.constants.RedisConstants;
import com.jzo2o.foundations.mapper.*;
import com.jzo2o.foundations.model.domain.*;
import com.jzo2o.foundations.model.dto.request.ServePageQueryReqDTO;
import com.jzo2o.foundations.model.dto.request.ServeUpsertReqDTO;
import com.jzo2o.foundations.model.dto.response.*;
import com.jzo2o.foundations.service.IServeService;
import com.jzo2o.mysql.utils.PageHelperUtils;
import com.jzo2o.mysql.utils.PageUtils;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.jzo2o.foundations.enums.FoundationStatusEnum.ENABLE;

@Service
public class ServeServiceImpl extends ServiceImpl<ServeMapper, Serve> implements IServeService {

    @Resource
    private ServeItemMapper serveItemMapper;
    @Resource
    private RegionMapper regionMapper;
    @Autowired
    private ServeMapper serveMapper;
    @Autowired
    private ServeSyncMapper serveSyncMapper;
    @Autowired
    private ServeTypeMapper serveTypeMapper;
    @Autowired
    private RestHighLevelClient client;

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

        //4.添加同步
        this.addServeSync(id);
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

        //3.添加同步
        serveSyncMapper.deleteById( id);
    }

    @Override
    @Caching(
            cacheable = {
                    //返回数据为空，则缓存空值30分钟，这样可以避免缓存穿透
                    @Cacheable(value = RedisConstants.CacheName.SERVE_ICON, key ="#regionId" ,
                            unless ="#result.size() > 0",cacheManager = RedisConstants.CacheManager.THIRTY_MINUTES),

                    //返回值不为空，则永久缓存数据
                    @Cacheable(value = RedisConstants.CacheName.SERVE_ICON, key ="#regionId" ,
                            unless ="#result.size() == 0",cacheManager = RedisConstants.CacheManager.FOREVER)
            }
    )
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
    @Caching(cacheable = {
        //返回数据为空，则缓存空值30分钟，这样可以避免缓存穿透
        @Cacheable(value = RedisConstants.CacheName.HOT_SERVE,key ="#regionId" ,
                unless ="#result.size() > 0",cacheManager = RedisConstants.CacheManager.THIRTY_MINUTES),

        //返回值不为空，则永久缓存数据
        @Cacheable(value = RedisConstants.CacheName.HOT_SERVE,key ="#regionId" ,
                unless ="#result.size() == 0",cacheManager = RedisConstants.CacheManager.FOREVER)
    }
)
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

    @Override
    public List<ServeAggregationTypeSimpleResDTO> serveTypeList(Long regionId) {
        //1 对区域进行校验
        Region region = regionMapper.selectById(regionId);
        if (ObjectUtil.isNull(region) || region.getActiveStatus() != 2) {
            return Collections.emptyList();
        }

        //2 查询当前区域下上架服务对应的分类
        return baseMapper.findServeTypeListByRegionId(regionId);
    }

    @Override
    public List<ServeSimpleResDTO> search(String cityCode, String keyword, Long serveTypeId) {
        //TODO
        //1. 创建请求对象
        SearchRequest request = new SearchRequest("serve_aggregation");

        //2. 封装请求参数
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        //城市编码
        boolQuery.must(QueryBuilders.termQuery("city_code", cityCode));
        //服务类型id
        if (serveTypeId != null) {
            boolQuery.must(QueryBuilders.termQuery("serve_type_id", serveTypeId));
        }
        //关键词
        if (StrUtil.isNotEmpty(keyword)) {
            boolQuery.must(QueryBuilders.multiMatchQuery(keyword, "serve_item_name", "serve_type_name"));
        }
        request.source().query(boolQuery);//查询
        request.source().sort("serve_item_sort_num", SortOrder.ASC);//排序

        //3. 执行请求
        SearchResponse response = null;
        try {
            response = client.search(request, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        //4. 处理返回结果   List<ServeSimpleResDTO>
        if (response.getHits().getTotalHits().value == 0) {
            return List.of();
        }
        return Arrays.stream(response.getHits().getHits())
                .map(e -> JSONUtil.toBean(e.getSourceAsString(), ServeSimpleResDTO.class))
                .collect(Collectors.toList());
    }

    @Override
    public ServeAggregationResDTO findById(Long serveId) {
        Serve serve = baseMapper.selectById(serveId);
        if (ObjectUtil.isNull(serve)) {
            throw new ForbiddenOperationException("服务详情查询失败，服务不存在");
        }
        ServeAggregationResDTO dto = BeanUtil.copyProperties(serve, ServeAggregationResDTO.class);
            
        ServeItem serveItem = serveItemMapper.selectById(serve.getServeItemId());
        if (ObjectUtil.isNull(serveItem)) {
            throw new ForbiddenOperationException("服务详情查询失败，服务项不存在");
        }
        dto.setServeItemName(serveItem.getName());
        dto.setServeItemId(serveItem.getId());
        dto.setServeItemSortNum(serveItem.getSortNum());
        dto.setUnit(serveItem.getUnit());
        dto.setDetailImg(serveItem.getDetailImg());
        dto.setServeItemImg(serveItem.getImg());
        dto.setServeItemIcon(serveItem.getServeItemIcon());
    
        ServeType serveType = serveTypeMapper.selectById(serveItem.getServeTypeId());
        if (ObjectUtil.isNull(serveType)) {
            throw new ForbiddenOperationException("服务详情查询失败，服务类型不存在");
        }
        dto.setServeTypeId(serveItem.getServeTypeId());
        dto.setServeTypeName(serveType.getName());
        dto.setServeTypeImg(serveType.getImg());
        dto.setServeTypeIcon(serveType.getServeTypeIcon());
        dto.setServeTypeSortNum(serveType.getSortNum());
            
        return dto;
    }


    /**
     * 新增服务同步数据
     *
     * @param serveId 服务id
     */
    private void addServeSync(Long serveId) {
        //服务信息
        Serve serve = baseMapper.selectById(serveId);
        //区域信息
        Region region = regionMapper.selectById(serve.getRegionId());
        //服务项信息
        ServeItem serveItem = serveItemMapper.selectById(serve.getServeItemId());
        //服务类型
        ServeType serveType = serveTypeMapper.selectById(serveItem.getServeTypeId());

        ServeSync serveSync = new ServeSync();
        serveSync.setServeTypeId(serveType.getId());
        serveSync.setServeTypeName(serveType.getName());
        serveSync.setServeTypeIcon(serveType.getServeTypeIcon());
        serveSync.setServeTypeImg(serveType.getImg());
        serveSync.setServeTypeSortNum(serveType.getSortNum());

        serveSync.setServeItemId(serveItem.getId());
        serveSync.setServeItemIcon(serveItem.getServeItemIcon());
        serveSync.setServeItemName(serveItem.getName());
        serveSync.setServeItemImg(serveItem.getImg());
        serveSync.setServeItemSortNum(serveItem.getSortNum());
        serveSync.setUnit(serveItem.getUnit());
        serveSync.setDetailImg(serveItem.getDetailImg());
        serveSync.setPrice(serve.getPrice());

        serveSync.setCityCode(region.getCityCode());
        serveSync.setId(serve.getId());
        serveSync.setIsHot(serve.getIsHot());
        serveSyncMapper.insert(serveSync);
    }
}
