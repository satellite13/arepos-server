package ru.kavader.arepos.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OefParseServiceTest {

    private val service = OefParseService()

    @Test
    fun `parses container and association-to-flow fixture`() {
        val xml = readFixture("oef/container-assoc-to-flow.xml")
        val parsed = service.parseAndValidate(xml)

        assertEquals("id-model-1", parsed.model.id)
        assertEquals("Container and Association-to-Flow", parsed.model.name)
        assertEquals(3, parsed.elements.size)
        assertEquals(2, parsed.relationships.size)
        assertEquals(1, parsed.views.size)

        val view = parsed.views.single()
        assertEquals(4, view.nodes.size)
        assertEquals(2, view.connections.size)

        val container = view.nodes.single { it.id == "node-box" }
        assertEquals("Container", container.type)
        assertEquals("Group", container.labelText)
        assertEquals(400.0, container.width)
        assertEquals(260.0, container.height)

        val issues = parsed.issues
        assertTrue(issues.any { it.code == "relationshipEndpointIsRelationship" && it.level == "warning" })
        assertTrue(issues.none { it.level == "error" })

        assertEquals(3, parsed.organizations.size)
        val business = parsed.organizations[0]
        assertEquals("Business", business.label)
        assertEquals(3, business.children?.size)
        assertEquals("element", business.children?.first()?.refKind)
        assertEquals("el-a", business.children?.first()?.refId)

        val relations = parsed.organizations[1]
        assertEquals("Relations", relations.label)
        assertEquals("relationship", relations.children?.first()?.refKind)

        val viewsOrg = parsed.organizations[2]
        assertEquals("Views", viewsOrg.label)
        assertEquals("view", viewsOrg.children?.single()?.refKind)
        assertEquals("view-1", viewsOrg.children?.single()?.refId)
    }

    @Test
    fun `rejects missing model root`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.parse("<root />".toByteArray())
        }
        assertTrue(ex.message!!.contains("missing <model>"))
    }

    @Test
    fun `rejects malformed xml`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.parse("<model><elements></model>".toByteArray())
        }
    }

    @Test
    fun `parses nested view nodes in document order`() {
        val xml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <model xmlns="http://www.opengroup.org/xsd/archimate/3.0/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" identifier="m1">
              <name>Nested</name>
              <elements>
                <element identifier="el-a" xsi:type="BusinessActor"><name>A</name></element>
              </elements>
              <relationships />
              <views>
                <diagrams>
                  <view identifier="view-1" xsi:type="Diagram">
                    <name>V</name>
                    <node identifier="node-box" xsi:type="Container" x="0" y="0" w="200" h="100">
                      <label>Box</label>
                      <node identifier="node-a" elementRef="el-a" xsi:type="Element" x="10" y="10" w="80" h="40" />
                    </node>
                  </view>
                </diagrams>
              </views>
            </model>
            """.trimIndent().toByteArray()

        val parsed = service.parseAndValidate(xml)
        assertEquals(listOf("node-box", "node-a"), parsed.views.single().nodes.map { it.id })
        assertEquals("Box", parsed.views.single().nodes.first().labelText)
    }

    @Test
    fun `parses relationship name`() {
        val xml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <model xmlns="http://www.opengroup.org/xsd/archimate/3.0/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" identifier="m1">
              <name>Named</name>
              <elements>
                <element identifier="el-a" xsi:type="BusinessProcess"><name>A</name></element>
                <element identifier="el-b" xsi:type="BusinessProcess"><name>B</name></element>
              </elements>
              <relationships>
                <relationship identifier="rel-1" source="el-a" target="el-b" xsi:type="Flow">
                  <name>Payload flow</name>
                </relationship>
              </relationships>
              <views><diagrams/></views>
            </model>
            """.trimIndent().toByteArray()

        val parsed = service.parseAndValidate(xml)
        assertEquals(1, parsed.relationships.size)
        assertEquals("Payload flow", parsed.relationships.single().name)
        assertEquals("Flow", parsed.relationships.single().type)
    }

    @Test
    fun `flags missing relationship endpoints as errors`() {
        val xml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <model xmlns="http://www.opengroup.org/xsd/archimate/3.0/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" identifier="m1">
              <name>Bad</name>
              <elements>
                <element identifier="el-a" xsi:type="BusinessActor"><name>A</name></element>
              </elements>
              <relationships>
                <relationship identifier="rel-1" source="el-a" target="missing" xsi:type="Association" />
              </relationships>
              <views><diagrams/></views>
            </model>
            """.trimIndent().toByteArray()

        val issues = service.parseAndValidate(xml).issues
        assertTrue(issues.any { it.code == "relationshipMissingTarget" && it.level == "error" })
    }

    @Test
    fun `resolves element and relationship properties by definition name`() {
        val parsed = service.parse(readFixture("oef/element-properties.xml"))
        val element = parsed.elements.single()
        assertEquals(
            mapOf("Owner" to "Team A", "Count" to "7", "OrphanProp" to "x"),
            element.properties,
        )
        val relationship = parsed.relationships.single()
        assertEquals(mapOf("Owner" to "Link Owner"), relationship.properties)
    }

    private fun readFixture(path: String): ByteArray =
        checkNotNull(javaClass.classLoader.getResourceAsStream(path)) {
            "Missing test fixture: $path"
        }.use { it.readBytes() }
}
