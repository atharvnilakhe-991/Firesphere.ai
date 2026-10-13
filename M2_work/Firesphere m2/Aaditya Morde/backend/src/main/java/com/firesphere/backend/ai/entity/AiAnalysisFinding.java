package com.firesphere.backend.ai.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "ai_analysis_findings")
public class AiAnalysisFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    private AiAnalysisRun run;

    @Column(nullable = false)
    private String finding;

    @Column(nullable = false)
    private Double confidence;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public AiAnalysisRun getRun() { return run; }
    public void setRun(AiAnalysisRun run) { this.run = run; }
    public String getFinding() { return finding; }
    public void setFinding(String finding) { this.finding = finding; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
}
