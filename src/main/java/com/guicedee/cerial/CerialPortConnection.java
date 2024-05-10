package com.guicedee.cerial;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;
import com.google.common.primitives.Bytes;
import com.google.inject.Inject;
import com.guicedee.cerial.enumerations.*;
import com.guicedee.client.CallScoper;
import com.guicedee.client.IGuiceContext;
import com.guicedee.guicedservlets.websockets.options.CallScopeProperties;
import com.guicedee.guicedservlets.websockets.options.CallScopeSource;
import gnu.io.NRSerialPort;
import gnu.io.RXTXPort;
import gnu.io.SerialPortEvent;
import gnu.io.UnsupportedCommOperationException;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import org.apache.commons.lang3.function.TriConsumer;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.TooManyListenersException;
import java.util.function.BiConsumer;
import java.util.logging.Level;

import static com.guicedee.cerial.enumerations.ComPortStatus.*;
import static lombok.AccessLevel.PRIVATE;

@Getter
@Setter
@NoArgsConstructor
@Log
public class CerialPortConnection
{
    private final Object[] readWriteLock = new Object[0];
    @JsonIgnore
    private NRSerialPort<?> serialPort;
    private Integer comPort;

    private BaudRate baudRate = BaudRate.$9600;
    private ComPortStatus comPortStatus = ComPortStatus.Offline;
    private ComPortType comPortType = ComPortType.Device;
    private DataBits dataBits = DataBits.$8;
    private FlowControl flowControl = FlowControl.None;
    private Parity parity = Parity.None;
    private StopBits stopBits = StopBits.$1;

    private Integer bufferSize = 4096;

    @Getter(PRIVATE)
    private OutputStream writer = null;

    @JsonIgnore
    private BiConsumer<CerialPortConnection, ComPortStatus> comPortStatusUpdate;
    @JsonIgnore
    private BiConsumer<byte[], CerialPortConnection> comPortRead;
    private TriConsumer<Throwable, CerialPortConnection,ComPortStatus> comPortError;
    @JsonIgnore
    private CerialIdleMonitor monitor;

    private LocalDateTime lastMessageTime;

    @Inject
    CallScoper callScoper;


    public CerialPortConnection(int comPort, BaudRate baudRate, int seconds)
    {
        this.comPort = comPort;
        this.baudRate = baudRate;
        this.serialPort = new NRSerialPort<>(getComPortName(), baudRate.toInt());
        this.setMonitor(new CerialIdleMonitor(this, 2, 1, seconds));
    }

    public CerialPortConnection(int comPort, BaudRate baudRate)
    {
        this(comPort, baudRate, 60* 10);
    }

    public CerialPortConnection connect()
    {
        beforeConnect();
        try
        {
            serialPort.connect();
            configure(serialPort.getSerialPortInstance());
            afterConnect();
            registerShutdownHook();
        }
        catch (Throwable e)
        {
            log.log(Level.SEVERE, "Error connecting to port", e);
            onConnectError(e, ComPortStatus.GeneralException);
        }
        return this;
    }

    public CerialPortConnection disconnect()
    {
        if (serialPort.isConnected())
        {
            serialPort.disconnect();
        }
        return this;
    }

    public CerialPortConnection beforeConnect()
    {
        if (callScoper == null)
        {
            IGuiceContext.instance()
                         .inject()
                         .injectMembers(this);
        }
        setComPortStatus(Opening);
        return this;
    }

    public CerialPortConnection afterConnect()
    {
        setComPortStatus(Silent);
        configureNotifications();
        getMonitor().begin();
        return this;
    }

    public CerialPortConnection onConnectError(Throwable e, ComPortStatus status)
    {
        if (comPortError != null)
        {
            comPortError.accept(e,this,status);
        }
        setComPortStatus(status);
        return this;
    }

    public CerialPortConnection onComPortStatusUpdate(BiConsumer<CerialPortConnection, ComPortStatus> comPortStatusUpdate)
    {
        this.comPortStatusUpdate = comPortStatusUpdate;
        return this;
    }

    public CerialPortConnection setComPortStatus(ComPortStatus comPortStatus, boolean... update)
    {
        if (this.comPortStatus != comPortStatus && (
                (update != null && update.length == 0) ||
                        (update != null && !update[0]))
        )
        {
            this.comPortStatusUpdate.accept(this, comPortStatus);
        }
        this.comPortStatus = comPortStatus;
        return this;
    }

    protected CerialPortConnection registerShutdownHook()
    {
        Runtime.getRuntime()
               .addShutdownHook(new Thread(() -> {
                   if (serialPort.isConnected())
                   {
                       beforeShutdown();
                       log.config("Shutting down com port - " + comPort);
                       serialPort.disconnect();
                       afterShutdown();
                   }
               }));
        return this;
    }

    protected CerialPortConnection afterShutdown()
    {
        return this;
    }

    protected CerialPortConnection beforeShutdown()
    {
        getMonitor().end();
        return this;
    }

    protected CerialPortConnection configure(RXTXPort instance) throws UnsupportedCommOperationException
    {
        setEndOfMessageChar((byte) '\n');

        if (flowControl != null)
        {
            instance.setFlowControlMode(flowControl.toInt());
        }
        if (parity != null)
        {
            instance.setSerialPortParams(baudRate.toInt(),
                                         dataBits.toInt(),
                                         stopBits.toInt(),
                                         parity.toInt());
        }
        if (bufferSize != null)
        {
            instance.setInputBufferSize(bufferSize);
            instance.setOutputBufferSize(bufferSize);
        }
        return this;
    }

    private CerialPortConnection me()
    {
        return this;
    }

    public CerialPortConnection configureNotifications()
    {
        serialPort.getSerialPortInstance()
                  .notifyOnBreakInterrupt(true);
        serialPort.getSerialPortInstance()
                  .notifyOnOverrunError(true);
        serialPort.getSerialPortInstance()
                  .notifyOnParityError(true);
        serialPort.getSerialPortInstance()
                  .notifyOnFramingError(true);
        serialPort.getSerialPortInstance()
                  .notifyOnRingIndicator(true);
        serialPort.getSerialPortInstance()
                  .notifyOnDataAvailable(true);
        serialPort.notifyOnDataAvailable(true);
        final DataInputStream inputStream = new DataInputStream(serialPort.getInputStream());
        writer = new DataOutputStream(serialPort.getOutputStream());
        try
        {
            serialPort.addEventListener(event -> {
                if (event.getEventType() == SerialPortEvent.DATA_AVAILABLE && event.getNewValue() && !event.getOldValue())
                {
                    if (getComPortStatus() == Silent)
                    {
                        setComPortStatus(Running);
                    }
                    if (comPortRead != null)
                    {
                        try
                        {
                            byte[] bytes = new byte[0];
                            while (!Thread.interrupted())
                            {
                                synchronized (readWriteLock)
                                {
                                    if (inputStream.available() > 0)
                                    {
                                        if (bytes.length == 0)
                                        {
                                            bytes = new byte[inputStream.available()];
                                            inputStream.readFully(bytes);
                                        }
                                        else
                                        {
                                            byte[] extendedBytes = new byte[inputStream.available()];
                                            inputStream.readFully(extendedBytes);
                                            bytes = Bytes.concat(bytes, extendedBytes);
                                        }
                                        if (bytes.length > 0 && !Strings.isNullOrEmpty(new String(bytes)))
                                        {
                                            if (Bytes.contains(bytes, serialPort.getSerialPortInstance()
                                                                                .getEndOfInputChar()))
                                            {
                                                bytes = checkAndProcessBytes(bytes);
                                            }
                                        }
                                    }
                                    Thread.sleep(50);
                                }
                            }
                        }
                        catch (Throwable e)
                        {
                            onConnectError(new SerialPortException("Exception in event listener thread [" + getComPort() + "]",e), GeneralException);
                            throw new SerialPortException("Throwable took place during byte reading",e);
                        }
                    }
                }
                if (event.getEventType() == SerialPortEvent.HARDWARE_ERROR)
                {
                    onConnectError(new SerialPortException("Hardware Error on Port [" + getComPort() + "]"), GeneralException);
                }
                else if (event.getEventType() == SerialPortEvent.FE)
                {
                    onConnectError(new SerialPortException("Framing Error on Port [" + getComPort() + "]"), GeneralException);
                }
                else if (event.getEventType() == SerialPortEvent.PE)
                {
                    onConnectError(new SerialPortException("Parity Error on Port [" + getComPort() + "]"), GeneralException);
                }
                else if (event.getEventType() == SerialPortEvent.OE)
                {
                    onConnectError(new SerialPortException("Overrun Error on Port [" + getComPort() + "]"), GeneralException);
                }
                else if (event.getEventType() == SerialPortEvent.BI)
                {
                    onConnectError(new SerialPortException("Break-Interrupt Error on Port [" + getComPort() + "]"), GeneralException);
                }
                else if (event.getEventType() == SerialPortEvent.CTS)
                {
                    onConnectError(new SerialPortException("Clear to Send on Port [" + getComPort() + "]"), GeneralException);
                }
                else if (event.getEventType() == SerialPortEvent.OUTPUT_BUFFER_EMPTY)
                {
                    onConnectError(new SerialPortException("Output has empty buffer error on Port [" + getComPort() + "]"), GeneralException);
                }
                else
                {
                    onConnectError(new SerialPortException("Event failure type not catered for [" + getComPort() + "]/[" + event.getEventType() + "]"), GeneralException);
                }
            });
        }
        catch (TooManyListenersException e)
        {
            onConnectError(new SerialPortException("Listeners already registered on Port [" + getComPort() + "]"), Missing);
            log.log(Level.WARNING,"A listener is already attached to the com port " + getComPortName(),e);
        }
        return this;
    }

    private byte[] checkAndProcessBytes(byte[] bytes) throws UnsupportedCommOperationException
    {
        int messageEnd = Bytes.indexOf(bytes, serialPort.getSerialPortInstance()
                                                        .getEndOfInputChar());
        if (messageEnd < 1)
        {
        }
        else
        {
            lastMessageTime = LocalDateTime.now();
            callScoper.enter();
            boolean workedOn = false;
            try
            {
                CallScopeProperties properties = IGuiceContext.get(CallScopeProperties.class);
                properties.setSource(CallScopeSource.SerialPort);
                setComPortStatus(Running);
                comPortRead.accept(Arrays.copyOf(bytes, messageEnd), me());
                workedOn = true;
            }
            finally
            {
                if (messageEnd == bytes.length)
                {
                    bytes = new byte[0];
                }
                else
                {
                    bytes = Arrays.copyOfRange(bytes, messageEnd, bytes.length);
                    bytes = new String(bytes).trim()
                                             .getBytes();
                    if (bytes.length > 0)
                    {
                        checkAndProcessBytes(bytes);
                    }
                }
                if (workedOn)
                {
                    callScoper.exit();
                }
            }
        }
        return bytes;
    }

    public CerialPortConnection setEndOfMessageChar(byte b) throws UnsupportedCommOperationException
    {
        this.serialPort.getSerialPortInstance()
                       .setEndOfInputChar(b);
        return this;
    }


    @JsonProperty
    protected String getComPortName()
    {
        if (OSValidator.isWindows())
        {
            return "COM" + getComPort();
        }
        else
        {
            return "/dev/ttyUSB" + getComPort() + " " + getBaudRate().toInt();
        }
    }

    @SneakyThrows
    public void write(String message, boolean... checkForEndOfCharacter)
    {
        if ((checkForEndOfCharacter != null && checkForEndOfCharacter.length == 0) || (checkForEndOfCharacter != null && checkForEndOfCharacter[0]))
        {
            try
            {
                if (!message.endsWith("" + serialPort.getSerialPortInstance()
                                                     .getEndOfInputChar()))
                {
                    message += (char) serialPort.getSerialPortInstance()
                                                .getEndOfInputChar();
                }
            }
            catch (Exception e)
            {
                throw new SerialPortException("Unable to fix message and add end of character - " + message, e);
            }
        }
        if (!message.endsWith("" + (char)serialPort.getSerialPortInstance()
                                             .getEndOfInputChar()))
        {
            log.warning("Message being sent on stream without an end character - " + message);
        }
        if (serialPort.isConnected() && !message.trim()
                                                .isEmpty())
        {
            if (writer != null)
            {
                try
                {
                    synchronized (readWriteLock)
                    {
                        writer.write(message.getBytes());
                    }
                }
                catch (Exception e)
                {
                    throw new SerialPortException("Could not write to serial port " + getComPortName() + " - " + e.getMessage(), e);
                }
            }
        }
    }

    private static class OSValidator
    {
        private static final String OS = System.getProperty("os.name")
                                               .toLowerCase();

        public static boolean isWindows()
        {
            return OS.contains("win");
        }

        public static boolean isMac()
        {
            return OS.contains("mac");
        }

        public static boolean isUnix()
        {
            return (OS.contains("nix") || OS.contains("nux") || OS.contains("aix"));
        }

        public static boolean isSolaris()
        {
            return OS.contains("sunos");
        }
    }
}