package com.jzo2o.foundations.handler;


import com.jzo2o.api.foundations.dto.response.RegionSimpleResDTO;
import com.jzo2o.foundations.constants.RedisConstants;

import com.jzo2o.foundations.service.IRegionService;
import com.jzo2o.foundations.service.IServeService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class SpringCacheSyncHandler {
    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private IRegionService regionService;

    @Autowired
    private IServeService serveService;


    //更新缓存---开通区域
    @XxlJob("activeRegionCacheSync")
    public void activeRegionCacheSync() {
        log.info("=============开始更新开通区域列表缓存============");
        //1. 使用redisTemplate删除当前缓存中开通区域列表
        redisTemplate.delete("JZ_CACHE::ACTIVE_REGIONS");


        //2. 重新将开通区域列表添加到缓存
        List<RegionSimpleResDTO> regionSimpleResDTOS
                = regionService.queryActiveRegionListCache();

        log.info("=============开始更新首页服务列表缓存============");
        //3. 查询所有开通区域, 然后进行遍历
        for (RegionSimpleResDTO regionSimpleResDTO : regionSimpleResDTOS) {
            //获取到每个开通区域的id
            Long id = regionSimpleResDTO.getId();

            //根据区域id删除缓存数据
            redisTemplate.delete(RedisConstants.CacheName.SERVE_ICON + "::" + id);
            redisTemplate.delete(RedisConstants.CacheName.HOT_SERVE+"::" + id);

            //重新查询,放入缓存
            serveService.firstPageServeList(id);
            log.info("--更新热门服务列表");
            serveService.hotServeList(id);
        }
    }

}
