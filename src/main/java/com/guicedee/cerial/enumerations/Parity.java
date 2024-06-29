package com.guicedee.cerial.enumerations;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.apache.commons.lang3.math.NumberUtils;

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
    public static Parity from(String value)
    {
        if (NumberUtils.isCreatable(value))
        {
            for (Parity parity : Parity.values())
            {
                if (parity.parity == Integer.parseInt(value))
                {
                    return parity;
                }
            }
        }
        else {
            for (Parity parity : Parity.values())
            {
                if (parity.name()
                          .equalsIgnoreCase(value))
                {
                    return parity;
                }
            }

        }
        return null;
    }
}
