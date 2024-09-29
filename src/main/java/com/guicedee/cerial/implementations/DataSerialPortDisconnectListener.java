package com.guicedee.cerial.implementations;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import com.fazecast.jSerialComm.SerialPortMessageListener;
import com.guicedee.cerial.CerialPortConnection;
import com.guicedee.cerial.SerialPortException;
import com.guicedee.cerial.enumerations.ComPortStatus;
import com.guicedee.client.CallScoper;
import com.guicedee.client.IGuiceContext;
import com.guicedee.guicedservlets.websockets.options.CallScopeProperties;
import com.guicedee.guicedservlets.websockets.options.CallScopeSource;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;

import java.util.function.BiConsumer;
import java.util.logging.Level;

import static com.fazecast.jSerialComm.SerialPort.LISTENING_EVENT_DATA_RECEIVED;
import static com.fazecast.jSerialComm.SerialPort.LISTENING_EVENT_PORT_DISCONNECTED;
import static com.guicedee.cerial.enumerations.ComPortStatus.Offline;
import static com.guicedee.cerial.enumerations.ComPortStatus.Running;
import static lombok.AccessLevel.PRIVATE;

@Getter
@Setter
@Log
public class DataSerialPortDisconnectListener implements SerialPortDataListener {

    private SerialPort comPort;
    private CerialPortConnection<?> connection;

    public DataSerialPortDisconnectListener(SerialPort comPort, CerialPortConnection<?> connection) {
        this.comPort = comPort;
        this.connection = connection;
    }

    @Override
    public int getListeningEvents() {
        return LISTENING_EVENT_PORT_DISCONNECTED;
    }

    @Override
    public void serialEvent(SerialPortEvent event) {
        if (event.getEventType() != LISTENING_EVENT_PORT_DISCONNECTED) {
            return;
        }
        log.log(Level.SEVERE, event.toString());
        connection.setComPortStatus(Offline);
        //connection.disconnect();
    }

}
