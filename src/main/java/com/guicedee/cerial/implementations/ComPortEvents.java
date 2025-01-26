package com.guicedee.cerial.implementations;

import com.fazecast.jSerialComm.SerialPortDataListener;

public interface ComPortEvents
{
    java.util.function.BiConsumer<byte[], com.fazecast.jSerialComm.SerialPort> getComPortRead();

    SerialPortDataListener setComPortRead(java.util.function.BiConsumer<byte[], com.fazecast.jSerialComm.SerialPort> comPortRead);
}
