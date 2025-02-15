package com.scv.domain.version.dto.response;

import com.scv.domain.version.domain.ModelVersion;

public record ModelVersionResponse(
        Long versionId,
        int versionNo
) {
    public ModelVersionResponse(ModelVersion modelVersion) {
        this(
                modelVersion.getId(),
                modelVersion.getVersionNo()
        );
    }
}
