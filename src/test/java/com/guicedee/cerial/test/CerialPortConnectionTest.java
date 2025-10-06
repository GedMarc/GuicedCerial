package com.guicedee.cerial.test;

import com.fazecast.jSerialComm.SerialPort;
import com.google.inject.Key;
import com.google.inject.name.Names;
import com.guicedee.cerial.CerialPortConnection;
import com.guicedee.client.IGuiceContext;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Disabled;

@Disabled("Requires real serial hardware; excluded from automated builds")
class CerialPortConnectionTest {
    public static void main(String[] args) throws IOException, InterruptedException {
        new CerialPortConnectionTest().getSerialPort();

    }

    @Test
    void jSerialComm() {
        SerialPort[] commPorts = SerialPort.getCommPorts();
        SerialPort.getCommPort("8");
        SerialPort comPort = SerialPort.getCommPorts()[0];
        comPort.openPort();
        comPort.closePort();
    }

    @Test
    void getSerialPort() throws InterruptedException {
        CerialPortConnection comPort5 = IGuiceContext.get(Key.get(CerialPortConnection.class, Names.named("98")));
        CerialPortConnection comPort7 = IGuiceContext.get(Key.get(CerialPortConnection.class, Names.named("99")));
        //CerialPortConnection comPort9 = IGuiceContext.get(Key.get(CerialPortConnection.class, Names.named("9")));
        assertEquals(comPort5, IGuiceContext.get(Key.get(CerialPortConnection.class, Names.named("98"))), "Singleton for serial port provider was not applied");
        //CerialPortConnection comPort5 = new CerialPortConnection(5, BaudRate.$9600,10);
        //CerialPortConnection connect = getConnection(comPort5);
        //CerialPortConnection connect7 = getConnection(comPort7);
        //CerialPortConnection connect8 = getConnection(comPort9);
        try {
            getConnection(comPort5);
            getConnection(comPort7);
            comPort5.connect();
            comPort7.connect();
            while (comPort7.getLastMessageTime() == null) {
                comPort5.write("Hello\n", true);
                TimeUnit.SECONDS.sleep(5);
            }
            assertTrue(comPort7.getLastMessageTime() != null);
        }finally {
            if(comPort5 != null) {
                comPort5.disconnect();
            }
            if(comPort7 != null) {
                comPort7.disconnect();
            }

        }
    }

    private static CerialPortConnection<?> getConnection(CerialPortConnection<?> comPort5) {
        comPort5.onComPortStatusUpdate((port, status) -> {
            System.out.println("Port [" + port.getComPort() + "] Status [" + status + "]");
        });
        comPort5.setComPortRead((bytes, port) -> {
            System.out.println("Received - " + new String(bytes));
        });
        comPort5.setComPortError((exception, connection, status) -> {
            System.out.println("Connection error - " + exception + ": " + connection + " / New Status " + status);
        });
        //comPort5.configureForRTS();

        return comPort5;
    }

    @Test
    void testPort() {
        //Mockito.mock()
    }


}