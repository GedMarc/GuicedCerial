package com.guicedee.cerial.implementations;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fazecast.jSerialComm.*;
import com.google.common.base.Strings;
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
import org.apache.commons.lang3.ArrayUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.fazecast.jSerialComm.SerialPort.*;
import static com.guicedee.cerial.CerialPortConnection.portNumberFormat;
import static com.guicedee.cerial.enumerations.ComPortStatus.Offline;
import static com.guicedee.cerial.enumerations.ComPortStatus.Running;
import static lombok.AccessLevel.PRIVATE;

@Getter
@Setter
@Log
public class DataSerialPortBytesListener implements SerialPortDataListenerWithExceptions, ComPortEvents
{
    @JsonIgnore
    private BiConsumer<byte[], SerialPort> comPortRead;

    @JsonIgnore
    private SerialPort comPort;
    @JsonIgnore
    private CerialPortConnection<?> connection;

    @JsonIgnore
    private char[] delimiter;

    @JsonIgnore
    private Pattern patternMatch;

    private Mode mode = Mode.Delimeter;

    private int maxBufferLength = 1024;

    public enum Mode
    {
        Delimeter,
        Pattern,
        All
    }


    public DataSerialPortBytesListener(char[] delimiter, SerialPort comPort, CerialPortConnection<?> connection)
    {
        this.delimiter = delimiter;
        this.comPort = comPort;
        this.connection = connection;
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
        if (event.getEventType() == LISTENING_EVENT_SOFTWARE_OVERRUN_ERROR)
        {
            log.log(Level.SEVERE, event.toString());
            connection.onConnectError(new SerialPortException("Software Overrun Error - " + event.toString()), ComPortStatus.GeneralException);
        } else if (event.getEventType() == LISTENING_EVENT_PARITY_ERROR)
        {
            log.log(Level.SEVERE, "Software Parity Error - " + event.toString());
            connection.onConnectError(new SerialPortException("Software Parity Error - " + event.toString()), ComPortStatus.GeneralException);
        } else if (event.getEventType() == LISTENING_EVENT_FRAMING_ERROR)
        {
            log.log(Level.SEVERE, "Hardware Framing Error - " + event.toString());
            connection.onConnectError(new SerialPortException("Hardware Framing Error - " + event.toString()), ComPortStatus.GeneralException);
        } else if (event.getEventType() == LISTENING_EVENT_FIRMWARE_OVERRUN_ERROR)
        {
            log.log(Level.SEVERE, "Hardware Firmware Overrun Error - " + event.toString());
            connection.onConnectError(new SerialPortException("Hardware Firmware Overrun Error - " + event.toString()), ComPortStatus.GeneralException);
        } else if (event.getEventType() == LISTENING_EVENT_BREAK_INTERRUPT)
        {
            log.log(Level.SEVERE, "Hardware Break Interrupt Error - " + event.toString());
            connection.onConnectError(new SerialPortException("Hardware Break Interrupt Error - " + event.toString()), ComPortStatus.GeneralException);
        } else if (event.getEventType() == LISTENING_EVENT_PORT_DISCONNECTED)
        {
            log.log(Level.SEVERE, event.toString());
            connection.setComPortStatus(Offline);
        } else if (event.getEventType() == LISTENING_EVENT_DATA_RECEIVED)
        {
            byte[] newData = event.getReceivedData();
            processReceivedBytes(newData);
        }
    }

    private StringBuilder buffer = new StringBuilder();

    public void processReceivedBytes(byte[] newData)
    {
        newData = remove(newData, (byte) 0);

        String message = "";

        for (byte b : newData)
        {
            if (b == 0)
            {
                continue;
            }

            boolean appended = false;
            if ((mode == Mode.All || mode == Mode.Pattern) && patternMatch != null)
            {
                buffer.append(String.valueOf((char)b).trim());
                appended = true;

                Matcher matcher = patternMatch.matcher(buffer.toString());
                while (matcher.find())
                {
                    message = matcher.group();
                    processMessage(message.getBytes());
                    buffer = new StringBuilder(buffer.substring(matcher.end()));
                }
            }
            if (mode == Mode.All || mode == Mode.Delimeter)
            {
                boolean found = false;
                for (char c : delimiter)
                {
                    if(c != b)
                    {
                        found = false;
                    }
                    else
                    {
                        found = true;
                        message = buffer.toString();
                        processMessage(message.getBytes());
                        buffer = new StringBuilder();
                    }
                }
                if (!found && !appended)
                {
                    buffer.append((char)b);
                    appended = true;
                }
            }

        }
    }

    private void processMessage(byte[] newData)
    {
        try
        {
            CompletableFuture.supplyAsync(() -> {
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
                    System.out.print("[" + portNumberFormat.format(connection.getComPort()) + "] RX - " + new String(newData));
                    if (comPortRead != null)
                    {
                        comPortRead.accept(newData, comPort);
                    }
                } catch (Throwable T)
                {
                    log.log(Level.SEVERE, "Error on ComPort [" + connection.getComPort() + "] Receipt", T);
                } finally
                {
                    callScoper.exit();
                }
                return true;
            }).get(2, TimeUnit.SECONDS);
        } catch (Exception e)
        {
            log.log(Level.SEVERE, "Error on running bytes serial ComPort [" + connection.getComPort() + "] Receipt", e);
        }
    }

    @Override
    public void catchException(Exception e)
    {
        log.log(Level.SEVERE, "Error on ComPort [" + connection.getComPort() + "] Receipt", e);

    }
}
