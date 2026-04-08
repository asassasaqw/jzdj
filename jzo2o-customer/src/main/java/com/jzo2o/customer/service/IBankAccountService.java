package com.jzo2o.customer.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jzo2o.customer.model.domain.BankAccount;

public interface IBankAccountService extends IService<BankAccount> {


    /**
     * 保存或更新
     *
     * @param bankAccount
     * @return 是否成功
     */
    boolean saveOrUpdate(BankAccount bankAccount);
}
