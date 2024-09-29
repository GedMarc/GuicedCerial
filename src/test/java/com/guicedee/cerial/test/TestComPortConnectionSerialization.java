package com.guicedee.cerial.test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.guicedee.cerial.CerialPortConnection;
import com.guicedee.cerial.enumerations.*;
import com.guicedee.services.jsonrepresentation.IJsonRepresentation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestComPortConnectionSerialization
{
    @Test
    public void testSerialization() throws JsonProcessingException
    {
        CerialPortConnection<?> cpc = new CerialPortConnection<>(6, BaudRate.$9600);
        cpc.setDataBits(DataBits.$8);
        cpc.setParity(Parity.None);
        cpc.setStopBits(StopBits.$1);
        cpc.setFlowControl(FlowControl.None);
        cpc.setComPortStatus(ComPortStatus.Offline);
        cpc.setComPortType(ComPortType.Device);

        cpc.connect();
        //cpc.configureForRTS();
       // cpc.configureNotifications();

        String json = cpc.toJson();
        System.out.println(json);
        CerialPortConnection cpcMatch = IJsonRepresentation.getObjectMapper()
                                                                       .readValue(json, CerialPortConnection.class);
        String json1 = cpcMatch.toJson();
        assertEquals(json, json1);



    }
}
