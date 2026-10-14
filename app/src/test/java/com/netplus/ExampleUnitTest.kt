package com.netplus

import com.netplus.data.model.Customer
import com.netplus.util.FormatUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testCurrencyFormatting() {
        val formatted = FormatUtils.formatRupiah(110000L)
        assert(formatted.contains("110.000"))
    }

    @Test
    fun testCustomerModelCreation() {
        val customer = Customer(
            id = 1791158991543384L,
            customerCode = "WIFI-102",
            name = "Kalizta",
            monthlyFee = 110000L,
            dueDay = 5
        )
        assertEquals("WIFI-102", customer.customerCode)
        assertEquals("Kalizta", customer.name)
        assertEquals(110000L, customer.monthlyFee)
    }
}
