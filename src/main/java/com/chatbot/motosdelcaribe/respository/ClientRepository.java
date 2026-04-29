package com.chatbot.motosdelcaribe.respository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chatbot.motosdelcaribe.model.Client;

public interface ClientRepository extends JpaRepository<Client, String> {

    /**
     * Devuelve todos los contratos asociados a un telefono. Un mismo TELULT puede
     * aparecer en mas de un contrato (un cliente con varias motos), por eso retorna
     * lista en lugar de Optional.
     */
    List<Client> findByPhone(String phone);

    /**
     * Lookup principal del chatbot: identidad confirmada por (telefono + placa).
     */
    Optional<Client> findByPhoneAndNumPlate(String phone, String numPlate);

}
