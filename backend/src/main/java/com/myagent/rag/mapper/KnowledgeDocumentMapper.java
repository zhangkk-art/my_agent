package com.myagent.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.myagent.rag.model.KnowledgeDocument;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocument> {
}
