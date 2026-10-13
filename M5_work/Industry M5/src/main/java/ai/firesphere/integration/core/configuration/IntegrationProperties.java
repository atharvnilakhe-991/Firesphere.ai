package ai.firesphere.integration.core.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Type-safe configuration properties for FireSphere.ai external integrations.
 * Root prefix: firesphere.integration
 */
@Component
@ConfigurationProperties(prefix = "firesphere.integration")
public class IntegrationProperties {

    private boolean enabled = true;
    private long defaultTimeoutMs = 5000;
    private int defaultMaxRetries = 3;

    private ProviderConfig payment = new ProviderConfig("mock-payment");
    private ProviderConfig sms = new ProviderConfig("mock-sms");
    private ProviderConfig email = new ProviderConfig("mock-email");
    private ProviderConfig whatsapp = new ProviderConfig("mock-whatsapp");
    private ProviderConfig maps = new ProviderConfig("mock-maps");
    private ProviderConfig digitalSignature = new ProviderConfig("mock-digital-signature");
    private ProviderConfig cad = new ProviderConfig("mock-cad");
    private ProviderConfig storage = new ProviderConfig("mock-storage");

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getDefaultTimeoutMs() {
        return defaultTimeoutMs;
    }

    public void setDefaultTimeoutMs(long defaultTimeoutMs) {
        this.defaultTimeoutMs = defaultTimeoutMs;
    }

    public int getDefaultMaxRetries() {
        return defaultMaxRetries;
    }

    public void setDefaultMaxRetries(int defaultMaxRetries) {
        this.defaultMaxRetries = defaultMaxRetries;
    }

    public ProviderConfig getPayment() {
        return payment;
    }

    public void setPayment(ProviderConfig payment) {
        this.payment = payment;
    }

    public ProviderConfig getSms() {
        return sms;
    }

    public void setSms(ProviderConfig sms) {
        this.sms = sms;
    }

    public ProviderConfig getEmail() {
        return email;
    }

    public void setEmail(ProviderConfig email) {
        this.email = email;
    }

    public ProviderConfig getWhatsapp() {
        return whatsapp;
    }

    public void setWhatsapp(ProviderConfig whatsapp) {
        this.whatsapp = whatsapp;
    }

    public ProviderConfig getMaps() {
        return maps;
    }

    public void setMaps(ProviderConfig maps) {
        this.maps = maps;
    }

    public ProviderConfig getDigitalSignature() {
        return digitalSignature;
    }

    public void setDigitalSignature(ProviderConfig digitalSignature) {
        this.digitalSignature = digitalSignature;
    }

    public ProviderConfig getCad() {
        return cad;
    }

    public void setCad(ProviderConfig cad) {
        this.cad = cad;
    }

    public ProviderConfig getStorage() {
        return storage;
    }

    public void setStorage(ProviderConfig storage) {
        this.storage = storage;
    }
}
