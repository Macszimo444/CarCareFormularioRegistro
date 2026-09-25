package com.example.carcareformularioregistro.ui

import com.example.carcareformularioregistro.data.Maintenance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MaintenanceSearchTest {
    private val records = listOf(
        maintenance(
            id = 1,
            type = "Cambio de aceite",
            date = "2026-09-15",
            mileage = 120000,
            workshop = "Taller López",
            description = "Aceite sintético y filtro nuevo",
            status = Maintenance.STATUS_REALIZADO
        ),
        maintenance(
            id = 2,
            type = "Cambio de aceite",
            date = "2026-10-20",
            mileage = 125000,
            workshop = "Taller Norte",
            description = "Revisión preventiva del motor",
            status = Maintenance.STATUS_PROXIMO
        ),
        maintenance(
            id = 3,
            type = "Cambio de frenos",
            date = "2026-10-21",
            mileage = 126000,
            workshop = "Taller López",
            description = "Sustitución de balatas delanteras",
            status = Maintenance.STATUS_PROXIMO
        ),
        maintenance(
            id = 4,
            type = "Cambio de batería",
            date = "2026-11-01",
            mileage = 127000,
            workshop = "Electricidad Central",
            description = "Comprobar alternador",
            status = Maintenance.STATUS_PENDIENTE
        ),
        maintenance(
            id = 5,
            type = "Afinación",
            date = "2026-08-01",
            mileage = 119000,
            workshop = "Mecánica Álvarez",
            description = "Limpieza de inyectores y bujías",
            status = Maintenance.STATUS_REALIZADO
        )
    )

    @Test
    fun oilQueryReturnsOnlyOilServices() {
        assertEquals(listOf(1, 2), ids("aceite"))
    }

    @Test
    fun uppercaseAndMixedCaseProduceTheSameResults() {
        val expected = ids("aceite")
        assertEquals(expected, ids("ACEITE"))
        assertEquals(expected, ids("AcEiTe"))
    }

    @Test
    fun partialServiceNameMatches() {
        assertEquals(listOf(1, 2), ids("ace"))
        assertEquals(listOf(3), ids("fre"))
    }

    @Test
    fun workshopMatchesAllOfItsServices() {
        assertEquals(listOf(1, 3), ids("Taller López"))
    }

    @Test
    fun descriptionIsSearchable() {
        assertEquals(listOf(3), ids("balatas"))
        assertEquals(listOf(5), ids("inyectores"))
    }

    @Test
    fun statusIsSearchableIncludingPendingRecords() {
        assertEquals(listOf(2, 3), ids("próximo"))
        assertEquals(listOf(1, 5), ids("realizado"))
        assertEquals(listOf(4), ids("pendiente"))
    }

    @Test
    fun rawMileageMatches() {
        assertEquals(listOf(1), ids("120000"))
    }

    @Test
    fun mileageWithThousandsSeparatorsMatchesTheSameRecord() {
        for (query in listOf("120,000", "120 000", "120.000")) {
            assertEquals("Query: $query", listOf(1), ids(query))
        }
    }

    @Test
    fun nextMileageIsSearchable() {
        assertEquals(listOf(1), ids("130000"))
    }

    @Test
    fun isoDateAndDisplayedDateMatchTheSameService() {
        assertEquals(listOf(1), ids("2026-09-15"))
        assertEquals(listOf(1), ids("15/09/2026"))
        assertEquals(listOf(1), ids("15/09"))
    }

    @Test
    fun nextDateIsSearchableInBothFormats() {
        assertEquals(listOf(1), ids("2027-03-15"))
        assertEquals(listOf(1), ids("15/03/2027"))
    }

    @Test
    fun existingDayFirstDateAlsoMatchesIsoQuery() {
        val legacy = listOf(records.first().copy(date = "15/09/2026"))
        assertEquals(legacy, MaintenanceSearch.filter(legacy, "2026-09-15", MaintenanceFilter.ALL))
    }

    @Test
    fun upcomingTabAndQueryApplyTogether() {
        assertEquals(listOf(2), ids("aceite", MaintenanceFilter.UPCOMING))
    }

    @Test
    fun completedTabAndQueryApplyTogether() {
        assertEquals(listOf(1), ids("aceite", MaintenanceFilter.COMPLETED))
    }

    @Test
    fun statusQueryCannotOverrideTheSelectedTab() {
        assertTrue(ids("realizado", MaintenanceFilter.UPCOMING).isEmpty())
    }

    @Test
    fun clearingSearchRestoresOnlyTheSelectedTab() {
        assertEquals(listOf(2), ids("aceite", MaintenanceFilter.UPCOMING))
        assertEquals(listOf(2, 3), ids("", MaintenanceFilter.UPCOMING))
        assertEquals(listOf(1, 5), ids("", MaintenanceFilter.COMPLETED))
        assertEquals(listOf(1, 2, 3, 4, 5), ids("", MaintenanceFilter.ALL))
    }

    @Test
    fun nonexistentTermReturnsAnEmptyListForTheEmptyState() {
        assertTrue(ids("servicio inexistente xyz").isEmpty())
    }

    @Test
    fun emptyDatabaseReturnsAnEmptyList() {
        for (tab in MaintenanceFilter.values()) {
            assertTrue(MaintenanceSearch.filter(emptyList(), "", tab).isEmpty())
            assertTrue(MaintenanceSearch.filter(emptyList(), "aceite", tab).isEmpty())
        }
    }

    @Test
    fun wordsCanMatchDifferentFieldsButMustAllMatch() {
        assertEquals(listOf(1), ids("aceite lópez 120000"))
        assertEquals(listOf(1), ids("  LÓPEZ   aceite  "))
        assertTrue(ids("aceite alternador").isEmpty())
    }

    @Test
    fun accentsAreOptionalInNamesAndDescriptions() {
        assertEquals(listOf(5), ids("afinacion"))
        assertEquals(listOf(5), ids("MECANICA ALVAREZ"))
        assertEquals(listOf(5), ids("bujias"))
        assertEquals(listOf(4), ids("bateria"))
    }

    @Test
    fun blankWhitespaceBehavesLikeClearedSearch() {
        assertEquals(listOf(1, 2, 3, 4, 5), ids(" \t\n "))
    }

    @Test
    fun filteringPreservesDatabaseOrder() {
        val reversed = records.reversed()
        val result = MaintenanceSearch.filter(reversed, "aceite", MaintenanceFilter.ALL)
        assertEquals(listOf(2, 1), result.map { it.id })
    }

    private fun ids(query: String, filter: MaintenanceFilter = MaintenanceFilter.ALL): List<Int> =
        MaintenanceSearch.filter(records, query, filter).map { it.id }

    private fun maintenance(
        id: Int,
        type: String,
        date: String,
        mileage: Int,
        workshop: String,
        description: String,
        status: String
    ) = Maintenance(
        id = id,
        vehicleId = 1,
        type = type,
        date = date,
        mileage = mileage,
        cost = 850.0,
        workshop = workshop,
        nextDate = if (id == 1) "2027-03-15" else "2027-06-01",
        nextMileage = mileage + 10000,
        description = description,
        status = status
    )
}
