package com.scv.domain.version.domain;

import com.scv.domain.model.domain.Model;
import com.scv.domain.result.domain.Result;
import com.scv.global.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Table(name = "model_version")
@Entity
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ModelVersion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "model_version_id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_id", nullable = false)
    private Model model;

    @OneToOne(mappedBy = "modelVersion", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    private Result result;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(name = "version_layer_at", columnDefinition = "JSON")
    private String layers;

    @Builder.Default
    @Column(name = "is_working_on", nullable = false, columnDefinition = "TINYINT(1)")
    private boolean isWorkingOn = true;


    /**
     *
     */
    public void updateVersionNo(int versionNo) {
        this.versionNo = versionNo;
    }

    /**
     * 레이어 수정
     *
     * @param layers
     */
    public void updateLayers(String layers) {
        this.layers = layers;
    }

    /**
     * 작업 상태 반전 (진행 중 또는 완료 상태 전환)
     */
    public void workingDone() {
        this.isWorkingOn = false;
    }

}

