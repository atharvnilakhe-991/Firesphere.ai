package ai.firesphere.notification.template;

import ai.firesphere.notification.channel.NotificationChannel;
import ai.firesphere.notification.event.NotificationEventType;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Encapsulates a channel-specific message template with variable substitution and validation.
 */
public class NotificationTemplate {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([a-zA-Z0-9_.-]+)\\}\\}");

    private final String templateCode;
    private final NotificationEventType eventType;
    private final NotificationChannel channel;
    private final String subjectTemplate;
    private final String bodyTemplate;
    private final Set<String> requiredVariables;

    public record RenderedContent(String subject, String body) {}

    public NotificationTemplate(
            String templateCode,
            NotificationEventType eventType,
            NotificationChannel channel,
            String subjectTemplate,
            String bodyTemplate,
            Set<String> requiredVariables
    ) {
        this.templateCode = templateCode;
        this.eventType = eventType;
        this.channel = channel;
        this.subjectTemplate = subjectTemplate;
        this.bodyTemplate = bodyTemplate;
        this.requiredVariables = requiredVariables != null ? requiredVariables : Collections.emptySet();
    }

    public RenderedContent render(Map<String, Object> payload) {
        Map<String, Object> safePayload = payload != null ? payload : Collections.emptyMap();

        // 1. Verify required variables are supplied
        for (String requiredVar : requiredVariables) {
            if (!safePayload.containsKey(requiredVar) || safePayload.get(requiredVar) == null) {
                throw new TemplateRenderingException(String.format(
                        "Template '%s' failed to render: missing required variable '%s'", templateCode, requiredVar));
            }
        }

        // 2. Substitute variables in subject
        String renderedSubject = substitute(subjectTemplate, safePayload);

        // 3. Substitute variables in body
        String renderedBody = substitute(bodyTemplate, safePayload);

        return new RenderedContent(renderedSubject, renderedBody);
    }

    private String substitute(String template, Map<String, Object> payload) {
        if (template == null) {
            return null;
        }

        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String varName = matcher.group(1);
            Object value = payload.get(varName);
            String replacement = value != null ? Matcher.quoteReplacement(String.valueOf(value)) : "";
            matcher.appendReplacement(sb, replacement);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public NotificationEventType getEventType() {
        return eventType;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public String getSubjectTemplate() {
        return subjectTemplate;
    }

    public String getBodyTemplate() {
        return bodyTemplate;
    }

    public Set<String> getRequiredVariables() {
        return Collections.unmodifiableSet(requiredVariables);
    }
}
