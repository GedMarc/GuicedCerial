package com.guicedee.cerial.implementations;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import com.guicedee.cerial.CerialPortConnection;
import com.guicedee.cerial.SerialPortException;
import com.guicedee.cerial.enumerations.ComPortStatus;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;

import java.util.logging.Level;

import static com.fazecast.jSerialComm.SerialPort.LISTENING_EVENT_BREAK_INTERRUPT;
import static com.fazecast.jSerialComm.SerialPort.LISTENING_EVENT_PORT_DISCONNECTED;
import static com.guicedee.cerial.enumerations.ComPortStatus.Offline;

@Getter
@Setter
@Log
public class DataSerialPortErrorBreakInterruptListener implements SerialPortDataListener {

    private SerialPort comPort;
    private CerialPortConnection<?> connection;

    public DataSerialPortErrorBreakInterruptListener(SerialPort comPort, CerialPortConnection<?> connection) {
        this.comPort = comPort;
        this.connection = connection;
    }

    @Override
    public int getListeningEvents() {
        return LISTENING_EVENT_BREAK_INTERRUPT;
    }

    @Override
    public void serialEvent(SerialPortEvent event) {
        if (event.getEventType() != LISTENING_EVENT_BREAK_INTERRUPT) {
            return;
        }
        log.log(Level.SEVERE, event.toString());
//        connection.setComPortStatus(Offline);
        connection.onConnectError(new SerialPortException("Break Interrupt - " + event.toString()), ComPortStatus.GeneralException);
    }

}
