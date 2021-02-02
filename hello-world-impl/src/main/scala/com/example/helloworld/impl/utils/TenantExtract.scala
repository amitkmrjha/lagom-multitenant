package com.example.helloworld.impl.utils

import com.example.domain.{Portfolio, Stock}
import com.example.helloworld.impl.tenant.TenantPersistenceId

object TenantExtract {
  implicit class StockTenantIdOps(stock:Stock){
    def toTenant = TenantPersistenceId(stock.tenantId.getOrElse(""))
  }

  implicit class PortfolioTenantIdOps(portfolio: Portfolio){
    def toTenant = TenantPersistenceId(portfolio.tenantId)
  }

}