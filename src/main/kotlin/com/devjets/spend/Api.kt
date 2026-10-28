
package com.devjets.spend

import jakarta.validation.Valid
import jakarta.validation.constraints.*
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.web.bind.annotation.*
import java.time.OffsetDateTime
import java.time.YearMonth
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

data class TransactionRequest(
    @field:NotNull val teamId: Long?,
    @field:NotBlank val category: String,
    @field:NotNull @field:Min(0) val amount: Long?,
    @field:Pattern(regexp="[A-Z]{3}") val currency: String = "EUR",
    @field:NotNull val occurredAt: OffsetDateTime?
)
data class TransactionRow(val id: Long, val teamId: Long, val category: String, val amount: Long, val currency: String, val occurredAt: OffsetDateTime)
data class Page<T>(val items: List<T>, val nextCursor: String?)
data class SpendGroup(val team: String?, val category: String?, val month: String?, val totalAmount: Long, val transactionCount: Long)
data class Report(val groups: List<SpendGroup>)

@RestController
class SpendController(private val service: SpendService) {
    @GetMapping("/customers/{customerId}/reports/spend")
    fun report(
        @PathVariable customerId: Long,
        @RequestParam from: OffsetDateTime,
        @RequestParam to: OffsetDateTime,
        @RequestParam("group_by") groupBy: String,
        @RequestParam(required = false) category: String?
    ) = service.report(customerId, from, to, groupBy, category)

    @GetMapping("/customers/{customerId}/transactions")
    fun transactions(
        @PathVariable customerId: Long,
        @RequestParam(required = false) from: OffsetDateTime?,
        @RequestParam(required = false) to: OffsetDateTime?,
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false) teamId: Long?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "100") @Min(1) @Max(500) limit: Int
    ) = service.transactions(customerId, from, to, category, teamId, cursor, limit)

    @PostMapping("/customers/{customerId}/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@PathVariable customerId: Long, @Valid @RequestBody request: TransactionRequest) =
        service.create(customerId, request)
}

class SpendService(private val db: NamedParameterJdbcTemplate) {
    private val cache = ConcurrentHashMap<String, Pair<Long, Report>>()
    private val locks = ConcurrentHashMap<String, ReentrantLock>()
    private val ttlMs = 30_000L

    fun report(customerId: Long, from: OffsetDateTime, to: OffsetDateTime, groupBy: String, category: String?): Report {
        val groups = groupBy.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
        require(groups.isNotEmpty() && groups.all { it in setOf("team","category","month") }) { "group_by must contain team, category and/or month" }
        require(from.isBefore(to)) { "from must be before to" }
        val key = "$customerId|$from|$to|${groups.joinToString(",")}|${category ?: ""}"
        val now = System.currentTimeMillis()
        cache[key]?.takeIf { now - it.first < ttlMs }?.let { return it.second }

        val lock = locks.computeIfAbsent(key) { ReentrantLock() }
        lock.withLock {
            cache[key]?.takeIf { System.currentTimeMillis() - it.first < ttlMs }?.let { return it.second }
            val dimensions = groups.joinToString(", ") {
                when(it) {
                    "team" -> "t.name AS team"
                    "category" -> "t.category AS category"
                    else -> "date_trunc('month', t.occurred_at) AS month"
                }
            }
            val sql = """
                SELECT $dimensions, COALESCE(SUM(t.amount),0) AS total_amount, COUNT(*) AS transaction_count
                FROM transactions t
                JOIN teams tm ON tm.id = t.team_id AND tm.customer_id = t.customer_id
                WHERE t.customer_id = :customerId
                  AND t.occurred_at >= :from AND t.occurred_at < :to
                  ${if (category != null) "AND t.category = :category" else ""}
                GROUP BY ${groups.joinToString(", ") {
                    when(it) { "team" -> "t.team_id, t.team_id, tm.name"; "category" -> "t.category"; else -> "date_trunc('month', t.occurred_at)" }
                }}
                ORDER BY ${groups.joinToString(", ") { when(it) { "team" -> "tm.name"; "category" -> "t.category"; else -> "date_trunc('month', t.occurred_at)" } }}
            """.trimIndent()
            val params = mutableMapOf<String, Any>("customerId" to customerId, "from" to from, "to" to to)
            if (category != null) params["category"] = category
            val result = db.query(sql, params) { rs, _ ->
                SpendGroup(
                    rs.getString("team").takeIf { !rs.wasNull() },
                    rs.getString("category").takeIf { !rs.wasNull() },
                    rs.getTimestamp("month")?.toInstant()?.toString()?.substring(0,7),
                    rs.getLong("total_amount"), rs.getLong("transaction_count")
                )
            }
            return Report(result).also { cache[key] = System.currentTimeMillis() to it }
        }
    }

    fun transactions(customerId: Long, from: OffsetDateTime?, to: OffsetDateTime?, category: String?, teamId: Long?, cursor: String?, limit: Int): Page<TransactionRow> {
        val decoded = cursor?.let { decodeCursor(it) }
        val where = mutableListOf("customer_id = :customerId")
        val params = mutableMapOf<String, Any>("customerId" to customerId, "limit" to limit + 1)
        if (from != null) { where += "occurred_at >= :from"; params["from"] = from }
        if (to != null) { where += "occurred_at < :to"; params["to"] = to }
        if (category != null) { where += "category = :category"; params["category"] = category }
        if (teamId != null) { where += "team_id = :teamId"; params["teamId"] = teamId }
        if (decoded != null) {
            where += "(occurred_at, id) < (:cursorAt, :cursorId)"
            params["cursorAt"] = decoded.first; params["cursorId"] = decoded.second
        }
        val rows = db.query("""
            SELECT id, team_id, category, amount, currency, occurred_at
            FROM transactions
            WHERE ${where.joinToString(" AND ")}
            ORDER BY occurred_at DESC, id DESC
            LIMIT :limit
        """.trimIndent(), params) { rs, _ ->
            TransactionRow(rs.getLong("id"), rs.getLong("team_id"), rs.getString("category"), rs.getLong("amount"), rs.getString("currency"), rs.getObject("occurred_at", OffsetDateTime::class.java))
        }
        val more = rows.size > limit
        val items = if (more) rows.dropLast(1) else rows
        val next = items.lastOrNull()?.let { encodeCursor(it.occurredAt, it.id) }
        return Page(items, if (more) next else null)
    }

    fun create(customerId: Long, r: TransactionRequest): TransactionRow {
        require(r.amount != null && r.occurredAt != null && r.teamId != null)
        val id = db.queryForObject("""
            INSERT INTO transactions(customer_id,team_id,category,amount,currency,occurred_at)
            VALUES(:customerId,:teamId,:category,:amount,:currency,:occurredAt)
            RETURNING id
        """, mapOf("customerId" to customerId, "teamId" to r.teamId, "category" to r.category, "amount" to r.amount, "currency" to r.currency, "occurredAt" to r.occurredAt), Long::class.java)!!
        invalidateCustomer(customerId)
        return TransactionRow(id, r.teamId, r.category, r.amount, r.currency, r.occurredAt)
    }

    private fun invalidateCustomer(customerId: Long) {
        cache.keys.removeIf { it.startsWith("$customerId|") }
    }

    private fun encodeCursor(at: OffsetDateTime, id: Long) =
        Base64.getUrlEncoder().withoutPadding().encodeToString("$at|$id".toByteArray())
    private fun decodeCursor(cursor: String): Pair<OffsetDateTime, Long> {
        val s = String(Base64.getUrlDecoder().decode(cursor))
        val p = s.lastIndexOf('|')
        return OffsetDateTime.parse(s.substring(0,p)) to s.substring(p+1).toLong()
    }
}
