
package com.devjets.spend

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import javax.sql.DataSource

@Configuration
class Config {
    @Bean fun db(ds: DataSource) = NamedParameterJdbcTemplate(ds)
    @Bean fun spendService(db: NamedParameterJdbcTemplate) = SpendService(db)
}
