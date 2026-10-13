package ai.firesphere.notification.template;

/**
 * Thrown when variable substitution fails during notification template rendering.
 */
public class TemplateRenderingException extends RuntimeException {

    public TemplateRenderingException(String message) {
        super(message);
    }

    public TemplateRenderingException(String message, Throwable cause) {
        super(message, cause);
    }
}
