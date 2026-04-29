package com.chatbot.motosdelcaribe.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Client {

    @Id
    private Integer numId;
    private String numPlate;
    private String name;
    private double balance;
    private double payment;
    private double acummulatedDebet;

}
