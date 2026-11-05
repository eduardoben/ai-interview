package com.edgecue.app.data.llm

import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Message

object SystemPromptHelper {
    fun createSystemPromptContents(systemPrompt: String): Contents {
        return Message.system(systemPrompt).contents
    }
}
