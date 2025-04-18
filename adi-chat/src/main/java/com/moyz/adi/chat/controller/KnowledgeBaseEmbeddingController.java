package com.moyz.adi.chat.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyz.adi.common.dto.KbItemEmbeddingDto;
import com.moyz.adi.common.service.embedding.IEmbeddingService;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/knowledge-base-embedding")
@Validated
public class KnowledgeBaseEmbeddingController {

    @Resource
    private IEmbeddingService iEmbeddingService;

    @GetMapping("/list/{kbItemUuid}")
    public Page<KbItemEmbeddingDto> list(@PathVariable String kbItemUuid, int currentPage, int pageSize) {
        return iEmbeddingService.listByItemUuid(kbItemUuid, currentPage, pageSize);
    }
}
