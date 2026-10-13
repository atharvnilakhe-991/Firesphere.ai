package ai.firesphere.integration.core.configuration;

import ai.firesphere.integration.core.retry.RetryPolicy;

/**
 * Provider-specific configuration details.
 */
public class ProviderConfig {

    private String name;
    private boolean enabled = true;
    private long timeoutMs = 5000;
    private int maxRetries = 3;
    private long initialRetryDelayMs = 50;
    private double retryMultiplier = 1.5;
    private String endpointUrl = "mock://default";
    private String apiKey = "mock-api-key";

    public ProviderConfig() {}

    public ProviderConfig(String name) {
        this.name = name;
    }

    public RetryPolicy toRetryPolicy() {
        return new RetryPolicy(maxRetries, initialRetryDelayMs, retryMultiplier);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public long getInitialRetryDelayMs() {
        return initialRetryDelayMs;
    }

    public void setInitialRetryDelayMs(long initialRetryDelayMs) {
        this.initialRetryDelayMs = initialRetryDelayMs;
    }

    public double getRetryMultiplier() {
        return retryMultiplier;
    }

    public void setRetryMultiplier(double retryMultiplier) {
        this.retryMultiplier = retryMultiplier;
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public void setEndpointUrl(String endpointUrl) {
        this.endpointUrl = endpointUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}
