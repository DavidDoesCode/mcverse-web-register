package net.mcverse.register.integration;

import com.djrapitops.plan.capability.CapabilityService;
import com.djrapitops.plan.query.CommonQueries;
import com.djrapitops.plan.query.QueryService;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * PLAN Query API collector. Isolated so a missing Plan plugin cannot cause
 * {@link NoClassDefFoundError} from other classes.
 */
public class PlanServerStatsCollector implements ServerStatsCollector {

    private static final double REGULAR_ACTIVITY_INDEX = 2.0D;

    private final Logger logger;
    private final long planActivePlaytimeMs;

    public PlanServerStatsCollector(Logger logger, long planActivePlaytimeMs) {
        this.logger = logger;
        this.planActivePlaytimeMs = planActivePlaytimeMs > 0 ? planActivePlaytimeMs : 1_800_000L;
    }

    @Override
    public String name() {
        return "plan";
    }

    @Override
    public boolean isAvailable() {
        try {
            Plugin plan = Bukkit.getPluginManager().getPlugin("Plan");
            if (plan == null || !plan.isEnabled()) {
                return false;
            }
            return CapabilityService.getInstance().hasCapability("QUERY_API");
        } catch (NoClassDefFoundError | IllegalStateException e) {
            return false;
        }
    }

    @Override
    public void collect(ServerStatsSnapshot.Builder builder) {
        QueryService queryService;
        UUID serverUuid;
        try {
            queryService = QueryService.getInstance();
            serverUuid = queryService.getServerUUID().orElse(null);
        } catch (NoClassDefFoundError | IllegalStateException e) {
            logger.warning("PLAN query service unavailable: " + e.getMessage());
            applyPaperTpsFallback(builder);
            return;
        }
        if (serverUuid == null) {
            logger.warning("PLAN server UUID is not ready; skipping PLAN metrics.");
            applyPaperTpsFallback(builder);
            return;
        }

        CommonQueries queries = queryService.getCommonQueries();
        long now = System.currentTimeMillis();
        long weekStart = now - TimeUnit.DAYS.toMillis(7L);
        long dayAgo = now - TimeUnit.DAYS.toMillis(1L);

        builder.playersJoined(safeLong("playersJoined", () -> queryPlayersJoined(queryService, queries, serverUuid)));
        builder.totalPlaytimeMs(safeLong("totalPlaytimeMs", () -> queryPlaytime(queryService, queries, serverUuid)));
        builder.planRegularPlayers(safeInt("planRegularPlayers", () -> queryRegularPlayers(queryService, queries, serverUuid, now)));
        builder.playerKillsAllTime(safeLong("playerKillsAllTime", () -> queryKills(queryService, queries, serverUuid, null)));
        builder.playerKillsThisWeek(safeLong("playerKillsThisWeek", () -> queryKills(queryService, queries, serverUuid, weekStart)));
        builder.deathsAllTime(safeLong("deathsAllTime", () -> querySessionSum(queryService, queries, serverUuid, "deaths", null)));
        builder.deathsThisWeek(safeLong("deathsThisWeek", () -> querySessionSum(queryService, queries, serverUuid, "deaths", weekStart)));
        builder.mobKillsAllTime(safeLong("mobKillsAllTime", () -> querySessionSum(queryService, queries, serverUuid, "mob_kills", null)));
        builder.mobKillsThisWeek(safeLong("mobKillsThisWeek", () -> querySessionSum(queryService, queries, serverUuid, "mob_kills", weekStart)));

        Double averageTps = safeDouble("averageTps", () -> queryAverageTps(queryService, queries, serverUuid, dayAgo));
        if (averageTps == null) {
            applyPaperTpsFallback(builder);
        } else {
            builder.averageTps(averageTps);
        }
    }

    private Long queryPlayersJoined(QueryService queryService, CommonQueries queries, UUID serverUuid) {
        if (!queries.doesDBHaveTable("plan_user_info")) {
            return null;
        }
        String filter = serverFilter("plan_user_info", queries);
        if (filter == null) {
            return null;
        }
        String sql = "SELECT COUNT(1) FROM plan_user_info WHERE " + filter;
        return queryService.query(sql, statement -> {
            statement.setString(1, serverUuid.toString());
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? results.getLong(1) : 0L;
            }
        });
    }

    private Long queryPlaytime(QueryService queryService, CommonQueries queries, UUID serverUuid) {
        if (!queries.doesDBHaveTable("plan_sessions")
                || !queries.doesDBHaveTableColumn("plan_sessions", "session_start")
                || !queries.doesDBHaveTableColumn("plan_sessions", "session_end")) {
            return null;
        }
        String filter = serverFilter("plan_sessions", queries);
        if (filter == null) {
            return null;
        }
        String sql = "SELECT COALESCE(SUM(session_end - session_start), 0) FROM plan_sessions WHERE " + filter;
        long stored = queryService.query(sql, statement -> {
            statement.setString(1, serverUuid.toString());
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? results.getLong(1) : 0L;
            }
        });
        return stored + currentSessionPlaytime(queries);
    }

    private long currentSessionPlaytime(CommonQueries queries) {
        try {
            if (!CapabilityService.getInstance().hasCapability("QUERY_API_ACTIVE_SESSION_PLAYTIME")) {
                return 0L;
            }
        } catch (NoClassDefFoundError | IllegalStateException e) {
            return 0L;
        }
        long extra = 0L;
        for (Player player : Bukkit.getOnlinePlayers()) {
            extra += queries.fetchCurrentSessionPlaytime(player.getUniqueId());
        }
        return extra;
    }

    private Integer queryRegularPlayers(QueryService queryService, CommonQueries queries, UUID serverUuid, long now) {
        if (!queries.doesDBHaveTable("plan_sessions")
                || !queries.doesDBHaveTable("plan_user_info")
                || !queries.doesDBHaveTable("plan_users")
                || !queries.doesDBHaveTableColumn("plan_sessions", "user_id")
                || !queries.doesDBHaveTableColumn("plan_user_info", "user_id")
                || !queries.doesDBHaveTableColumn("plan_sessions", "session_start")
                || !queries.doesDBHaveTableColumn("plan_sessions", "session_end")) {
            return null;
        }
        String sessionFilter = serverFilter("plan_sessions", queries);
        String userInfoFilter = serverFilter("plan_user_info", queries);
        if (sessionFilter == null || userInfoFilter == null) {
            return null;
        }

        String playtimeExpr = queries.doesDBHaveTableColumn("plan_sessions", "afk_time")
                ? "session_end - session_start - afk_time"
                : "session_end - session_start";

        String weekSelect = "SELECT ax_ux.user_id, COALESCE(active_playtime, 0) AS active_playtime"
                + " FROM plan_user_info ax_ux LEFT JOIN ("
                + "SELECT user_id, SUM(" + playtimeExpr + ") AS active_playtime FROM plan_sessions"
                + " WHERE " + sessionFilter
                + " AND session_end >= ? AND session_start <= ? GROUP BY user_id"
                + ") ax_sx ON ax_sx.user_id = ax_ux.user_id";

        String sql = "SELECT COUNT(1) FROM ("
                + "SELECT COALESCE(activity_index, 0) AS activity_index FROM plan_user_info u "
                + "LEFT JOIN ("
                + "SELECT 5.0 - 5.0 * AVG(1.0 / (?/2.0 * (ax_q1.active_playtime*1.0/?) + 1.0)) AS activity_index,"
                + " ax_u.id AS user_id FROM ("
                + weekSelect + " UNION ALL " + weekSelect + " UNION ALL " + weekSelect
                + ") ax_q1 INNER JOIN plan_users ax_u ON ax_u.id = ax_q1.user_id GROUP BY ax_u.id"
                + ") q2 ON q2.user_id = u.user_id WHERE " + userInfoFilter + " AND u.registered <= ?"
                + ") i WHERE i.activity_index >= ?";

        return queryService.query(sql, statement -> {
            int index = 1;
            statement.setDouble(index++, Math.PI);
            statement.setLong(index++, planActivePlaytimeMs);
            index = bindWeekWindow(statement, index, serverUuid, now - TimeUnit.DAYS.toMillis(7L), now);
            index = bindWeekWindow(statement, index, serverUuid, now - TimeUnit.DAYS.toMillis(14L), now - TimeUnit.DAYS.toMillis(7L));
            index = bindWeekWindow(statement, index, serverUuid, now - TimeUnit.DAYS.toMillis(21L), now - TimeUnit.DAYS.toMillis(14L));
            statement.setString(index++, serverUuid.toString());
            statement.setLong(index++, now);
            statement.setDouble(index, REGULAR_ACTIVITY_INDEX);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? results.getInt(1) : 0;
            }
        });
    }

    private int bindWeekWindow(PreparedStatement statement, int index, UUID serverUuid, long from, long to) throws Exception {
        statement.setString(index++, serverUuid.toString());
        statement.setLong(index++, from);
        statement.setLong(index++, to);
        return index;
    }

    private Long queryKills(QueryService queryService, CommonQueries queries, UUID serverUuid, Long afterInclusive) {
        if (!queries.doesDBHaveTable("plan_kills") || !queries.doesDBHaveTableColumn("plan_kills", "date")) {
            return null;
        }
        String filter = serverFilter("plan_kills", queries);
        if (filter == null) {
            return null;
        }
        String sql = "SELECT COUNT(1) FROM plan_kills WHERE " + filter;
        if (afterInclusive != null) {
            sql += " AND date >= ?";
        }
        return queryService.query(sql, statement -> {
            statement.setString(1, serverUuid.toString());
            if (afterInclusive != null) {
                statement.setLong(2, afterInclusive);
            }
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? results.getLong(1) : 0L;
            }
        });
    }

    private Long querySessionSum(
            QueryService queryService,
            CommonQueries queries,
            UUID serverUuid,
            String column,
            Long sessionEndAfter
    ) {
        if (!queries.doesDBHaveTable("plan_sessions") || !queries.doesDBHaveTableColumn("plan_sessions", column)) {
            return null;
        }
        String filter = serverFilter("plan_sessions", queries);
        if (filter == null) {
            return null;
        }
        String sql = "SELECT COALESCE(SUM(" + column + "), 0) FROM plan_sessions WHERE " + filter;
        if (sessionEndAfter != null) {
            sql += " AND session_end >= ?";
        }
        return queryService.query(sql, statement -> {
            statement.setString(1, serverUuid.toString());
            if (sessionEndAfter != null) {
                statement.setLong(2, sessionEndAfter);
            }
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? results.getLong(1) : 0L;
            }
        });
    }

    private Double queryAverageTps(QueryService queryService, CommonQueries queries, UUID serverUuid, long after) {
        if (!queries.doesDBHaveTable("plan_tps") || !queries.doesDBHaveTableColumn("plan_tps", "tps")) {
            return null;
        }
        String filter = serverFilter("plan_tps", queries);
        if (filter == null) {
            return null;
        }
        String sql = "SELECT AVG(tps) FROM plan_tps WHERE " + filter + " AND date >= ?";
        return queryService.query(sql, statement -> {
            statement.setString(1, serverUuid.toString());
            statement.setLong(2, after);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    return null;
                }
                double value = results.getDouble(1);
                return results.wasNull() ? null : value;
            }
        });
    }

    private String serverFilter(String table, CommonQueries queries) {
        if (queries.doesDBHaveTableColumn(table, "server_id")) {
            return "server_id = (SELECT plan_servers.id FROM plan_servers WHERE plan_servers.uuid=? LIMIT 1)";
        }
        if (queries.doesDBHaveTableColumn(table, "server_uuid")) {
            return "server_uuid=?";
        }
        return null;
    }

    private void applyPaperTpsFallback(ServerStatsSnapshot.Builder builder) {
        if (builder.averageTps() != null) {
            return;
        }
        new PaperTpsCollector().collect(builder);
    }

    private Long safeLong(String metric, SqlLongCall call) {
        try {
            return call.run();
        } catch (Exception e) {
            logger.warning("PLAN metric " + metric + " failed: " + e.getMessage());
            return null;
        }
    }

    private Integer safeInt(String metric, SqlIntCall call) {
        try {
            return call.run();
        } catch (Exception e) {
            logger.warning("PLAN metric " + metric + " failed: " + e.getMessage());
            return null;
        }
    }

    private Double safeDouble(String metric, SqlDoubleCall call) {
        try {
            return call.run();
        } catch (Exception e) {
            logger.warning("PLAN metric " + metric + " failed: " + e.getMessage());
            return null;
        }
    }

    @FunctionalInterface
    private interface SqlLongCall {
        Long run() throws Exception;
    }

    @FunctionalInterface
    private interface SqlIntCall {
        Integer run() throws Exception;
    }

    @FunctionalInterface
    private interface SqlDoubleCall {
        Double run() throws Exception;
    }
}
