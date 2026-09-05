package com.backend.dto;

public class MetricsDataPoint {
    private double latency; // generic 'latency' or 'secondary' value
    private double throughput; // generic 'throughput' or 'primary' value

    public MetricsDataPoint() {}

    public MetricsDataPoint(double latency, double throughput) {
        this.latency = latency;
        this.throughput = throughput;
    }

    public double getLatency() { return latency; }
    public void setLatency(double latency) { this.latency = latency; }

    public double getThroughput() { return throughput; }
    public void setThroughput(double throughput) { this.throughput = throughput; }
}
