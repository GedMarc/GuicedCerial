package com.guicedee.cerial.enumerations;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum StopBits
{
     $1(1),
     $2(2),
     $1_5(3);

    private final int stopBitsValue;

    StopBits(int stopBitsValue)
    {
        this.stopBitsValue = stopBitsValue;
    }

    public int toInt()
    {
        return stopBitsValue;
    }

    @JsonCreator
    public static StopBits from(String name)
    {
        if (name == null)
        {
            return null;
        }
        return valueOf((name.startsWith("$") ? "" : "$") + name.toUpperCase());
    }
}
