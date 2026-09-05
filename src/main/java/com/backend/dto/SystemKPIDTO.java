package com.backend.dto;

import java.util.List;

public class SystemKPIDTO {
    private double cpuUtilization;
    private double jvmHeap;
    private double dbStorageGB;
    private double averageApiLatency;
    private int pgActiveConnections;
    
    // Arrays for charts
    private List<MetricsDataPoint> trendData; // Latency vs Throughput (for All Logs)
    private List<MetricsDataPoint> dbTrendData; // Query Latency vs IOPS (for Database)
    private List<MetricsDataPoint> gatewayTrendData; // Request Rate vs Errors (for API Gateway)
    private List<MetricsDataPoint> thirdPartyTrendData; // Webhook Delivery vs Timeouts
    
    private List<Integer> pgConnectionsHistory; // For right chart

    public SystemKPIDTO() {}

    public double getCpuUtilization() { return cpuUtilization; }
    public void setCpuUtilization(double cpuUtilization) { this.cpuUtilization = cpuUtilization; }

    public double getJvmHeap() { return jvmHeap; }
    public void setJvmHeap(double jvmHeap) { this.jvmHeap = jvmHeap; }

    public double getDbStorageGB() { return dbStorageGB; }
    public void setDbStorageGB(double dbStorageGB) { this.dbStorageGB = dbStorageGB; }

    public double getAverageApiLatency() { return averageApiLatency; }
    public void setAverageApiLatency(double averageApiLatency) { this.averageApiLatency = averageApiLatency; }

    public int getPgActiveConnections() { return pgActiveConnections; }
    public void setPgActiveConnections(int pgActiveConnections) { this.pgActiveConnections = pgActiveConnections; }

    public List<MetricsDataPoint> getTrendData() { return trendData; }
    public void setTrendData(List<MetricsDataPoint> trendData) { this.trendData = trendData; }

    public List<MetricsDataPoint> getDbTrendData() { return dbTrendData; }
    public void setDbTrendData(List<MetricsDataPoint> dbTrendData) { this.dbTrendData = dbTrendData; }

    public List<MetricsDataPoint> getGatewayTrendData() { return gatewayTrendData; }
    public void setGatewayTrendData(List<MetricsDataPoint> gatewayTrendData) { this.gatewayTrendData = gatewayTrendData; }

    public List<MetricsDataPoint> getThirdPartyTrendData() { return thirdPartyTrendData; }
    public void setThirdPartyTrendData(List<MetricsDataPoint> thirdPartyTrendData) { this.thirdPartyTrendData = thirdPartyTrendData; }

    public List<Integer> getPgConnectionsHistory() { return pgConnectionsHistory; }
    public void setPgConnectionsHistory(List<Integer> pgConnectionsHistory) { this.pgConnectionsHistory = pgConnectionsHistory; }
}
