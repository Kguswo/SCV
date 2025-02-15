package com.scv.global.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class UrlUtil {

    @Value(("${spring.fastapi.train.host}"))
    private String fastTrainHost;

    @Value("${spring.fastapi.train.port}")
    private String fastTrainPort;

    @Value("${spring.fastapi.test.host}")
    private String fastTestHost;

    @Value("${spring.fastapi.test.port}")
    private String fastTestPort;

    /**
     * @return "http://{fastTrainHost}:{fastTrainPort}/fast/v1/models/{modelId}/versions/{modelVersionId}"
     */
    public String getTrainUrl(Long modelId, Long modelVersionId) {
        return String.format("http://%s:%s/fast/v1/models/%d/versions/%d", fastTrainHost, fastTrainPort, modelId, modelVersionId);
    }

    /**
     * @return "http://{fastTestHost}:{fastTestPort}/fast/v1/model/test/analyze/{modelId}/{modelVersionId}/{dataName}"
     */
    public String getTestUrl(Long modelId, Long modelVersionId, String dataName) {
        return String.format("http://%s:%s/fast/v1/model/test/analyze/%d/%d/%s", fastTestHost, fastTestPort, modelId, modelVersionId, dataName.toLowerCase());
    }
}
