package com.moyz.adi.common.workflow.node.knowledgeretrieval;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moyz.adi.common.cosntant.AdiConstant;
import com.moyz.adi.common.entity.WorkflowComponent;
import com.moyz.adi.common.entity.WorkflowNode;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.rag.EmbeddingRag;
import com.moyz.adi.common.util.JsonUtil;
import com.moyz.adi.common.util.SpringUtil;
import com.moyz.adi.common.vo.RetrieverCreateParam;
import com.moyz.adi.common.workflow.NodeProcessResult;
import com.moyz.adi.common.workflow.WfNodeState;
import com.moyz.adi.common.workflow.WfState;
import com.moyz.adi.common.workflow.data.NodeIOData;
import com.moyz.adi.common.workflow.node.AbstractWfNode;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsIn;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

import static com.moyz.adi.common.cosntant.AdiConstant.MetadataKey.KB_UUID;
import static com.moyz.adi.common.cosntant.AdiConstant.RAG_RETRIEVE_MIN_SCORE_DEFAULT;
import static com.moyz.adi.common.cosntant.AdiConstant.WorkflowConstant.DEFAULT_OUTPUT_PARAM_NAME;
import static com.moyz.adi.common.enums.ErrorEnum.*;
import static com.moyz.adi.common.enums.ErrorEnum.B_BREAK_SEARCH;

/**
 * 【节点】知识抽取 <br/>
 * 节点内容固定格式：KnowledgeRetrievalNodeConfig
 */
@Slf4j
public class KnowledgeRetrievalNode extends AbstractWfNode {

    public KnowledgeRetrievalNode(WorkflowComponent wfComponent, WorkflowNode nodeDef, WfState wfState, WfNodeState nodeState) {
        super(wfComponent, nodeDef, wfState, nodeState);
    }

    /**
     * nodeConfig格式：<br/>
     * {"knowledge_base_uuid": "","score":0.6,"top_n":3,"is_strict": false, "default_response":"数据不存在~~~"}<br/>
     */
    @Override
    public NodeProcessResult onProcess() {
        ObjectNode objectConfig = node.getNodeConfig();
        if (objectConfig.isEmpty()) {
            throw new BaseException(A_WF_NODE_CONFIG_NOT_FOUND);
        }
        KnowledgeRetrievalNodeConfig nodeConfigObj = JsonUtil.fromJson(objectConfig, KnowledgeRetrievalNodeConfig.class);
        if (null == nodeConfigObj || StringUtils.isBlank(nodeConfigObj.getKnowledgeBaseUuid())) {
            log.warn("找不到知识检索节点的配置");
            throw new BaseException(A_WF_NODE_CONFIG_ERROR);
        }
        String kbUuid = nodeConfigObj.getKnowledgeBaseUuid();
        log.info("KnowledgeRetrievalNode config:{}", nodeConfigObj);
        String textInput = getFirstInputText();
        if (StringUtils.isBlank(textInput)) {
            log.warn("输入内容为空");
            return NodeProcessResult
                    .builder()
                    .content(List.of(NodeIOData.createByText(DEFAULT_OUTPUT_PARAM_NAME, "", "")))
                    .build();
        }
        RetrieverCreateParam kbRetrieveParam = RetrieverCreateParam.builder()
                .filter(new IsEqualTo(AdiConstant.MetadataKey.KB_UUID, kbUuid))
                .maxResults(nodeConfigObj.getTopN())
                .minScore(nodeConfigObj.getScore())
                .breakIfSearchMissed(nodeConfigObj.getIsStrict())
                .build();
        EmbeddingRag embeddingRag = SpringUtil.getBean(EmbeddingRag.class);
        ContentRetriever retriever = embeddingRag.createRetriever(kbRetrieveParam);
        StringBuilder resp = new StringBuilder();
        try {
            List<Content> contents = retriever.retrieve(Query.from(textInput));
            for (Content content : contents) {
                resp.append(content.textSegment().text());
            }
        } catch (BaseException e) {
            if (B_BREAK_SEARCH.getCode().equals(e.getCode())) {
                log.warn(B_BREAK_SEARCH.getInfo());
            } else {
                log.error("KnowledgeRetrievalNode retrieve error", e);
                throw e;
            }
        }
        String respText = resp.toString();
        if (StringUtils.isBlank(respText) && StringUtils.isNotBlank(nodeConfigObj.getDefaultResponse())) {
            respText = nodeConfigObj.getDefaultResponse();
        }
        return NodeProcessResult
                .builder()
                .content(List.of(NodeIOData.createByText(DEFAULT_OUTPUT_PARAM_NAME, "", respText)))
                .build();
    }
}
