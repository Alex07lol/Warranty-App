package com.warrantyvault.repair

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepairLocationTest {

    private val provider = OverpassRepairLocationProvider()

    // ---------- Overpass response parsing ----------

    @Test
    fun `parses node and way elements with center`() {
        val raw = """
        {"version":0.6,"elements":[
          {"type":"node","id":1001,"lat":52.52,"lon":13.40,
           "tags":{"name":"Fixit Phones","shop":"mobile_phone","phone":"+49 30 111",
                   "addr:street":"Main St","addr:housenumber":"12","addr:city":"Berlin",
                   "addr:postcode":"10115","opening_hours":"Mo-Fr 09:00-18:00","operator":"Fixit GmbH"}},
          {"type":"way","id":2002,"center":{"lat":52.51,"lon":13.39},
           "tags":{"name":"Laptop Lab","craft":"electronics_repair","website":"https://laptoplab.example"}}
        ]}
        """.trimIndent()

        val result = provider.parseOverpassResponse(raw)

        assertEquals(2, result.size)
        val node = result.first { it.name == "Fixit Phones" }
        assertEquals("node/1001", node.id)
        assertEquals(52.52, node.latitude, 1e-9)
        assertEquals(13.40, node.longitude, 1e-9)
        assertEquals("+49 30 111", node.phone)
        assertEquals("12 Main St, 10115, Berlin", node.address)
        assertEquals("Mo-Fr 09:00-18:00", node.openingHours)
        assertEquals("Fixit GmbH", node.operator)
        assertEquals("OpenStreetMap", node.source)

        val way = result.first { it.name == "Laptop Lab" }
        assertEquals("way/2002", way.id)
        assertEquals(52.51, way.latitude, 1e-9)
        assertEquals("https://laptoplab.example", way.website)
        assertEquals("electronics_repair", way.category)
    }

    @Test
    fun `elements without name are skipped`() {
        val raw = """
        {"elements":[{"type":"node","id":1,"lat":1.0,"lon":2.0,"tags":{"shop":"electronics"}}]}
        """.trimIndent()
        assertTrue(provider.parseOverpassResponse(raw).isEmpty())
    }

    @Test
    fun `elements without coordinates are skipped`() {
        val raw = """
        {"elements":[{"type":"node","id":2,"tags":{"name":"Ghost Repair"}}]}
        """.trimIndent()
        assertTrue(provider.parseOverpassResponse(raw).isEmpty())
    }

    @Test
    fun `malformed json yields empty list not crash`() {
        assertTrue(provider.parseOverpassResponse("not json at all").isEmpty())
        assertTrue(provider.parseOverpassResponse("").isEmpty())
        assertTrue(provider.parseOverpassResponse("{\"elements\": 42}").isEmpty())
    }

    @Test
    fun `missing optional tags produce nulls`() {
        val raw = """
        {"elements":[{"type":"node","id":5,"lat":10.0,"lon":20.0,"tags":{"name":"Bare Shop"}}]}
        """.trimIndent()
        val loc = provider.parseOverpassResponse(raw).single()
        assertNull(loc.address)
        assertNull(loc.phone)
        assertNull(loc.website)
        assertNull(loc.openingHours)
        assertNull(loc.operator)
        assertEquals("OpenStreetMap", loc.source)
    }

    // ---------- Distance ----------

    @Test
    fun `haversine distance is sane`() {
        // Berlin Mitte -> Potsdam ~ 26km.
        val d = distanceMeters(52.52, 13.40, 52.39, 13.06)
        assertTrue("expected ~20-32km, got $d", d in 20_000.0..32_000.0)
        // Zero distance to self.
        assertEquals(0.0, distanceMeters(52.52, 13.40, 52.52, 13.40), 0.001)
    }

    @Test
    fun `distance ordering ascending`() {
        val origin = 52.52 to 13.40
        val near = RepairLocation("a", "Near", origin.first + 0.005, origin.second, null, null, null, null, null, null, null, "OSM")
        val far = RepairLocation("b", "Far", origin.first + 0.05, origin.second, null, null, null, null, null, null, null, "OSM")

        val decorated = listOf(far, near).map {
            it.copy(distanceMeters = distanceMeters(origin.first, origin.second, it.latitude, it.longitude))
        }.sortedBy { it.distanceMeters }

        assertEquals("Near", decorated.first().name)
        assertTrue(decorated.first().distanceMeters!! < decorated.last().distanceMeters!!)
    }

    // ---------- Formatting ----------

    @Test
    fun `distance formatting`() {
        assertEquals("450 m", formatDistance(450.0))
        assertEquals("1.5 km", formatDistance(1500.0).replace(',', '.'))
        assertEquals("15 km", formatDistance(15_000.0))
    }

    // ---------- Category filters ----------

    @Test
    fun `category filters are coarse and privacy-safe`() {
        assertTrue(categoryToOsmFilters("phone").contains("shop=mobile_phone"))
        assertTrue(categoryToOsmFilters("laptop").contains("shop=computer"))
        assertTrue(categoryToOsmFilters(null).isNotEmpty())
        // Fallback includes general repair tags.
        assertTrue(categoryToOsmFilters("other").contains("craft=electronics_repair"))
    }

    @Test
    fun `radius constants sane`() {
        assertEquals(5000, RepairLocationProvider.DEFAULT_RADIUS)
        assertTrue(RepairLocationProvider.MAX_RADIUS >= RepairLocationProvider.DEFAULT_RADIUS)
    }

    // ---------- ProductContext privacy shape ----------

    @Test
    fun `product context carries only a category`() {
        val ctx = ProductContext("phone")
        assertEquals("phone", ctx.category)
        // Compile-time guarantee of the privacy contract: no other fields exist.
        assertEquals(ProductContext("phone"), ctx)
    }

    @Test
    fun `provider interface default radii exposed`() {
        assertNotNull(RepairLocationProvider.MAX_RADIUS)
    }
}
