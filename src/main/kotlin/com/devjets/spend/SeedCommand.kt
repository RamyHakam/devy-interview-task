
package com.devjets.spend

import org.springframework.boot.CommandLineRunner
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class SeedCommand(private val jdbc: JdbcTemplate) : CommandLineRunner {
    override fun run(vararg args: String?) {
        if (System.getenv("SEED_ENABLED")?.lowercase() == "false") return
        val count = System.getenv("SEED_COUNT")?.toLongOrNull() ?: 2_000_000L
        val existing = jdbc.queryForObject("SELECT COUNT(*) FROM transactions", Long::class.java) ?: 0
        if (existing >= count) return
        jdbc.execute("""
            INSERT INTO customers(name)
            SELECT 'Customer ' || g FROM generate_series(1,50) g
            ON CONFLICT DO NOTHING
        """)
        jdbc.execute("""
            INSERT INTO teams(customer_id,name)
            SELECT c.id, 'Team ' || x
            FROM customers c CROSS JOIN generate_series(1,10) x
            ON CONFLICT DO NOTHING
        """)
        jdbc.update("""
            INSERT INTO transactions(customer_id,team_id,category,amount,currency,occurred_at)
            SELECT
              CASE WHEN g <= ? * 0.80 THEN 1 ELSE 2 + ((g * 17) % 49) END,
              1 + (((CASE WHEN g <= ? * 0.80 THEN 1 ELSE 2 + ((g * 17) % 49) END - 1) * 10) + (g % 10)),
              (ARRAY['travel','software','meals','office','hardware'])[1 + (g % 5)],
              100 + (g % 100000),
              'EUR',
              now() - ((g % 730)::text || ' days')::interval
            FROM generate_series(? + 1, ?) g
        """, existing, count, existing, count)
    }
}
