package com.jzo2o.customer.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jzo2o.common.model.PageResult;
import com.jzo2o.customer.model.domain.AgencyCertificationAudit;
import com.jzo2o.customer.model.dto.request.AgencyCertificationAuditAddReqDTO;
import com.jzo2o.customer.model.dto.request.AgencyCertificationAuditPageQueryReqDTO;
import com.jzo2o.customer.model.dto.request.CertificationAuditReqDTO;
import com.jzo2o.customer.model.dto.response.AgencyCertificationAuditResDTO;
import com.jzo2o.customer.model.dto.response.RejectReasonResDTO;

public interface IAgencyCertificationAuditService extends IService<AgencyCertificationAudit> {
    /**
     * 机构申请资质认证
     * @param agencyCertificationAuditAddReqDTO 认证申请请求体
     */
    void applyCertification(AgencyCertificationAuditAddReqDTO agencyCertificationAuditAddReqDTO);

    /**
     * 查询当前用户最近驳回原因
     * @return 驳回原因
     */
    RejectReasonResDTO queryCurrentUserLastRejectReason();

    /**
     * 机构认证审核信息分页查询
     * @param agencyCertificationAuditPageQueryReqDTO
     * @return
     */
    PageResult<AgencyCertificationAuditResDTO> pageQuery(AgencyCertificationAuditPageQueryReqDTO agencyCertificationAuditPageQueryReqDTO);

    /**
     * 审核机构认证信息
     * @param id
     * @param certificationAuditReqDTO
     */
    void auditCertification(Long id, CertificationAuditReqDTO certificationAuditReqDTO);
}