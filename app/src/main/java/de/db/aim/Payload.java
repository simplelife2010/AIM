package de.db.aim;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Payload {

    private long timestamp;
    private final Map<String, Object> metrics;

    public Payload() {
        this.metrics = new HashMap<>();
    }

    public long getTimestamp() {
        return this.timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public Object getMetric(String name) {
        return this.metrics.get(name);
    }

    public void addMetric(String name, Object value) {
        this.metrics.put(name, value);
    }

    public void removeMetric(String name) {
        this.metrics.remove(name);
    }

    public void removeAllMetrics() {
        this.metrics.clear();
    }

    public Set<String> metricNames() {
        return Collections.unmodifiableSet(this.metrics.keySet());
    }

    public Map<String, Object> metrics() {
        return Collections.unmodifiableMap(this.metrics);
    }

    public String toJson() {
        String json = "{" +
                "\"sentOn\":" + String.valueOf(this.timestamp) + "," +
                "\"metrics\":{";
        for (String key : metricNames()) {
            Object value = metrics().get(key);
            String valueString;
            if (value instanceof String) {
                valueString = "\"" + value.toString() + "\"";
            } else {
                valueString = value.toString();
            }
            json += "\"" + key + "\":" + valueString + ",";
        }
        json = json.substring(0, json.length() - 1) + "}}";
        return json;
    }
}
