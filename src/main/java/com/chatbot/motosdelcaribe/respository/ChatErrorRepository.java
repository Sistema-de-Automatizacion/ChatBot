package com.chatbot.motosdelcaribe.respository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chatbot.motosdelcaribe.model.ChatError;

public interface ChatErrorRepository extends JpaRepository<ChatError, Long> {
}
