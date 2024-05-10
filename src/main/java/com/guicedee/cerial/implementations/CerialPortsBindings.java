package com.guicedee.cerial.implementations;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import com.guicedee.cerial.CerialPortConnection;
import com.guicedee.guicedinjection.interfaces.IGuiceModule;
import lombok.extern.java.Log;

/**
 * Binds the range of ports to a singleton provider that will control the injection of the ports
 */
@Log
public class CerialPortsBindings extends AbstractModule implements IGuiceModule<CerialPortsBindings>
{
    @Override
    protected void configure()
    {
        for (int i = 0; i < 1000; i++)
        {
            bind(Key.get(CerialPortConnection.class, Names.named(i + ""))).toProvider(new CerialPortConnectionProvider(i))
                                                                          .in(Singleton.class);
        }
        log.fine("Bound the collection of possible serial port numbers to a dynamic singleton provider");
    }


}
