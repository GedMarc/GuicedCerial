package com.guicedee.cerial.implementations;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortEvent;
import com.fazecast.jSerialComm.SerialPortMessageListener;
import com.guicedee.cerial.CerialPortConnection;
import com.guicedee.client.CallScoper;
import com.guicedee.client.IGuiceContext;
import com.guicedee.guicedservlets.websockets.options.CallScopeProperties;
import com.guicedee.guicedservlets.websockets.options.CallScopeSource;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;

import java.text.MessageFormat;
import java.util.function.BiConsumer;
import java.util.logging.Level;

import static com.fazecast.jSerialComm.SerialPort.LISTENING_EVENT_DATA_RECEIVED;
import static com.guicedee.cerial.enumerations.ComPortStatus.Running;
import static lombok.AccessLevel.PRIVATE;

@Getter
@Setter
@Log
public class DataSerialPortMessageListener implements SerialPortMessageListener {

    @JsonIgnore
    @Getter(PRIVATE)
    private BiConsumer<byte[], SerialPort> comPortRead;
    @JsonIgnore
    @Getter(PRIVATE)
    private SerialPort comPort;
    @JsonIgnore
    @Getter(PRIVATE)
    private CerialPortConnection<?> connection;
    @JsonIgnore
    @Getter(PRIVATE)
    private char delimiter;

    public DataSerialPortMessageListener(char delimiter, SerialPort comPort, CerialPortConnection<?> connection) {
        this.delimiter = delimiter;
        this.comPort = comPort;
        this.connection = connection;
    }

    @Override
    public byte[] getMessageDelimiter() {
        return new byte[]{(byte) delimiter};
    }

    @Override
    public boolean delimiterIndicatesEndOfMessage() {
        return true;
    }

    @Override
    public int getListeningEvents() {
        return LISTENING_EVENT_DATA_RECEIVED;
    }

    @Override
    public void serialEvent(SerialPortEvent event) {
        if (event.getEventType() != LISTENING_EVENT_DATA_RECEIVED) {
            return;
        }
        byte[] newData =  event.getReceivedData();
     //   int numRead = comPort.readBytes(newData, newData.length);
     //   System.out.println("Read " + newData.length + " bytes.");
        var callScoper = IGuiceContext.get(CallScoper.class);
        callScoper.enter();
        try {
            CallScopeProperties properties = IGuiceContext.get(CallScopeProperties.class);
            properties.setSource(CallScopeSource.SerialPort);
            properties.getProperties()
                    .put("ComPort", comPort);
            properties.getProperties()
                    .put("CerialPortConnection", this);
            getConnection().setComPortStatus(Running);
           // log.warning(MessageFormat.format("RX : {0}", new String(newData)));
            if (comPortRead != null) {
                comPortRead.accept(newData, comPort);
            }
        }catch (Throwable T)
        {
            log.log(Level.SEVERE,"Error on ComPort [" + connection.getComPort() + "] Receipt",T);
        }
        finally {
            callScoper.exit();
        }
    }
}
