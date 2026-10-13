package ai.firesphere.notification.template;

import ai.firesphere.notification.channel.NotificationChannel;
import ai.firesphere.notification.event.NotificationEventType;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry storing and resolving channel-specific notification templates for supported events.
 */
@Component
public class NotificationTemplateRegistry {

    private final Map<String, NotificationTemplate> registry = new ConcurrentHashMap<>();

    public NotificationTemplateRegistry() {
        registerDefaultTemplates();
    }

    private String buildKey(NotificationEventType eventType, NotificationChannel channel) {
        return eventType.name() + ":" + channel.name();
    }

    public void registerTemplate(NotificationTemplate template) {
        String key = buildKey(template.getEventType(), template.getChannel());
        registry.put(key, template);
    }

    public Optional<NotificationTemplate> findTemplate(NotificationEventType eventType, NotificationChannel channel) {
        if (eventType == null || channel == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(registry.get(buildKey(eventType, channel)));
    }

    public NotificationTemplate getTemplateOrThrow(NotificationEventType eventType, NotificationChannel channel) {
        return findTemplate(eventType, channel)
                .orElseThrow(() -> new TemplateNotFoundException(eventType, channel));
    }

    public Map<String, NotificationTemplate> getAllTemplates() {
        return Collections.unmodifiableMap(registry);
    }

    private void registerDefaultTemplates() {
        // 1. INCIDENT_ASSIGNMENT
        registerTemplate(new NotificationTemplate(
                "TPL-INCIDENT-PUSH",
                NotificationEventType.INCIDENT_ASSIGNMENT,
                NotificationChannel.PUSH,
                "Incident Assignment: {{incidentNumber}}",
                "You are assigned to incident {{incidentNumber}} at {{address}}. Severity: {{severity}}.",
                Set.of("incidentNumber", "address", "severity")
        ));
        registerTemplate(new NotificationTemplate(
                "TPL-INCIDENT-SMS",
                NotificationEventType.INCIDENT_ASSIGNMENT,
                NotificationChannel.SMS,
                null,
                "FireSphere: Assigned to incident {{incidentNumber}} at {{address}} ({{severity}}). Respond immediately.",
                Set.of("incidentNumber", "address", "severity")
        ));
        registerTemplate(new NotificationTemplate(
                "TPL-INCIDENT-EMAIL",
                NotificationEventType.INCIDENT_ASSIGNMENT,
                NotificationChannel.EMAIL,
                "[URGENT] Incident Assignment - {{incidentNumber}}",
                "Officer, you have been assigned to Incident {{incidentNumber}}.\nLocation: {{address}}\nSeverity: {{severity}}.",
                Set.of("incidentNumber", "address", "severity")
        ));
        registerTemplate(new NotificationTemplate(
                "TPL-INCIDENT-WA",
                NotificationEventType.INCIDENT_ASSIGNMENT,
                NotificationChannel.WHATSAPP,
                null,
                "🚨 *FireSphere Alert*: Assigned to Incident *{{incidentNumber}}* at {{address}}. Severity: {{severity}}.",
                Set.of("incidentNumber", "address", "severity")
        ));

        // 2. DISPATCH
        registerTemplate(new NotificationTemplate(
                "TPL-DISPATCH-PUSH",
                NotificationEventType.DISPATCH,
                NotificationChannel.PUSH,
                "Turnout Order: {{dispatchCode}}",
                "Unit {{unitCode}} dispatched to {{location}} for incident {{incidentNumber}}.",
                Set.of("dispatchCode", "unitCode", "location")
        ));
        registerTemplate(new NotificationTemplate(
                "TPL-DISPATCH-SMS",
                NotificationEventType.DISPATCH,
                NotificationChannel.SMS,
                null,
                "FireSphere Turnout: Unit {{unitCode}} dispatched to {{location}}. Dispatch: {{dispatchCode}}.",
                Set.of("dispatchCode", "unitCode", "location")
        ));
        registerTemplate(new NotificationTemplate(
                "TPL-DISPATCH-EMAIL",
                NotificationEventType.DISPATCH,
                NotificationChannel.EMAIL,
                "Emergency Dispatch Order - {{dispatchCode}}",
                "Apparatus {{unitCode}} dispatched to {{location}}.\nDispatch Reference: {{dispatchCode}}.",
                Set.of("dispatchCode", "unitCode", "location")
        ));
        registerTemplate(new NotificationTemplate(
                "TPL-DISPATCH-WA",
                NotificationEventType.DISPATCH,
                NotificationChannel.WHATSAPP,
                null,
                "🚒 *DISPATCH ORDER {{dispatchCode}}*: Apparatus *{{unitCode}}* roll out to {{location}}.",
                Set.of("dispatchCode", "unitCode", "location")
        ));

        // 3. VEHICLE_ALERT
        registerTemplate(new NotificationTemplate(
                "TPL-VEHICLE-PUSH",
                NotificationEventType.VEHICLE_ALERT,
                NotificationChannel.PUSH,
                "Vehicle Alert: {{vehicleCode}}",
                "Alert {{alertType}} for vehicle {{vehicleCode}}: {{details}}.",
                Set.of("vehicleCode", "alertType", "details")
        ));
        registerTemplate(new NotificationTemplate(
                "TPL-VEHICLE-SMS",
                NotificationEventType.VEHICLE_ALERT,
                NotificationChannel.SMS,
                null,
                "FireSphere Fleet: {{vehicleCode}} alert: {{alertType}} - {{details}}.",
                Set.of("vehicleCode", "alertType", "details")
        ));
        registerTemplate(new NotificationTemplate(
                "TPL-VEHICLE-EMAIL",
                NotificationEventType.VEHICLE_ALERT,
                NotificationChannel.EMAIL,
                "[Fleet Warning] {{vehicleCode}} - {{alertType}}",
                "Apparatus {{vehicleCode}} triggered an alert.\nType: {{alertType}}\nDetails: {{details}}.",
                Set.of("vehicleCode", "alertType", "details")
        ));
        registerTemplate(new NotificationTemplate(
                "TPL-VEHICLE-WA",
                NotificationEventType.VEHICLE_ALERT,
                NotificationChannel.WHATSAPP,
                null,
                "⚠️ *Fleet Alert {{vehicleCode}}*: {{alertType}} - {{details}}.",
                Set.of("vehicleCode", "alertType", "details")
        ));

        // 4. LOW_INVENTORY
        registerTemplate(new NotificationTemplate(
                "TPL-INVENTORY-PUSH",
                NotificationEventType.LOW_INVENTORY,
                NotificationChannel.PUSH,
                "Low Stock: {{sku}}",
                "Item {{itemName}} (SKU: {{sku}}) is below threshold at Station {{stationId}}. Remaining: {{quantity}}.",
                Set.of("sku", "itemName", "quantity")
        ));
        registerTemplate(new NotificationTemplate(
                "TPL-INVENTORY-SMS",
                NotificationEventType.LOW_INVENTORY,
                NotificationChannel.SMS,
                null,
                "FireSphere Store: SKU {{sku}} ({{itemName}}) low stock at station {{stationId}}: {{quantity}} left.",
                Set.of("sku", "itemName", "quantity")
        ));
        registerTemplate(new NotificationTemplate(
                "TPL-INVENTORY-EMAIL",
                NotificationEventType.LOW_INVENTORY,
                NotificationChannel.EMAIL,
                "Inventory Reorder Notice - {{sku}}",
                "Store Keeper, item {{itemName}} (SKU {{sku}}) has dropped below safety threshold.\nCurrent Level: {{quantity}} at Station {{stationId}}.",
                Set.of("sku", "itemName", "quantity")
        ));
        registerTemplate(new NotificationTemplate(
                "TPL-INVENTORY-WA",
                NotificationEventType.LOW_INVENTORY,
                NotificationChannel.WHATSAPP,
                null,
                "📦 *Inventory Alert*: {{itemName}} (SKU: {{sku}}) low stock: {{quantity}} units remaining at Station {{stationId}}.",
                Set.of("sku", "itemName", "quantity")
        ));

        // 5. MAINTENANCE_REMINDER
        registerTemplate(new NotificationTemplate(
                "TPL-MAINT-PUSH",
                NotificationEventType.MAINTENANCE_REMINDER,
                NotificationChannel.PUSH,
                "Maintenance Due: {{assetCode}}",
                "Scheduled maintenance due for {{assetCode}} on {{dueDate}}.",
                Set.of("assetCode", "dueDate")
        ));
        registerTemplate(new NotificationTemplate(
                "TPL-MAINT-SMS",
                NotificationEventType.MAINTENANCE_REMINDER,
                NotificationChannel.SMS,
                null,
                "FireSphere Maintenance: Service due for {{assetCode}} on {{dueDate}}.",
                Set.of("assetCode", "dueDate")
        ));
        registerTemplate(new NotificationTemplate(
                "TPL-MAINT-EMAIL",
                NotificationEventType.MAINTENANCE_REMINDER,
                NotificationChannel.EMAIL,
                "Scheduled Maintenance Reminder - {{assetCode}}",
                "Service reminder for asset {{assetCode}}.\nScheduled Due Date: {{dueDate}}.",
                Set.of("assetCode", "dueDate")
        ));
        registerTemplate(new NotificationTemplate(
                "TPL-MAINT-WA",
                NotificationEventType.MAINTENANCE_REMINDER,
                NotificationChannel.WHATSAPP,
                null,
                "🔧 *Maintenance Reminder*: Asset *{{assetCode}}* is scheduled for service on {{dueDate}}.",
                Set.of("assetCode", "dueDate")
        ));

        // 6. SYSTEM_ALERT
        registerTemplate(new NotificationTemplate(
                "TPL-SYS-PUSH",
                NotificationEventType.SYSTEM_ALERT,
                NotificationChannel.PUSH,
                "System Alert: {{alertTitle}}",
                "{{severity}} System Alert: {{message}}.",
                Set.of("alertTitle", "severity", "message")
        ));
        registerTemplate(new NotificationTemplate(
                "TPL-SYS-SMS",
                NotificationEventType.SYSTEM_ALERT,
                NotificationChannel.SMS,
                null,
                "FireSphere System: [{{severity}}] {{alertTitle}} - {{message}}",
                Set.of("alertTitle", "severity", "message")
        ));
        registerTemplate(new NotificationTemplate(
                "TPL-SYS-EMAIL",
                NotificationEventType.SYSTEM_ALERT,
                NotificationChannel.EMAIL,
                "[System Notice] {{severity}} - {{alertTitle}}",
                "System administrative alert:\nTitle: {{alertTitle}}\nSeverity: {{severity}}\nMessage: {{message}}",
                Set.of("alertTitle", "severity", "message")
        ));
        registerTemplate(new NotificationTemplate(
                "TPL-SYS-WA",
                NotificationEventType.SYSTEM_ALERT,
                NotificationChannel.WHATSAPP,
                null,
                "🔔 *SYSTEM ALERT [{{severity}}] {{alertTitle}}*: {{message}}",
                Set.of("alertTitle", "severity", "message")
        ));

        // 7. INSPECTION_NOTIFICATION
        registerTemplate(new NotificationTemplate(
                "TPL-INSPECT-PUSH",
                NotificationEventType.INSPECTION_NOTIFICATION,
                NotificationChannel.PUSH,
                "Inspection Scheduled: {{inspectionCode}}",
                "Safety inspection scheduled for {{facilityName}} on {{date}}.",
                Set.of("inspectionCode", "facilityName", "date")
        ));
        registerTemplate(new NotificationTemplate(
                "TPL-INSPECT-SMS",
                NotificationEventType.INSPECTION_NOTIFICATION,
                NotificationChannel.SMS,
                null,
                "FireSphere Inspection: {{facilityName}} scheduled for {{date}} (Ref: {{inspectionCode}}).",
                Set.of("inspectionCode", "facilityName", "date")
        ));
        registerTemplate(new NotificationTemplate(
                "TPL-INSPECT-EMAIL",
                NotificationEventType.INSPECTION_NOTIFICATION,
                NotificationChannel.EMAIL,
                "Safety Inspection Notice - {{inspectionCode}}",
                "Notice of scheduled safety inspection.\nFacility: {{facilityName}}\nDate: {{date}}.",
                Set.of("inspectionCode", "facilityName", "date")
        ));
        registerTemplate(new NotificationTemplate(
                "TPL-INSPECT-WA",
                NotificationEventType.INSPECTION_NOTIFICATION,
                NotificationChannel.WHATSAPP,
                null,
                "📋 *Inspection Notice*: Safety inspection scheduled for *{{facilityName}}* on {{date}} (Ref: {{inspectionCode}}).",
                Set.of("inspectionCode", "facilityName", "date")
        ));

        // 8. CERTIFICATE_NOTIFICATION
        registerTemplate(new NotificationTemplate(
                "TPL-CERT-PUSH",
                NotificationEventType.CERTIFICATE_NOTIFICATION,
                NotificationChannel.PUSH,
                "Certificate Issued: {{certNumber}}",
                "Fire compliance certificate {{certNumber}} issued for {{facilityName}}.",
                Set.of("certNumber", "facilityName")
        ));
        registerTemplate(new NotificationTemplate(
                "TPL-CERT-SMS",
                NotificationEventType.CERTIFICATE_NOTIFICATION,
                NotificationChannel.SMS,
                null,
                "FireSphere: Certificate {{certNumber}} issued for {{facilityName}}.",
                Set.of("certNumber", "facilityName")
        ));
        registerTemplate(new NotificationTemplate(
                "TPL-CERT-EMAIL",
                NotificationEventType.CERTIFICATE_NOTIFICATION,
                NotificationChannel.EMAIL,
                "Fire Safety Certificate - {{certNumber}}",
                "Compliance Certificate {{certNumber}} has been issued for {{facilityName}}.",
                Set.of("certNumber", "facilityName")
        ));
        registerTemplate(new NotificationTemplate(
                "TPL-CERT-WA",
                NotificationEventType.CERTIFICATE_NOTIFICATION,
                NotificationChannel.WHATSAPP,
                null,
                "📜 *Certificate Update*: Fire Safety Certificate *{{certNumber}}* issued for {{facilityName}}.",
                Set.of("certNumber", "facilityName")
        ));

        // 9. APPLICATION_UPDATE
        registerTemplate(new NotificationTemplate(
                "TPL-APP-UPDATE-PUSH",
                NotificationEventType.APPLICATION_UPDATE,
                NotificationChannel.PUSH,
                "Application Status: {{status}}",
                "Your Fire Safety application {{applicationId}} status is now: {{status}}.",
                Set.of("applicationId", "status")
        ));
        registerTemplate(new NotificationTemplate(
                "TPL-APP-UPDATE-SMS",
                NotificationEventType.APPLICATION_UPDATE,
                NotificationChannel.SMS,
                null,
                "FireSphere: Application {{applicationId}} status updated to {{status}}.",
                Set.of("applicationId", "status")
        ));
        registerTemplate(new NotificationTemplate(
                "TPL-APP-UPDATE-EMAIL",
                NotificationEventType.APPLICATION_UPDATE,
                NotificationChannel.EMAIL,
                "Fire Safety Application Update - {{applicationId}}",
                "Dear {{applicantName}}, your Fire Safety application {{applicationId}} status is now {{status}}.",
                Set.of("applicationId", "status", "applicantName")
        ));
        registerTemplate(new NotificationTemplate(
                "TPL-APP-UPDATE-WA",
                NotificationEventType.APPLICATION_UPDATE,
                NotificationChannel.WHATSAPP,
                null,
                "📝 *Application Update*: Your application *{{applicationId}}* is now *{{status}}*.",
                Set.of("applicationId", "status")
        ));

        // 10. INSPECTION_REMINDER
        registerTemplate(new NotificationTemplate(
                "TPL-INSP-REMINDER-PUSH",
                NotificationEventType.INSPECTION_REMINDER,
                NotificationChannel.PUSH,
                "Inspection Reminder: {{facilityName}}",
                "Reminder: Fire inspection for {{facilityName}} is scheduled on {{scheduledDate}}.",
                Set.of("facilityName", "scheduledDate")
        ));
        registerTemplate(new NotificationTemplate(
                "TPL-INSP-REMINDER-SMS",
                NotificationEventType.INSPECTION_REMINDER,
                NotificationChannel.SMS,
                null,
                "FireSphere Reminder: Inspection for {{facilityName}} scheduled on {{scheduledDate}}.",
                Set.of("facilityName", "scheduledDate")
        ));
        registerTemplate(new NotificationTemplate(
                "TPL-INSP-REMINDER-EMAIL",
                NotificationEventType.INSPECTION_REMINDER,
                NotificationChannel.EMAIL,
                "Upcoming Inspection Reminder - {{facilityName}}",
                "Reminder: An on-site fire inspection is scheduled for {{facilityName}} on {{scheduledDate}} (ID: {{inspectionId}}).",
                Set.of("facilityName", "scheduledDate", "inspectionId")
        ));
        registerTemplate(new NotificationTemplate(
                "TPL-INSP-REMINDER-WA",
                NotificationEventType.INSPECTION_REMINDER,
                NotificationChannel.WHATSAPP,
                null,
                "⏰ *Inspection Reminder*: Scheduled on *{{scheduledDate}}* for *{{facilityName}}*.",
                Set.of("facilityName", "scheduledDate")
        ));

        // 11. OFFICER_ASSIGNMENT
        registerTemplate(new NotificationTemplate(
                "TPL-OFFICER-ASSIGN-PUSH",
                NotificationEventType.OFFICER_ASSIGNMENT,
                NotificationChannel.PUSH,
                "New Task Assigned: {{taskId}}",
                "Officer {{officerBadge}}, you have been assigned task {{taskId}} at {{facilityName}}.",
                Set.of("officerBadge", "taskId", "facilityName")
        ));
        registerTemplate(new NotificationTemplate(
                "TPL-OFFICER-ASSIGN-SMS",
                NotificationEventType.OFFICER_ASSIGNMENT,
                NotificationChannel.SMS,
                null,
                "FireSphere Ops: Task {{taskId}} assigned to Officer {{officerBadge}} at {{facilityName}}.",
                Set.of("officerBadge", "taskId", "facilityName")
        ));
        registerTemplate(new NotificationTemplate(
                "TPL-OFFICER-ASSIGN-EMAIL",
                NotificationEventType.OFFICER_ASSIGNMENT,
                NotificationChannel.EMAIL,
                "Operational Task Assignment - {{taskId}}",
                "Officer {{officerBadge}}, you are assigned to operational task {{taskId}} for {{facilityName}}.",
                Set.of("officerBadge", "taskId", "facilityName")
        ));
        registerTemplate(new NotificationTemplate(
                "TPL-OFFICER-ASSIGN-WA",
                NotificationEventType.OFFICER_ASSIGNMENT,
                NotificationChannel.WHATSAPP,
                null,
                "👮 *Officer Assignment*: Task *{{taskId}}* assigned to Officer *{{officerBadge}}* for *{{facilityName}}*.",
                Set.of("officerBadge", "taskId", "facilityName")
        ));

        // 12. AI_ANALYSIS_COMPLETION
        registerTemplate(new NotificationTemplate(
                "TPL-AI-COMPLETE-PUSH",
                NotificationEventType.AI_ANALYSIS_COMPLETION,
                NotificationChannel.PUSH,
                "AI Analysis Complete: Job {{jobId}}",
                "AI computer vision analysis complete for inspection {{inspectionId}}. {{hazardCount}} hazard(s) flagged for review.",
                Set.of("jobId", "inspectionId", "hazardCount")
        ));
        registerTemplate(new NotificationTemplate(
                "TPL-AI-COMPLETE-SMS",
                NotificationEventType.AI_ANALYSIS_COMPLETION,
                NotificationChannel.SMS,
                null,
                "FireSphere AI: Analysis complete for job {{jobId}} ({{hazardCount}} findings ready for review).",
                Set.of("jobId", "hazardCount")
        ));
        registerTemplate(new NotificationTemplate(
                "TPL-AI-COMPLETE-EMAIL",
                NotificationEventType.AI_ANALYSIS_COMPLETION,
                NotificationChannel.EMAIL,
                "AI Inspection Analysis Complete - Job {{jobId}}",
                "Automated analysis completed for inspection {{inspectionId}}. Found {{hazardCount}} potential hazard(s) requiring officer review.",
                Set.of("jobId", "inspectionId", "hazardCount")
        ));
        registerTemplate(new NotificationTemplate(
                "TPL-AI-COMPLETE-WA",
                NotificationEventType.AI_ANALYSIS_COMPLETION,
                NotificationChannel.WHATSAPP,
                null,
                "🤖 *AI Analysis Complete*: Job *{{jobId}}* finished with *{{hazardCount}}* hazard(s) ready for review.",
                Set.of("jobId", "hazardCount")
        ));

        // 13. INSPECTION_REPORT_READY
        registerTemplate(new NotificationTemplate(
                "TPL-INSP-REPORT-PUSH",
                NotificationEventType.INSPECTION_REPORT_READY,
                NotificationChannel.PUSH,
                "Inspection Report Ready: {{facilityName}}",
                "Final inspection report {{reportId}} is ready. Outcome: {{outcome}}.",
                Set.of("reportId", "facilityName", "outcome")
        ));
        registerTemplate(new NotificationTemplate(
                "TPL-INSP-REPORT-SMS",
                NotificationEventType.INSPECTION_REPORT_READY,
                NotificationChannel.SMS,
                null,
                "FireSphere: Inspection report {{reportId}} for {{facilityName}} is ready. Result: {{outcome}}.",
                Set.of("reportId", "facilityName", "outcome")
        ));
        registerTemplate(new NotificationTemplate(
                "TPL-INSP-REPORT-EMAIL",
                NotificationEventType.INSPECTION_REPORT_READY,
                NotificationChannel.EMAIL,
                "Safety Inspection Report Ready - {{facilityName}}",
                "The safety inspection report {{reportId}} for {{facilityName}} has been finalized. Final outcome: {{outcome}}.",
                Set.of("reportId", "facilityName", "outcome")
        ));
        registerTemplate(new NotificationTemplate(
                "TPL-INSP-REPORT-WA",
                NotificationEventType.INSPECTION_REPORT_READY,
                NotificationChannel.WHATSAPP,
                null,
                "📄 *Inspection Report*: Report *{{reportId}}* for *{{facilityName}}* is finalized. Outcome: *{{outcome}}*.",
                Set.of("reportId", "facilityName", "outcome")
        ));

        // 14. RENEWAL_REMINDER
        registerTemplate(new NotificationTemplate(
                "TPL-RENEWAL-PUSH",
                NotificationEventType.RENEWAL_REMINDER,
                NotificationChannel.PUSH,
                "NOC Renewal Reminder: {{certificateNumber}}",
                "Fire certificate {{certificateNumber}} for {{facilityName}} expires on {{expiryDate}}. Please initiate renewal.",
                Set.of("certificateNumber", "facilityName", "expiryDate")
        ));
        registerTemplate(new NotificationTemplate(
                "TPL-RENEWAL-SMS",
                NotificationEventType.RENEWAL_REMINDER,
                NotificationChannel.SMS,
                null,
                "FireSphere: Certificate {{certificateNumber}} for {{facilityName}} expires on {{expiryDate}}. Renew today.",
                Set.of("certificateNumber", "facilityName", "expiryDate")
        ));
        registerTemplate(new NotificationTemplate(
                "TPL-RENEWAL-EMAIL",
                NotificationEventType.RENEWAL_REMINDER,
                NotificationChannel.EMAIL,
                "Fire Safety Certificate Renewal Reminder - {{certificateNumber}}",
                "Dear Manager, your Fire Safety Certificate {{certificateNumber}} for {{facilityName}} expires on {{expiryDate}}. Please submit your renewal application.",
                Set.of("certificateNumber", "facilityName", "expiryDate")
        ));
        registerTemplate(new NotificationTemplate(
                "TPL-RENEWAL-WA",
                NotificationEventType.RENEWAL_REMINDER,
                NotificationChannel.WHATSAPP,
                null,
                "🔄 *Renewal Notice*: Certificate *{{certificateNumber}}* for *{{facilityName}}* expires on *{{expiryDate}}*. Renew to stay compliant.",
                Set.of("certificateNumber", "facilityName", "expiryDate")
        ));
    }
}
