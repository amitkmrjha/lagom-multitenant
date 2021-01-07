package com.example.helloworld.impl

package object daos {
  object ColumnFamilies {
    val StockById: String = "stock_by_id"

    val PortfolioById: String = "portfolio_by_id"
  }

  object Columns {
    val StockEntityID: String = "id"
    val TenantId: String = "tenant_id"
    val Name: String = "name"
    val Price: String = "price"

    val PortfolioEntityID: String = "id"
    val Holdings: String = "holdings"
  }
}