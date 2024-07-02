package com.guicedee.cerial;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;
import com.google.common.primitives.Bytes;
import com.google.inject.Inject;
import com.guicedee.cerial.enumerations.*;
import com.guicedee.client.CallScoper;
import com.guicedee.client.IGuiceContext;
import com.guicedee.guicedservlets.websockets.options.CallScopeProperties;
import com.guicedee.guicedservlets.websockets.options.CallScopeSource;
import com.guicedee.services.jsonrepresentation.IJsonRepresentation;
import gnu.io.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.ToString;
import lombok.extern.java.Log;
import org.apache.commons.lang3.function.TriConsumer;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.TooManyListenersException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.logging.Level;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY;
import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE;
import static com.guicedee.cerial.enumerations.ComPortStatus.*;
import static lombok.AccessLevel.PRIVATE;

@Getter
@Log
@ToString(of = {"comPort","comPortStatus"})
@JsonIgnoreProperties(ignoreUnknown = true, value = {"inspection"})
@JsonAutoDetect(fieldVisibility = ANY, getterVisibility = NONE, setterVisibility = NONE)
@NoArgsConstructor
public class CerialPortConnection<J extends CerialPortConnection<J>> implements IJsonRepresentation<J>
{
    @JsonIgnore
    private final Object[] readWriteLock = new Object[0];
    @JsonIgnore
    private NRSerialPort<?> serialPort;

    @JsonIgnore
    private final AtomicBoolean outputBufferEmpty = new AtomicBoolean(false);
    @JsonIgnore
    private final AtomicBoolean clearToSend = new AtomicBoolean(false);

    private Integer comPort;

    private BaudRate baudRate = BaudRate.$9600;
    private ComPortStatus comPortStatus = ComPortStatus.Offline;
    private ComPortType comPortType = ComPortType.Device;
    private DataBits dataBits = DataBits.$8;
    private FlowControl flowControl = FlowControl.None;
    private Parity parity = Parity.None;
    private StopBits stopBits = StopBits.$1;

    private Integer bufferSize = 4096;

    private Integer idleTimerSeconds = 5;

    @Getter(PRIVATE)
    @JsonIgnore
    private OutputStream writer = null;

    @JsonIgnore
    @Getter(PRIVATE)
    private BiConsumer<CerialPortConnection<?>, ComPortStatus> comPortStatusUpdate;
    @JsonIgnore
    @Getter(PRIVATE)
    private BiConsumer<byte[], CerialPortConnection<?>> comPortRead;
    @JsonIgnore
    @Getter(PRIVATE)
    private TriConsumer<Throwable, CerialPortConnection<?>, ComPortStatus> comPortError;
    @JsonIgnore
    private CerialIdleMonitor monitor;

    private LocalDateTime lastMessageTime;

    @Inject
    @JsonIgnore
    @Getter(PRIVATE)
    CallScoper callScoper;

    public CerialPortConnection(int comPort, BaudRate baudRate, int seconds)
    {
        this.comPort = comPort;
        this.baudRate = baudRate;
        setDataBits(DataBits.$8);
        setParity(Parity.None);
        setStopBits(StopBits.$1);
        setFlowControl(FlowControl.None);
        setComPortStatus(ComPortStatus.Offline);
        setComPortType(ComPortType.Device);

        this.serialPort = new NRSerialPort<>(getComPortName(), baudRate.toInt());
        this.idleTimerSeconds = seconds;
        this.setMonitor(new CerialIdleMonitor(this, 2, 1, seconds));
    }

    public CerialPortConnection(int comPort, BaudRate baudRate)
    {
        this(comPort, baudRate, 60 * 10);
    }

    /**
     * @return Returns a serial port, or constructs a new one
     */
    public NRSerialPort<?> getSerialPort()
    {
        if(this.serialPort == null)
        {
            this.serialPort = new NRSerialPort<>(getComPortName(), baudRate.toInt());
        }
        return serialPort;
    }

    public CerialIdleMonitor getMonitor()
    {
        if (monitor == null)
        {
            this.setMonitor(new CerialIdleMonitor(this, 2, 1, idleTimerSeconds));
        }
        return monitor;
    }

    public J connect()
    {
        beforeConnect();
        try
        {
            serialPort.connect();
            configure(serialPort.getSerialPortInstance());
            afterConnect();
            registerShutdownHook();
            log.info("Com Port Connected - " + getComPortName());
        }
        catch (Throwable e)
        {
            log.log(Level.SEVERE, "Error connecting to port", e);
            onConnectError(e, ComPortStatus.GeneralException);
        }
        return (J)this;
    }

    public J disconnect()
    {
        if (serialPort.isConnected())
        {
            serialPort.disconnect();
            setComPortStatus(Offline);
        }
        return (J)this;
    }

    public J beforeConnect()
    {
        if (callScoper == null)
        {
            IGuiceContext.instance()
                         .inject()
                         .injectMembers(this);
        }
        setComPortStatus(Opening);
        return (J)this;
    }

    public J afterConnect()
    {
        setComPortStatus(Silent);
        configureNotifications();
        configureForRTS();
        getMonitor().begin();
        return (J)this;
    }

    public J onConnectError(Throwable e, ComPortStatus status)
    {
        if (comPortError != null)
        {
            comPortError.accept(e, this, status);
        }
        setComPortStatus(status);
        disconnect();
        return (J)this;
    }

    public J onComPortStatusUpdate(BiConsumer<CerialPortConnection<?>, ComPortStatus> comPortStatusUpdate)
    {
        this.comPortStatusUpdate = comPortStatusUpdate;
        return (J)this;
    }

    public J setComPortStatus(ComPortStatus comPortStatus, boolean... update)
    {
        if (this.comPortStatus != comPortStatus && (
                (update != null && update.length == 0) ||
                        (update != null && !update[0]))
        )
        {
            this.comPortStatusUpdate.accept(this, comPortStatus);
        }
        this.comPortStatus = comPortStatus;
        return (J)this;
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
        return (J)this;
    }

    protected CerialPortConnection afterShutdown()
    {
        return (J)this;
    }

    protected CerialPortConnection beforeShutdown()
    {
        getMonitor().end();
        return (J)this;
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
        return (J)this;
    }

    private CerialPortConnection me()
    {
        return (J)this;
    }

    public J configureForRTS()
    {
        getSerialPort().getSerialPortInstance()
                  .setRTS(true);
        getSerialPort().getSerialPortInstance()
                  .notifyOnCTS(true);
        getSerialPort().getSerialPortInstance()
                  .notifyOnOutputEmpty(true);
        getSerialPort().getSerialPortInstance().setFlowControlMode(SerialPort.FLOWCONTROL_RTSCTS_IN |
                                              SerialPort.FLOWCONTROL_RTSCTS_OUT);
        getSerialPort().getSerialPortInstance().setDTR(true);

        return (J)this;
    }

    public J configureNotifications()
    {
        getSerialPort().getSerialPortInstance()
                  .notifyOnBreakInterrupt(true);
        getSerialPort().getSerialPortInstance()
                  .notifyOnOverrunError(true);
        getSerialPort().getSerialPortInstance()
                  .notifyOnParityError(true);
        getSerialPort().getSerialPortInstance()
                  .notifyOnFramingError(true);
        getSerialPort().getSerialPortInstance()
                  .notifyOnRingIndicator(true);
        getSerialPort().getSerialPortInstance()
                  .notifyOnDataAvailable(true);
        getSerialPort().notifyOnDataAvailable(true);
        final DataInputStream inputStream = new DataInputStream(getSerialPort().getInputStream());
        writer = new DataOutputStream(getSerialPort().getOutputStream());
        try
        {
            getSerialPort().addEventListener(event -> {
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
                            onConnectError(new SerialPortException("Exception in event listener thread [" + getComPort() + "]", e), GeneralException);
                            throw new SerialPortException("Throwable took place during byte reading", e);
                        }
                    }
                }
                else if (event.getEventType() == SerialPortEvent.CTS)
                {
                    //ready to start sending
                    clearToSend.set(event.getNewValue());
                    setComPortStatus(OperationInProgress);
                }else if (event.getEventType() == SerialPortEvent.OUTPUT_BUFFER_EMPTY)
                {
                    outputBufferEmpty.set(event.getNewValue());
                    setComPortStatus(Silent);
                }
                else if (event.getEventType() == SerialPortEvent.HARDWARE_ERROR)
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
                else
                {
                    onConnectError(new SerialPortException("Event failure type not catered for [" + getComPort() + "]/[" + event.getEventType() + "]"), GeneralException);
                }
            });
        }
        catch (TooManyListenersException e)
        {
            onConnectError(new SerialPortException("Listeners already registered on Port [" + getComPort() + "]"), Missing);
            log.log(Level.WARNING, "A listener is already attached to the com port " + getComPortName(), e);
        }
        return (J)this;
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
                properties.getProperties()
                          .put("ComPort", comPort);
                properties.getProperties()
                          .put("CerialPortConnection", this);
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

    public J setEndOfMessageChar(byte b) throws UnsupportedCommOperationException
    {
        this.serialPort.getSerialPortInstance()
                       .setEndOfInputChar(b);
        return (J)this;
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
                if (!message.endsWith("" + getSerialPort().getSerialPortInstance()
                                                     .getEndOfInputChar()))
                {
                    message += (char) getSerialPort().getSerialPortInstance()
                                                .getEndOfInputChar();
                }
            }
            catch (Exception e)
            {
                throw new SerialPortException("Unable to fix message and add end of character - " + message, e);
            }
        }
        if (!message.endsWith("" + (char) serialPort.getSerialPortInstance()
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

    public J setComPort(Integer comPort)
    {
        this.comPort = comPort;
        return (J)this;
    }

    public J setBaudRate(BaudRate baudRate)
    {
        this.baudRate = baudRate;
        return (J)this;
    }

    public J setComPortStatus(ComPortStatus comPortStatus)
    {
        if (this.comPortStatus != comPortStatus && this.comPortStatusUpdate != null)
        {
            this.comPortStatusUpdate.accept(this,comPortStatus);
        }
        this.comPortStatus = comPortStatus;
        return (J)this;
    }

    public J setComPortType(ComPortType comPortType)
    {
        this.comPortType = comPortType;
        return (J)this;
    }

    public J setDataBits(DataBits dataBits)
    {
        this.dataBits = dataBits;
        return (J)this;
    }

    public J setFlowControl(FlowControl flowControl)
    {
        this.flowControl = flowControl;
        return (J)this;
    }

    public J setParity(Parity parity)
    {
        this.parity = parity;
        return (J)this;
    }

    public J setStopBits(StopBits stopBits)
    {
        this.stopBits = stopBits;
        return (J)this;
    }

    public J setBufferSize(Integer bufferSize)
    {
        this.bufferSize = bufferSize;
        return (J)this;
    }

    public J setWriter(OutputStream writer)
    {
        this.writer = writer;
        return (J)this;
    }

    public J setComPortStatusUpdate(BiConsumer<CerialPortConnection<?>, ComPortStatus> comPortStatusUpdate)
    {
        this.comPortStatusUpdate = comPortStatusUpdate;
        return (J)this;
    }

    public J setComPortRead(BiConsumer<byte[], CerialPortConnection<?>> comPortRead)
    {
        this.comPortRead = comPortRead;
        return (J)this;
    }

    public J setComPortError(TriConsumer<Throwable, CerialPortConnection<?>, ComPortStatus> comPortError)
    {
        this.comPortError = comPortError;
        return (J)this;
    }

    public J setMonitor(CerialIdleMonitor monitor)
    {
        this.monitor = monitor;
        return (J)this;
    }

    public J setLastMessageTime(LocalDateTime lastMessageTime)
    {
        this.lastMessageTime = lastMessageTime;
        return (J)this;
    }
}
