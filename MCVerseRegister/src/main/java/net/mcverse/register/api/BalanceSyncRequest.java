package net.mcverse.register.api;

import java.time.Instant;

public record BalanceSyncRequest(double balance, Instant observedAt) {

    public String toJson() {
        StringBuilder json = new StringBuilder();
        json.append("{\"balance\":").append(balance);
        if (observedAt != null) {
            json.append(",\"observedAt\":\"").append(observedAt).append("\"");
        }
        json.append("}");
        return json.toString();
    }

    public String summary() {
        return "balance=" + balance;
    }
}
