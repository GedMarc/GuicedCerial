package com.guicedee.cerial.enumerations;

import com.fasterxml.jackson.annotation.JsonCreator;


public enum BaudRate
{
    $300,
    $600,
    $1200,
    $4800,
    $9600,
    $14400,
    $19200,
    $38400,
    $57600,
    $115200,
    $128000,
    $256000;

    @Override
    public String toString()
    {
        return name().replace("$", "");
    }

    public int toInt()
    {
        return Integer.parseInt(toString());
    }

    @JsonCreator
    public static BaudRate from(String s)
    {
        return BaudRate.valueOf("$" + s.toUpperCase());
    }

}
