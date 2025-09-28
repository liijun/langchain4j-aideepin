package com.moyz.adi.common.service;

import com.google.common.base.Joiner;
import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.cosntant.AdiConstant;
import com.moyz.adi.common.entity.AiModel;
import com.moyz.adi.common.entity.ModelPlatform;
import com.moyz.adi.common.helper.AsrModelContext;
import com.moyz.adi.common.helper.ImageModelContext;
import com.moyz.adi.common.helper.LLMContext;
import com.moyz.adi.common.helper.TtsModelContext;
import com.moyz.adi.common.searchengine.GoogleSearchEngineService;
import com.moyz.adi.common.searchengine.SearchEngineServiceContext;
import com.moyz.adi.common.service.languagemodel.*;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.moyz.adi.common.util.LocalCache.MODEL_ID_TO_OBJ;

@Slf4j
@Service
public class AiModelInitializer {

    @Resource
    private AdiProperties adiProperties;

    @Resource
    private ModelPlatformService modelPlatformService;

    private InetSocketAddress proxyAddress;

    private List<AiModel> all = new ArrayList<>();

    /**
     * 模型及其配置初始化
     *
     * @param allModels
     */
    public void init(List<AiModel> allModels) {
        this.all = allModels;
        for (AiModel model : all) {
            if (Boolean.TRUE.equals(model.getIsEnable())) {
                MODEL_ID_TO_OBJ.put(model.getId(), model);
            } else {
                MODEL_ID_TO_OBJ.remove(model.getId());
            }
        }
        if (adiProperties.getProxy().isEnable()) {
            proxyAddress = new InetSocketAddress(adiProperties.getProxy().getHost(), adiProperties.getProxy().getHttpPort());
        } else {
            proxyAddress = null;
        }

        Map<String, ModelPlatform> nameToPlatform = modelPlatformService.listAll().stream().collect(Collectors.toMap(ModelPlatform::getName, Function.identity(), (v1, v2) -> v1));
        initLLMServiceList(nameToPlatform);
        initImageModelServiceList(nameToPlatform);
        initAsrModelServiceList(nameToPlatform);
        initTtsModelServiceList(nameToPlatform);
    }

    /**
     * 初始化大语言模型列表
     */
    private synchronized void initLLMServiceList(Map<String, ModelPlatform> nameToPlatform) {

        // OpenAi api 兼容模型
        initOpenAiCompatibleService(nameToPlatform, (model, modelPlatformName) -> new OpenAiCompatibleLLMService(model, nameToPlatform.get(modelPlatformName)).setProxyAddress(proxyAddress));

        //deepseek
        initLLMService(AdiConstant.ModelPlatform.DEEPSEEK, model -> new DeepSeekLLMService(model, nameToPlatform.get(AdiConstant.ModelPlatform.DEEPSEEK)).setProxyAddress(proxyAddress));

        //openai
        initLLMService(AdiConstant.ModelPlatform.OPENAI, model -> new OpenAiLLMService(model, nameToPlatform.get(AdiConstant.ModelPlatform.OPENAI)).setProxyAddress(proxyAddress));

        //dashscope
        initLLMService(AdiConstant.ModelPlatform.DASHSCOPE, model -> new DashScopeLLMService(model, nameToPlatform.get(AdiConstant.ModelPlatform.DASHSCOPE)).setProxyAddress(proxyAddress));

        //qianfan
        initLLMService(AdiConstant.ModelPlatform.QIANFAN, model -> new QianFanLLMService(model, nameToPlatform.get(AdiConstant.ModelPlatform.QIANFAN)).setProxyAddress(proxyAddress));

        //ollama
        initLLMService(AdiConstant.ModelPlatform.OLLAMA, model -> new OllamaLLMService(model, nameToPlatform.get(AdiConstant.ModelPlatform.OLLAMA)));

        // 硅基流动
        initLLMService(AdiConstant.ModelPlatform.SILICONFLOW, model -> new SiliconflowLLMService(model, nameToPlatform.get(AdiConstant.ModelPlatform.SILICONFLOW)));
    }

    /**
     * 初始化文生图模型、搜索服务
     */
    private synchronized void initImageModelServiceList(Map<String, ModelPlatform> nameToPlatform) {
        //openai image model
        initImageModelService(AdiConstant.ModelPlatform.OPENAI, model -> new OpenAiDalleService(model, nameToPlatform.get(AdiConstant.ModelPlatform.OPENAI)).setProxyAddress(proxyAddress));


        initImageModelService(AdiConstant.ModelPlatform.DASHSCOPE, model -> new DashScopeWanxService(model, nameToPlatform.get(AdiConstant.ModelPlatform.DASHSCOPE)));

        //search engine
        SearchEngineServiceContext.addWebSearcher(AdiConstant.SearchEngineName.GOOGLE, new GoogleSearchEngineService(proxyAddress));
    }

    private synchronized void initAsrModelServiceList(Map<String, ModelPlatform> nameToPlatform) {
        initAsrModelService(AdiConstant.ModelPlatform.DASHSCOPE, model -> new DashScopeAsrService(model, nameToPlatform.get(AdiConstant.ModelPlatform.DASHSCOPE)));
        initAsrModelService(AdiConstant.ModelPlatform.SILICONFLOW, model -> new SiliconflowAsrService(model, nameToPlatform.get(AdiConstant.ModelPlatform.SILICONFLOW)));
    }

    private synchronized void initTtsModelServiceList(Map<String, ModelPlatform> nameToPlatform) {
        initTtsModelService(AdiConstant.ModelPlatform.DASHSCOPE, model -> new DashScopeTtsService(model, nameToPlatform.get(AdiConstant.ModelPlatform.DASHSCOPE)));
    }

    private void initLLMService(String platform, Function<AiModel, AbstractLLMService> function) {
        List<AiModel> models = all.stream().filter(item -> item.getType().equals(AdiConstant.ModelType.TEXT) && item.getPlatform().equals(platform)).toList();
        if (CollectionUtils.isEmpty(models)) {
            log.warn("{} service is disabled", platform);
        }
        LLMContext.clearByPlatform(platform);
        for (AiModel model : models) {
            log.info("add llm model,model:{}", model);
            LLMContext.addLLMService(function.apply(model));
        }
    }

    private void initOpenAiCompatibleService(Map<String, ModelPlatform> nameToPlatform, BiFunction<AiModel, String, AbstractLLMService> function) {
        log.info("init openai api compatible llm model");
        List<String> compatiblePlatforms = nameToPlatform.values().stream().filter(ModelPlatform::getIsOpenaiApiCompatible).map(ModelPlatform::getName).toList();
        for (String platform : compatiblePlatforms) {
            List<AiModel> models = all.stream().filter(item -> item.getType().equals(AdiConstant.ModelType.TEXT) && item.getPlatform().equals(platform)).toList();
            if (CollectionUtils.isEmpty(models)) {
                log.warn("{} service is disabled", Joiner.on(",").join(compatiblePlatforms));
            }
            for (AiModel model : models) {
                log.info("add openai api compatible llm model,model:{}", model);
                LLMContext.addLLMService(function.apply(model, platform));
            }
        }
    }

    private void initImageModelService(String platform, Function<AiModel, AbstractImageModelService> function) {
        List<AiModel> models = all.stream().filter(item -> item.getType().equals(AdiConstant.ModelType.IMAGE) && item.getPlatform().equals(platform)).toList();
        if (CollectionUtils.isEmpty(models)) {
            log.warn("{} service is disabled", platform);
        }
        ImageModelContext.clearByPlatform(platform);
        for (AiModel model : models) {
            log.info("add image model,model:{}", model);
            ImageModelContext.addImageModelService(function.apply(model));
        }

    }

    private void initAsrModelService(String platform, Function<AiModel, AbstractAsrModelService> function) {
        List<AiModel> models = all.stream().filter(item -> item.getType().equals(AdiConstant.ModelType.ASR) && item.getPlatform().equals(platform)).toList();
        if (CollectionUtils.isEmpty(models)) {
            log.warn("{} service is disabled", platform);
        }
        AsrModelContext.clearByPlatform(platform);
        for (AiModel model : models) {
            log.info("add asr model,model:{}", model);
            AsrModelContext.addService(function.apply(model));
        }
    }

    private void initTtsModelService(String platform, Function<AiModel, AbstractTtsModelService> function) {
        List<AiModel> models = all.stream().filter(item -> item.getType().equals(AdiConstant.ModelType.TTS) && item.getPlatform().equals(platform)).toList();
        if (CollectionUtils.isEmpty(models)) {
            log.warn("{} service is disabled", platform);
        }
        TtsModelContext.clearByPlatform(platform);
        for (AiModel model : models) {
            log.info("add tts model,model:{}", model);
            TtsModelContext.addService(function.apply(model));
        }
    }


    public void delete(AiModel aiModel) {
        LLMContext.remove(aiModel.getPlatform(), aiModel.getName());
        ImageModelContext.remove(aiModel.getName());
        MODEL_ID_TO_OBJ.remove(aiModel.getId());
    }

    public void addOrUpdate(AiModel aiModel) {
        AiModel existOne = all.stream().filter(item -> item.getId().equals(aiModel.getId())).findFirst().orElse(null);
        if (null == existOne) {
            all.add(aiModel);
        } else {
            BeanUtils.copyProperties(aiModel, existOne);
        }
        Map<String, ModelPlatform> nameToPlatform = modelPlatformService.listAll().stream().collect(Collectors.toMap(ModelPlatform::getName, Function.identity(), (v1, v2) -> v1));
        if (AdiConstant.ModelType.TEXT.equalsIgnoreCase(aiModel.getType())) {
            initLLMServiceList(nameToPlatform);
        } else {
            initImageModelServiceList(nameToPlatform);
        }
        MODEL_ID_TO_OBJ.put(aiModel.getId(), aiModel);
    }
}
