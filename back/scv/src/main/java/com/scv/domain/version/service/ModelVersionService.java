package com.scv.domain.version.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scv.domain.data.domain.Data;
import com.scv.domain.data.enums.DataSet;
import com.scv.domain.data.exception.DataNotFoundException;
import com.scv.domain.data.repository.DataRepository;
import com.scv.domain.model.domain.Model;
import com.scv.domain.model.dto.response.ModelCreateResponse;
import com.scv.domain.model.exception.ModelNotFoundException;
import com.scv.domain.model.repository.ModelRepository;
import com.scv.global.util.UrlUtil;
import com.scv.global.oauth2.auth.CustomOAuth2User;
import com.scv.domain.result.domain.Result;
import com.scv.domain.result.dto.request.ResultRequest;
import com.scv.domain.result.dto.response.ResultResponse;
import com.scv.domain.result.dto.response.ResultResponseWithImages;
import com.scv.domain.result.exception.ResultNotFoundException;
import com.scv.domain.result.repository.ResultRepository;
import com.scv.domain.version.domain.ModelVersion;
import com.scv.domain.version.dto.layer.LayerDTO;
import com.scv.domain.version.dto.request.ModelVersionRequest;
import com.scv.domain.version.dto.response.ModelVersionDetail;
import com.scv.domain.version.dto.response.ModelVersionDetailWithResult;
import com.scv.domain.version.dto.response.ModelVersionOnWorking;
import com.scv.domain.version.exception.ModelVersionNotFoundException;
import com.scv.domain.version.repository.ModelVersionRepository;
import com.scv.global.util.ParsingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.BadRequestException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ModelVersionService {

    private final ModelRepository modelRepository;
    private final ModelVersionRepository modelVersionRepository;
    private final ResultRepository resultRepository;
    private final DataRepository dataRepository;
    private final UrlUtil urlUtil;

    // 모델 버전 생성
    public ModelCreateResponse createModelVersion(Long modelId, Long modelVersionId, CustomOAuth2User user) throws BadRequestException {
        Model model = modelRepository.findById(modelId).orElseThrow(ModelNotFoundException::new);
        ModelVersion modelVersion = modelVersionRepository.findById(modelVersionId).orElseThrow(ModelNotFoundException::new);
        if (user.getUserId() != model.getUser().getUserId()) {
            throw new BadRequestException("모델의 제작자만 생성할 수 있습니다.");
        }

        ModelVersion newModelVersion = ModelVersion.builder()
                .model(model)
                .versionNo(0)
                .layers(modelVersion.getLayers())
                .build();

        modelVersionRepository.save(newModelVersion);
        return new ModelCreateResponse(newModelVersion);
    }


    // 모델버전 상세 조회
    @Transactional(readOnly = true)
    public ModelVersionDetail getModelVersion(Long versionId) {
        ModelVersion version = modelVersionRepository.findById(versionId).orElseThrow(ModelVersionNotFoundException::new);

        Optional<Result> result = resultRepository.findById(versionId);
        if (result.isPresent()) {
            ResultResponseWithImages resultResponseWithImages = new ResultResponseWithImages(result.get());
            return new ModelVersionDetailWithResult(version, resultResponseWithImages);
        }

        return new ModelVersionDetail(version);
    }


    // 개발중인 모델 조회
    @Transactional(readOnly = true)
    public Page<ModelVersionOnWorking> getModelVersionsOnWorking(CustomOAuth2User user, Pageable pageable, String modelName, DataSet dataName) {
        Page<ModelVersion> modelVersions = modelVersionRepository.findAllByUserAndIsWorkingTrueAndDeletedFalse(modelName, dataName, user.getUserId(), pageable);

        return modelVersions.map(ModelVersionOnWorking::new);
    }


    // 모델 버전 수정
    public void updateModelVersion(Long modelVersionId, ModelVersionRequest request, CustomOAuth2User user) throws BadRequestException {
        ModelVersion modelVersion = modelVersionRepository.findById(modelVersionId)
                .orElseThrow(ModelVersionNotFoundException::new);

        // 사용자 권한 검사
        if (user.getUserId() != modelVersion.getModel().getUser().getUserId()) {
            throw new BadRequestException("제작자만 수정할 수 있습니다.");
        }

        String layersJson = ParsingUtil.toJson(request.layers());

        // 모델 버전 정보 업데이트
        modelVersion.updateLayers(layersJson);
        Optional<Result> result = resultRepository.findById(modelVersionId);

        result.ifPresent(resultRepository::delete);
    }


    // 모델 버전 삭제
    public void deleteModelVersion(Long modelVersionId, CustomOAuth2User user) throws BadRequestException {
        ModelVersion modelVersion = modelVersionRepository.findById(modelVersionId)
                .orElseThrow(ModelVersionNotFoundException::new);
        if (!user.getUserId().equals(modelVersion.getModel().getUser().getUserId())) {
            throw new BadRequestException("제작자만 삭제할 수 있습니다.");
        }

        Model model = modelVersion.getModel();

        modelVersionRepository.softDeleteById(modelVersionId);
        /**
         * 벡터 DB 삭제 로직
         */
        String url = String.format("http://fast-search-service.scv.svc.cluster.local:8001/fast/v1/model/match/%d/%d", model.getId(), modelVersionId);
        RestTemplate restTemplate = new RestTemplate();
        try {
            restTemplate.delete(url);
            log.info("vectorDB 삭제 성공");
        } catch (RestClientException e) {
            log.error("vectorDB 삭제 실패: model_{}_v{}", model.getId(), modelVersionId);
        }
        /**
         * 벡터 DB 삭제 로직
         */

        // Result가 존재하는 경우에만 소프트 삭제
        Optional<Result> result = resultRepository.findByIdAndDeletedFalse(modelVersionId);
        result.ifPresent(r -> resultRepository.softDeleteByModelVersionId(modelVersionId));

        // 버전, 정확도관리
        if (model.getLatestVersion() != 0) {
            List<ModelVersion> modelVersionList = modelVersionRepository.findAllByModelIdAndDeletedFalse(model.getId());
            modelVersionList.sort(Comparator.comparingInt(ModelVersion::getVersionNo).reversed());

            if (!modelVersionList.isEmpty()) {
                for (int i = 0; i < modelVersionList.size(); i++) {
                    if (modelVersionList.get(i).getResult() != null) {
                        model.setAccuracy(modelVersionList.get(i).getResult().getTestAccuracy());
                        model.setLatestVersion(modelVersionList.get(i).getVersionNo());
                        break;
                    }
                }
            } else {
                model.setLatestVersion(0);
                model.setAccuracy(-1.0);
            }
        }
        modelVersionRepository.save(modelVersion);
    }


    // 모델 실행 및 저장
    public ResultResponse runResult(Long modelVersionId) {
        ModelVersion modelVersion = modelVersionRepository.findById(modelVersionId)
                .orElseThrow(ModelVersionNotFoundException::new);
        Data data = dataRepository.findById(modelVersion.getModel().getData().getId())
                .orElseThrow(DataNotFoundException::new);

        List<LayerDTO> layers = ParsingUtil.parseJsonToList(modelVersion.getLayers(), LayerDTO.class);
        ResultRequest request = new ResultRequest(layers, data);
        String url = urlUtil.getTrainUrl(modelVersion.getModel().getId(), modelVersionId);

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
        String jsonResponse = response.getBody();

        JsonNode rootNode = ParsingUtil.parseJsonToNode(jsonResponse);
        JsonNode testResults = rootNode.path("test_results").path("results");

        double finalTestAccuracy = testResults.path("final_test_accuracy").asDouble(0.0);
        double finalTestLoss = testResults.path("final_test_loss").asDouble(0.0);

        String modelCode = testResults.path("model_code").asText();
        String codeJson = ParsingUtil.toJson(modelCode);

        String layerParams = ParsingUtil.toJson(testResults.path("layer_parameters"));

        ObjectNode trainInfoNode = new ObjectMapper().createObjectNode();
        trainInfoNode.set("train_result_per_epoch", testResults.path("train_result_per_epoch"));
        trainInfoNode.set("training_history", testResults.path("training_history"));

        String trainInfo = trainInfoNode.toString();

        int totalParams = calculateTotalParams(testResults.path("layer_parameters"));

        Optional<Result> existingResult = resultRepository.findByIdWithLock(modelVersionId);
        Result result;
        if (existingResult.isPresent()) {
            result = existingResult.get();
            result.updateResult(codeJson, finalTestAccuracy, finalTestLoss, trainInfo, layerParams, totalParams);
        } else {
            result = Result.builder()
                    .modelVersion(modelVersion)
                    .code(codeJson)
                    .testAccuracy(finalTestAccuracy)
                    .testLoss(finalTestLoss)
                    .layerParams(layerParams)
                    .trainInfo(trainInfo)
                    .totalParams(totalParams)
                    .build();
        }

        resultRepository.save(result);

        return new ResultResponse(result);
    }


    // 결과 및 분석 저장
    public ResultResponseWithImages saveResult(Long modelVersionId) {
        ModelVersion modelVersion = modelVersionRepository.findById(modelVersionId)
                .orElseThrow(ModelVersionNotFoundException::new);
        Result result = resultRepository.findById(modelVersionId).orElseThrow(ResultNotFoundException::new);
        Model model = modelVersion.getModel();
        Long modelId = model.getId();
        String data = model.getData().getName().toString();
        if ("Fashion".equals(data)) {
            data += "_MNIST";
        }

        String url = urlUtil.getTestUrl(modelId, modelVersionId, data);

        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        String jsonResponse = response.getBody();

        JsonNode rootNode = ParsingUtil.parseJson(jsonResponse, JsonNode.class);

        result.updateAnalysis(
                ParsingUtil.getJsonFieldAsString(rootNode, "confusion_matrix"),
                ParsingUtil.getJsonFieldAsString(rootNode, "example_image"),
                ParsingUtil.getJsonFieldAsString(rootNode, "feature_activation"),
                ParsingUtil.getJsonFieldAsString(rootNode, "activation_maximization")
        );
        resultRepository.save(result);

        int latest = model.getLatestVersion();
        double accuarcy = result.getTestAccuracy();

        if (latest == 0) {
            modelVersion.updateVersionNo(1);
            model.setLatestVersion(1);
            model.setAccuracy(accuarcy);
        } else {
            if (modelVersion.getVersionNo() == latest) {
                model.setAccuracy(accuarcy);
            } else if (modelVersion.getVersionNo() == 0) {
                modelVersion.updateVersionNo(latest + 1);
                model.setLatestVersion(latest + 1);
                model.setAccuracy(accuarcy);
            }
        }
        modelVersion.workingDone();

        modelVersionRepository.save(modelVersion);
        modelRepository.save(model);

        return new ResultResponseWithImages(result);
    }


    private int calculateTotalParams(JsonNode layerParameters) {
        int totalParams = 0;
        for (JsonNode paramNode : layerParameters) {
            totalParams += paramNode.asInt();
        }
        return totalParams;
    }
}
