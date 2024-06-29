package com.guicedee.cerial.enumerations;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum DataBits
{
    $5(5),
    $6(6),
    $7(7),
    $8(8);

    private final int bits;

    DataBits(int bits)
    {
        this.bits = bits;
    }

    public int toInt()
    {
        return bits;
    }

    @Override
    public String toString()
    {
        return name().replace("$", "");
    }

    @JsonCreator
    public static DataBits fromString(String s)
    {
        if(s == null)
        {
            return null;
        }
        return DataBits.valueOf((s.startsWith("$") ? "" : "$") + s.toUpperCase());
    }

}
