package com.jzo2o.customer.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jzo2o.common.model.PageResult;
import com.jzo2o.customer.model.domain.WorkerCertificationAudit;
import com.jzo2o.customer.model.dto.request.CertificationAuditReqDTO;
import com.jzo2o.customer.model.dto.request.WorkerCertificationAuditAddReqDTO;
import com.jzo2o.customer.model.dto.request.WorkerCertificationAuditPageQueryReqDTO;
import com.jzo2o.customer.model.dto.response.RejectReasonResDTO;
import com.jzo2o.customer.model.dto.response.WorkerCertificationAuditResDTO;
import org.springframework.stereotype.Service;

@Service
public interface IWorkerCertificationAuditService extends IService<WorkerCertificationAudit> {

    /**
     * 提交认证申请
     *
     * @param workerCertificationAuditAddReqDTO
     */
    void applyCertification(WorkerCertificationAuditAddReqDTO workerCertificationAuditAddReqDTO);

    /**
     * 查询当前用户最近驳回原因
     * @return 驳回原因
     */
    RejectReasonResDTO queryCurrentUserLastRejectReason();

    /**
     * 分页查询
     * @param workerCertificationAuditPageQueryReqDTO 分页查询条件
     * @return 分页结果
     */
    PageResult<WorkerCertificationAuditResDTO> pageQuery(WorkerCertificationAuditPageQueryReqDTO workerCertificationAuditPageQueryReqDTO);

    /**
     * 审核认证信息
     * @param id                       申请记录id
     * @param certificationAuditReqDTO 审核请求
     */
    void auditCertification(Long id, CertificationAuditReqDTO certificationAuditReqDTO);
}
