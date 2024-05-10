package com.guicedee.cerial.enumerations;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum Parity
{
     None(0),
     Odd(1),
     Even(2),
     Mark(3),
     Space(4);

    private final int parity;

    Parity(int parity)
    {
        this.parity = parity;
    }

    public int toInt()
    {
        return parity;
    }

    @JsonCreator
    public static Parity from(Integer value)
    {
        for (Parity parity : Parity.values())
        {
            if (parity.parity == value)
            {
                return parity;
            }
        }
        return null;
    }
}
