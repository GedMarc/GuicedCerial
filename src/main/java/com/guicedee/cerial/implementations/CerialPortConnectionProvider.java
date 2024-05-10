package com.guicedee.cerial.implementations;

import com.google.inject.Provider;
import com.guicedee.cerial.CerialPortConnection;
import com.guicedee.cerial.enumerations.BaudRate;

public class CerialPortConnectionProvider implements Provider<CerialPortConnection>
{
    private final int comPortNumber;

    public CerialPortConnectionProvider(int comPortNumber)
    {
        this.comPortNumber = comPortNumber;
    }
    @Override

    public CerialPortConnection get()
    {
        CerialPortConnection cerialPortConnection = new CerialPortConnection(comPortNumber, BaudRate.$9600);
        return cerialPortConnection;
    }

}
