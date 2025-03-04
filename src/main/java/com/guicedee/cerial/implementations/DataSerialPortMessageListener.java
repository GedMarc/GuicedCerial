package com.guicedee.cerial.implementations;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortEvent;
import com.fazecast.jSerialComm.SerialPortMessageListener;
import com.google.common.base.Strings;
import com.guicedee.cerial.CerialPortConnection;
import com.guicedee.cerial.SerialPortException;
import com.guicedee.cerial.enumerations.ComPortStatus;
import com.guicedee.client.CallScoper;
import com.guicedee.client.IGuiceContext;
import com.guicedee.guicedinjection.LogUtils;
import com.guicedee.guicedservlets.websockets.options.CallScopeProperties;
import com.guicedee.guicedservlets.websockets.options.CallScopeSource;
import io.vertx.core.Vertx;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.core.Logger;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.logging.Level;

import static com.fazecast.jSerialComm.SerialPort.*;
import static com.guicedee.cerial.CerialPortConnection.portNumberFormat;
import static com.guicedee.cerial.enumerations.ComPortStatus.Offline;
import static com.guicedee.cerial.enumerations.ComPortStatus.Running;
import static lombok.AccessLevel.PRIVATE;

@Getter
@Setter
public class DataSerialPortMessageListener implements SerialPortMessageListener, ComPortEvents
{
    @JsonIgnore
    private Logger log;

    @JsonIgnore
    
    private BiConsumer<byte[], SerialPort> comPortRead;
    @JsonIgnore
    
    private SerialPort comPort;
    @JsonIgnore
    
    private CerialPortConnection<?> connection;
    @JsonIgnore
    
    private char[] delimiter;

    public DataSerialPortMessageListener(char[] delimiter, SerialPort comPort, CerialPortConnection<?> connection)
    {
        this.delimiter = delimiter;
        this.comPort = comPort;
        this.connection = connection;
        log = LogUtils.getSpecificRollingLogger("COM" + connection.getComPort(), "cerial",
                "[%d{yyyy-MM-dd HH:mm:ss.SSS}] [%-5level] - [%msg]%n",true);
    }

    @Override
    public byte[] getMessageDelimiter()
    {
        return new String(delimiter).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean delimiterIndicatesEndOfMessage()
    {
        return true;
    }

    @Override
    public int getListeningEvents()
    {
        return LISTENING_EVENT_DATA_RECEIVED | LISTENING_EVENT_PORT_DISCONNECTED | LISTENING_EVENT_BREAK_INTERRUPT | LISTENING_EVENT_FRAMING_ERROR | LISTENING_EVENT_FIRMWARE_OVERRUN_ERROR | LISTENING_EVENT_PARITY_ERROR | LISTENING_EVENT_SOFTWARE_OVERRUN_ERROR;
    }

    public static byte[] remove(byte[] array, byte toRemove)
    {
        List<Byte> byteList = new ArrayList<>(Arrays.asList(ArrayUtils.toObject(array)));
        byteList.removeIf(b -> b == toRemove);
        return ArrayUtils.toPrimitive(byteList.toArray(new Byte[0]));
    }

    @Override
    public void serialEvent(SerialPortEvent event)
    {
        if (event.getEventType() == LISTENING_EVENT_SOFTWARE_OVERRUN_ERROR) {
            log.error( event.toString());
            connection.onConnectError(new SerialPortException("Software Overrun Error - " + event.toString()), ComPortStatus.GeneralException);
        }
        else  if (event.getEventType() == LISTENING_EVENT_PARITY_ERROR) {
            log.error("Software Parity Error - " + event.toString());
            connection.onConnectError(new SerialPortException("Software Parity Error - " + event.toString()), ComPortStatus.GeneralException);
        }
        else  if (event.getEventType() == LISTENING_EVENT_FRAMING_ERROR) {
            log.error("Hardware Framing Error - " + event.toString());
            connection.onConnectError(new SerialPortException("Hardware Framing Error - " + event.toString()), ComPortStatus.GeneralException);
        }
        else  if (event.getEventType() == LISTENING_EVENT_FIRMWARE_OVERRUN_ERROR) {
            log.error("Hardware Firmware Overrun Error - " + event.toString());
            connection.onConnectError(new SerialPortException("Hardware Firmware Overrun Error - " + event.toString()), ComPortStatus.GeneralException);
        }
        else  if (event.getEventType() == LISTENING_EVENT_BREAK_INTERRUPT) {
            log.error("Hardware Break Interrupt Error - " + event.toString());
            connection.onConnectError(new SerialPortException("Hardware Break Interrupt Error - " + event.toString()), ComPortStatus.GeneralException);
        } else if (event.getEventType() == LISTENING_EVENT_PORT_DISCONNECTED) {
            log.error( event.toString());
            connection.setComPortStatus(Offline);
        } else if (event.getEventType() == LISTENING_EVENT_DATA_RECEIVED)
        {
            byte[] newData = event.getReceivedData();
            processReceivedBytes(newData);
        }
    }

    public void processReceivedBytes(byte[] newData)
    {
        remove(newData, (byte) 0);

        if (Strings.isNullOrEmpty(new String(newData).trim()))
            return;

        log.info("RX] - [" + portNumberFormat.format(getConnection().getComPort()) + "] - [" + portNumberFormat.format(connection.getComPort()) + " - " + new String(newData).trim());
        var vertx =IGuiceContext.get(Vertx.class);
        vertx.executeBlocking(() -> {
            var callScoper = IGuiceContext.get(CallScoper.class);
            callScoper.enter();
            try
            {
                CallScopeProperties properties = IGuiceContext.get(CallScopeProperties.class);
                properties.setSource(CallScopeSource.SerialPort);
                properties.getProperties()
                        .put("ComPort", comPort);
                properties.getProperties()
                        .put("CerialPortConnection", this);
                getConnection().setComPortStatus(Running);
                // log.warning(MessageFormat.format("RX : {0}", new String(newData)));
                //System.out.print("[" + portNumberFormat.format(connection.getComPort()) + "] RX - " + new String(newData));
                if (comPortRead != null)
                {
                    comPortRead.accept(newData, comPort);
                }
            } catch (Throwable T)
            {
                log.error( "Error on ComPort [" + connection.getComPort() + "] Receipt", T);
            } finally
            {
                callScoper.exit();
            }
            return null;
        },false);
    }
}
