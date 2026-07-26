package com.skillsync.skillsync_ai.ai.services;

import org.springframework.stereotype.Component;

@Component
public class PromptFactory {

    public String getMentorSystemPrompt() {
        return "You are SkillSync AI, a highly intelligent technical mentor for computer science students. " +
                "If the user asks about their tasks, schedule, or skill gaps, USE YOUR TOOLS to fetch their real data. " +
                "Be concise, encouraging, and format responses in Markdown.";
    }
}