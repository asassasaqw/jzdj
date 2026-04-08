package com.jzo2o.customer.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jzo2o.customer.mapper.BankAccountMapper;
import com.jzo2o.customer.model.domain.BankAccount;
import com.jzo2o.customer.service.IBankAccountService;
import org.springframework.stereotype.Service;

@Service
public class BankAccountServiceImpl extends ServiceImpl<BankAccountMapper, BankAccount> implements IBankAccountService {
}
