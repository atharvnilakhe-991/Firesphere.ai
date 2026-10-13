package com.firesphere.backend.checklist.service;

import com.firesphere.backend.checklist.entity.ChecklistTemplate;
import com.firesphere.backend.checklist.entity.ChecklistTemplateItem;
import com.firesphere.backend.checklist.repository.ChecklistTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(10)
public class ChecklistTemplateInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ChecklistTemplateInitializer.class);
    private final ChecklistTemplateRepository templateRepository;

    public ChecklistTemplateInitializer(ChecklistTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (templateRepository.count() == 0) {
            log.info("Initializing regulatory checklist templates (NBC 2016 Part 4)...");
            seedCommercialTemplate();
            seedResidentialTemplate();
            log.info("Checklist templates successfully initialized.");
        }
    }

    private void seedCommercialTemplate() {
        ChecklistTemplate template = new ChecklistTemplate();
        template.setChecklistCode("NBC_2016_COMMERCIAL");
        template.setTitle("National Building Code 2016 Part 4 - Commercial & Business Buildings");
        template.setOccupancyType("COMMERCIAL");
        template.setInspectionType("ROUTINE");
        template.setNbcReferenceClause("NBC 2016 Part 4 Table 7");
        template.setSourceAuthority("NBC_2016_PART_4");
        template.setVersion(1);
        template.setIsActive(true);
        template.setDescription("Authoritative Fire NOC statutory inspection requirements for commercial, institutional, and mercantile occupancies.");

        template.addItem(createItem(1, "EXT-01", "Portable Fire Extinguishers Distribution & Mounting",
                "Portable fire extinguishers of appropriate class installed at designated locations, mounted not higher than 1.5m above finished floor level, fully charged with unbroken seals (NBC 2016 Part 4 Table 7 Cl 4.3).",
                "MANDATORY", "PHYSICAL_INSPECTION", true, "FIRE_EXTINGUISHER", 10,
                "Verify extinguisher type, pressure gauge in green zone, maintenance tag within validity, unobstructed access."));

        template.addItem(createItem(2, "EXIT-01", "Illuminated Emergency Exit Signage",
                "All required emergency exits and exit access doors shall be marked by an approved illuminated exit sign visible from any direction of exit access (NBC 2016 Part 4 Cl 4.16).",
                "MANDATORY", "PHYSICAL_INSPECTION", true, "EXIT_SIGN", 15,
                "Verify illumination from primary and secondary/battery power sources. Text must be distinctly legible."));

        template.addItem(createItem(3, "EXIT-02", "Emergency Exit Doors Operation & Swing",
                "Exit doors shall swing outwards in the direction of exit travel, remain unlocked and unlatched from the inside during all hours of building occupancy, and open freely (NBC 2016 Part 4 Cl 4.17).",
                "MANDATORY", "PHYSICAL_INSPECTION", true, "EXIT_DOOR", 20,
                "Verify panic hardware where applicable, no padlocks or chains, clear swing path without latch resistance."));

        template.addItem(createItem(4, "OBS-01", "Clearance of Means of Egress & Escape Routes",
                "Means of egress including hallways, stairways, and exit discharge paths shall be continuously maintained free of all obstructions, furniture, storage, or hazardous impediments (NBC 2016 Part 4 Cl 4.3).",
                "MANDATORY", "PHYSICAL_INSPECTION", true, "OBSTACLE", 25,
                "Inspect entire corridor length for boxes, pallets, kiosks, temporary partitions, or clutter reducing minimum exit width."));

        template.addItem(createItem(5, "ALARM-01", "Manual Call Points & Fire Alarm Annunciation",
                "Manual call points located on all floors near exits, audibility of alarm exceeding ambient noise level by at least 15 dB throughout all occupant spaces (NBC 2016 Part 4 Cl 4.18).",
                "MANDATORY", "PHYSICAL_INSPECTION", false, null, 15,
                "Test fire alarm signal transmission to main control panel and confirm audible sirens on upper/lower levels."));

        template.addItem(createItem(6, "HYDR-01", "Internal Wet Riser & Landing Valve Operating Pressure",
                "Wet riser system operational with minimum residual pressure of 3.5 bar at highest hydrant point, fire brigade inlet connection undamaged (NBC 2016 Part 4 Cl 5.1).",
                "RECOMMENDED", "PHYSICAL_INSPECTION", false, null, 10,
                "Verify pressure gauge reading at roof landing valve, delivery hose in cabinet clean and uncracked."));

        templateRepository.save(template);
    }

    private void seedResidentialTemplate() {
        ChecklistTemplate template = new ChecklistTemplate();
        template.setChecklistCode("NBC_2016_RESIDENTIAL");
        template.setTitle("National Building Code 2016 Part 4 - Residential Buildings (>15m Height)");
        template.setOccupancyType("RESIDENTIAL");
        template.setInspectionType("ROUTINE");
        template.setNbcReferenceClause("NBC 2016 Part 4 Table 7");
        template.setSourceAuthority("NBC_2016_PART_4");
        template.setVersion(1);
        template.setIsActive(true);
        template.setDescription("Standard statutory inspection checklist for high-rise residential group housing complexes.");

        template.addItem(createItem(1, "EXT-01", "Floor Landing Fire Extinguishers",
                "Minimum one ABC dry chemical powder or water-CO2 extinguisher provided per floor landing adjacent to staircase enclosure (NBC 2016 Part 4 Table 7).",
                "MANDATORY", "PHYSICAL_INSPECTION", true, "FIRE_EXTINGUISHER", 10,
                "Check pressure gauge and seal on each floor."));

        template.addItem(createItem(2, "EXIT-01", "Exit Wayfinding Signage & Emergency Lighting",
                "Photoluminescent or illuminated exit markers along stairwells and corridors with min 90 minutes emergency lighting autonomy (NBC 2016 Part 4 Cl 4.16).",
                "MANDATORY", "PHYSICAL_INSPECTION", true, "EXIT_SIGN", 15,
                "Verify visibility in simulated blackout condition."));

        template.addItem(createItem(3, "OBS-01", "Staircase Enclosures Free of Combustible Encroachments",
                "Staircases and lobby exits maintained strictly clear of footwear racks, bicycle storage, garbage, or combustible domestic clutter (NBC 2016 Part 4 Cl 4.3).",
                "MANDATORY", "PHYSICAL_INSPECTION", true, "OBSTACLE", 20,
                "Inspect stairwell landings and refuge areas."));

        template.addItem(createItem(4, "DOOR-01", "Fire Check Doors in Staircase Shafts",
                "Staircase fire doors self-closing with minimum 1-hour fire resistance rating, closing tightly against frame stops (NBC 2016 Part 4 Cl 4.17).",
                "MANDATORY", "PHYSICAL_INSPECTION", true, "EXIT_DOOR", 15,
                "Ensure automatic door closers are active and doors are not propped open with wedges."));

        templateRepository.save(template);
    }

    private ChecklistTemplateItem createItem(int sequence, String code, String title, String description,
                                             String requirementType, String verificationMethod,
                                             boolean evidenceRequired, String aiCategoryTag,
                                             int penaltyPoints, String helpGuidance) {
        ChecklistTemplateItem item = new ChecklistTemplateItem();
        item.setItemSequence(sequence);
        item.setItemCode(code);
        item.setItemTitle(title);
        item.setParameterDescription(description);
        item.setRequirementType(requirementType);
        item.setVerificationMethod(verificationMethod);
        item.setEvidenceRequired(evidenceRequired);
        item.setAiCategoryTag(aiCategoryTag);
        item.setPenaltyPoints(penaltyPoints);
        item.setHelpGuidance(helpGuidance);
        return item;
    }
}
