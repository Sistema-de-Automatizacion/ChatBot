package com.chatbot.motosdelcaribe.model;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.annotation.Id;

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
    private int numId;
    private String numPlate;
    private String name;
    private double balance;
    private double payment;
    private double acummulatedDebet;

}
