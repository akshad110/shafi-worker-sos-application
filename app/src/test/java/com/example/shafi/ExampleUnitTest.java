package com.example.shafi;

import com.example.shafi.core.Format;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ExampleUnitTest {
    @Test
    public void moneyUsesMinorUnits() {
        assertEquals("₹2,500", Format.money(250000, "INR"));
    }

    @Test
    public void statusLabelsAreReadable() {
        assertEquals("Under Review", Format.status("UNDER_REVIEW"));
    }
}