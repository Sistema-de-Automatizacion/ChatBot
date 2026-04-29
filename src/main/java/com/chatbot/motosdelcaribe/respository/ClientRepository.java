package com.chatbot.motosdelcaribe.respository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chatbot.motosdelcaribe.model.Client;

public interface ClientRepository extends JpaRepository<Client, Integer> {

}
