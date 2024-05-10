package com.guicedee.cerial.test;

import com.google.inject.Key;
import com.google.inject.name.Names;
import com.guicedee.cerial.CerialPortConnection;
import com.guicedee.client.IGuiceContext;
import gnu.io.UnsupportedCommOperationException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CerialPortConnectionTest
{
    public static void main(String[] args) throws UnsupportedCommOperationException, IOException, InterruptedException
    {
        new CerialPortConnectionTest().getSerialPort();

    }

    @Test
    void getSerialPort() throws UnsupportedCommOperationException, InterruptedException, IOException
    {
        CerialPortConnection comPort5 = IGuiceContext.get(Key.get(CerialPortConnection.class, Names.named("5")));
        assertEquals(comPort5, IGuiceContext.get(Key.get(CerialPortConnection.class, Names.named("5"))), "Singleton for serial port provider was not applied");
        //CerialPortConnection comPort5 = new CerialPortConnection(5, BaudRate.$9600,10);
        comPort5.onComPortStatusUpdate((port, status) -> {
            System.out.println("Port [" + port.getComPort() + "] Status [" + status + "]");
        });
        comPort5.setComPortRead((bytes, port) -> {
            System.out.println("Received - " + new String(bytes))
            ;
        });
        comPort5.setComPortError((exception,connection,status)->{
            System.out.println("Connection error - " + exception + ": " + connection + " / New Status " + status);
        });
        CerialPortConnection connect = comPort5.connect();
        while (connect.getLastMessageTime() == null)
        {
            connect.write("Hello",true);
            TimeUnit.SECONDS.sleep(5);
        }
        assertTrue(connect.getLastMessageTime() != null);
    }

    @Test
    void testPort()
    {
        //Mockito.mock()
    }


}