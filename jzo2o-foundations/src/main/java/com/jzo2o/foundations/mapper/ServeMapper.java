package com.jzo2o.foundations.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jzo2o.api.foundations.dto.response.ServeAggregationResDTO;
import com.jzo2o.foundations.enums.FoundationStatusEnum;
import com.jzo2o.foundations.model.domain.Serve;
import com.jzo2o.foundations.model.dto.response.ServeAggregationSimpleResDTO;
import com.jzo2o.foundations.model.dto.response.ServeAggregationTypeSimpleResDTO;
import com.jzo2o.foundations.model.dto.response.ServeCategoryResDTO;
import com.jzo2o.foundations.model.dto.response.ServeResDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 * Mapper 接口
 * </p>
 *
 * @author itcast
 * @since 2023-07-03
 */
@Mapper
public interface ServeMapper extends BaseMapper<Serve> {


    List<ServeResDTO> queryListByRegionId(Long regionId);

    @Select("SELECT * FROM serve WHERE region_id = #{regionId} AND sale_status = #{saleStatus}")
    List<Serve> queryByRegionIdAndSaleStatus(Long regionId, Integer saleStatus);

    List<ServeCategoryResDTO> findListByRegionId(Long regionId);

    List<ServeAggregationSimpleResDTO> findServeListByRegionId(Long regionId);


    List<ServeAggregationTypeSimpleResDTO> findServeTypeListByRegionId(Long regionId);

}
