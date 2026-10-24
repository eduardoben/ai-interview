package com.local.interviewassistant.data.llm

import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.Role

object SystemPromptHelper {
    fun createSystemPromptContents(systemPrompt: String): Contents {
        return Message.system(systemPrompt).contents
    }
}
